package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import com.github.jferard.fastods.{AnonymousOdsFileWriter, ObjectToCellValueConverter, OdsFactory, Table, TableCellWalker}
import com.github.jferard.fastods.style.TableCellStyle
import com.github.jferard.fastods.attribute.SimpleLength
import com.github.jferard.fastods.datastyle.{DataStyle, FloatStyleBuilder}
import com.github.jferard.fastods.style.TableRowStyle
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, RelationToAccidentDAO, WorkAccidentDAO}
import models.{InjuredWorker, InjuredWorkerRow, Severity, WorkAccidentSummary}
import play.api.{Configuration, Logger}
import play.api.cache.Cached
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import views.{Helpers, PaginationInfo}

import java.util.Locale
import java.io.ByteArrayOutputStream
import java.time.{LocalDate, ZoneOffset}
import java.util.{Date, Locale}
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class Column[T](val name:String, writer:(T, TableCellWalker)=>Any) {
  def write(t:T, w:TableCellWalker)=writer(t,w)
}
object Column {
  def apply[T](name:String, extractor:(T, TableCellWalker)=>Any) = new Column(name, extractor)
}

object PublicCtrl {
  val PAGE_SIZE = 50
  val integerDataStyle = new FloatStyleBuilder("int", Locale.US).decimalPlaces(0).groupThousands(false).build()
  val rowStyle = TableRowStyle.builder("okRow").rowHeight(SimpleLength.pt(16.0)).build()
}



class PublicCtrl @Inject()(cc: ControllerComponents, accidents:WorkAccidentDAO, regions:RegionsDAO,
                           relations:RelationToAccidentDAO, industries:IndustriesDAO, deadbolt:DeadboltActions,
                           cached:Cached, conf:Configuration)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport {
  
  import PublicCtrl._
  private val logger = Logger(classOf[PublicCtrl])
  
  val accidentsDatasetCols:Seq[Column[WorkAccidentSummary]] = Seq(
    Column("id", (v:WorkAccidentSummary,w:TableCellWalker) => printInt(v.id, w)),
    Column("date", (v,w) => printDate(v.date,w) ),
    Column("time", (v:WorkAccidentSummary,w:TableCellWalker) => v.time match {
      case None => w.setStringValue("")
      case Some(time) => w.setStringValue(time.format(views.Helpers.dateFormats(views.Helpers.DateFmt.HR_Time)))
    }),
    Column("region_id", (v,w)=> printIntOption( v.regionId, w)),
    Column("region_name", (v,w)=> w.setStringValue(v.regionId.flatMap( r => regions(r).map(_.name)).getOrElse(""))),
    Column("location", (v,w)=>w.setStringValue(v.location) ),
    Column("related_entities", (v,w)=>w.setStringValue(
      v.relateds.map( r=>s"${r._2.id} ${r._2.name} (${relations(r._1.id).map(_.name).getOrElse("")})")
        .mkString(", "))
    ),
    Column("details", (v,w)=>w.setStringValue(v.details)),
    Column("investigation", (v,w)=>w.setStringValue(v.investigation)),
    Column("injured_count", (v,w)=>printInt(v.injuredCount,w)),
    Column("killed_count",  (v,w)=>printInt(v.killedCount,w))
  )
  
  val injuredDatasetCols:Seq[Column[InjuredWorkerRow]] = Seq(
    Column("id", (v,w)=>printInt(v.worker.id, w) ),
    Column("accident_id", (v,w)=>printInt(v.accidentId, w)),
    Column("date", (v,w) => printDate(v.accidentDate, w)),
    Column("name", (v,w) => w.setStringValue( if (v.worker.injurySeverity.contains(Severity.fatal)) v.worker.name else "") ),
    Column("age", (v,w)  => printIntOption(v.worker.age, w)),
    Column("citizenship_id",   (v,w) => printIntOption(v.worker.citizenship.map(_.id), w)),
    Column("citizenship_name", (v,w) => printStrOption(v.worker.citizenship.map(_.name), w)),
    Column("industry_id",      (v,w) => printIntOption(v.worker.industry.map(_.id), w)),
    Column("industry_name",    (v,w) => printStrOption(v.worker.industry.map(_.name), w)),
    Column("employer_id",      (v,w) => printOption(v.worker.employer.map(_.id), w)),
    Column("employer_name",    (v,w) => printStrOption(v.worker.employer.map(_.name), w)),
    Column("injury_cause_id",  (v,w) => printOption(v.worker.injuryCause.map(_.id), w)),
    Column("injury_cause_name",    (v,w) => printStrOption(v.worker.injuryCause.map(_.name), w) ),
    Column("injury_severity_code", (v,w) => printOption(v.worker.injurySeverity.map(_.id), w) ),
    Column("injury_severity_name", (v,w) => printStrOption(v.worker.injurySeverity.map(_.toString), w) ),
    Column("injury_description",   (v,w) => w.setStringValue( v.worker.injuryDescription) ),
    Column("remarks", (v,w) => w.setStringValue(v.worker.publicRemarks) )
  )
  
  def printInt( i:Long, w:TableCellWalker ):Unit = {
    w.setFloatValue(i.toFloat)
    w.setDataStyle(integerDataStyle)
  }
  
  def printStrOption(os:Option[String], w: TableCellWalker ):Unit = {
    os match {
      case None => w.setStringValue("")
      case Some(s) => w.setStringValue(s)
    }
  }
  
  def printIntOption( os:Option[Int], w: TableCellWalker ):Unit = printOption(os.map(_.toLong), w)
  def printOption( os:Option[Long], w: TableCellWalker ):Unit = {
    os match {
      case None => w.setStringValue("")
      case Some(s) => printInt(s,w)
    }
  }
  
  def printDate( d:LocalDate, w:TableCellWalker ):Unit = {
    val millies = d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli
    val jd = new Date(millies)
    w.setDateValue(jd)
  }
  
  def main = cached("publicMain"){
    Action.async{implicit req =>
      logger.warn("public-main actually rendered.")
      for {
        recentInjuries <- accidents.listRecentInjuredWorkers(conf.get[Int]("klo.main.recentCount"))
        injuryCounts   <- accidents.injuryCountsByIndustryAndSeverity(LocalDate.now.getYear)
        prevYears      <- accidents.injuryCountsBySeverityAndYear
      } yield {
        Ok( views.html.publicside.main(recentInjuries, injuryCounts, prevYears) )
      }
    }
  }
  
  def accidentIndex(pRegions:Option[String], pIndustries:Option[String], pSeverities:Option[String],
                    pFrom:Option[String], pTo:Option[String], pSortBy:Option[String]=None,
                    pAsc:Option[String]=None, pPage:Option[Int]=None) = Action.async{ implicit req =>
    val dateFormat = Helpers.dateFormats(Helpers.DateFmt.ISO_Date)
    val page = pPage.getOrElse(1)
    val asc = pAsc.getOrElse("f").trim=="t"
    val sortBy = WorkAccidentDAO.SortKey.named(pSortBy.getOrElse("Datetime") ).getOrElse(WorkAccidentDAO.SortKey.Datetime)
    val ppFrom = pFrom.map(_.trim).filter(_.nonEmpty).map( s => LocalDate.parse(s, dateFormat) )
    val ppTo = pTo.map(_.trim).filter(_.nonEmpty).map( s => LocalDate.parse(s, dateFormat))
    val (start, end) = (ppFrom, ppTo) match {
        case (Some(f), Some(t)) => if (f.isAfter(t) ) (ppTo, ppFrom) else (ppFrom, ppTo)
        case _ => (ppFrom, ppTo)
      }
    val selRgns = pRegions.map( _.split(",").map(_.trim).filter(_.nonEmpty).map(_.toInt).map(regions(_)).filter(_.isDefined).flatten.toSet ).getOrElse(Set())
    val selIndustries = pIndustries.map( _.split(",").map(_.trim).filter(_.nonEmpty).map(_.toInt).map(industries(_)).filter(_.isDefined).flatten.toSet ).getOrElse(Set())
    val selSeverities = pSeverities.map( _.split(",").map(_.trim).filter(_.nonEmpty).map(Severity.withName).toSet ).getOrElse(Set())
    
    val rgnIds = selRgns.map(_.id)
    val indIds = selIndustries.map(_.id)
    for {
      regionList <- regions.list()
      industryList <- industries.list()
      accCount <- accidents.accidentCount(start, end, rgnIds, indIds, selSeverities)
      accRows  <- accidents.listAccidents(start, end, rgnIds, indIds, selSeverities, (page-1)*PAGE_SIZE, PAGE_SIZE, sortBy, asc)
    } yield {
      Ok(views.html.publicside.accidentsList(accRows,
        regionList, industryList, regions.apply,
        selRgns, selIndustries, selSeverities,
        start.map( dateFormat.format ), end.map( dateFormat.format ),
        accCount, PaginationInfo(page, Math.ceil(accCount/PAGE_SIZE.toDouble).toInt), sortBy, asc))
    }
  }
  
  def accidentDetails( id:Long ) = deadbolt.WithAuthRequest()(){ implicit req =>
    for {
      accident <- accidents.getAccident(id)
    } yield accident match {
      case None => {
        val msgs = request2Messages(req)
        NotFound(views.html.errorPage(404, msgs("error.accidentNotFound"), Some(msgs("error.accidentNotFound.explanation")), None, req, msgs) )
      }
      case Some(acc) => {
        Ok( views.html.publicside.accidentDetails(acc) )
      }
    }
  }
  
  def fatalities(year:Option[Int]) = Action.async{ implicit req =>
    for {
      yearsWithAccidents <- accidents.listYearsWithAccidents
      actualYear = year.getOrElse(LocalDate.now().getYear)
      killed <- accidents.listKilledWorkers(actualYear)
    } yield {
      Ok( views.html.publicside.fatalitiesList(actualYear, yearsWithAccidents, killed) )
    }
    
  }
  
  def datasets = Action.async{ implicit req =>
    for {
      lastUpdate <- accidents.getLastUpdateDate
    } yield {
      Ok(views.html.publicside.datasets(lastUpdate))
    }
  }
  
  // TODO: Cache these.
  def accidentsDataset = Action.async{ req =>
  
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("public-ctrl"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    val table = document.addTable("Accidents")
    val walker = table.getWalker
    
    
    // add title row
    val titleStyle = TableCellStyle.builder("title").fontWeightBold().build()
    accidentsDatasetCols.foreach( c => {
      walker.setStringValue(c.name)
      walker.setStyle(titleStyle)
      walker.next()
    } )
    walker.setRowStyle(rowStyle)
    walker.nextRow()
    
    // add rows
    for {
      workAccidents <- accidents.listAllAccidents()
    } yield {
      for ( acc <- workAccidents ) {
        accidentsDatasetCols.foreach( c => {c.write(acc, walker); walker.next()} )
        walker.setRowStyle(rowStyle)
        walker.nextRow()
      }
      fastOdsToOkFile(writer, "work-accidents.ods")
    }
  }
  
  def injuriesDataset = Action.async{ req =>
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("public-ctrl"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    val table = document.addTable("Accidents")
    val walker = table.getWalker
  
  
    // add title row
    val titleStyle = TableCellStyle.builder("title").fontWeightBold().build()
    injuredDatasetCols.foreach( c => {
      walker.setStringValue(c.name)
      walker.setStyle(titleStyle)
      walker.next()
    } )
    walker.setRowStyle(rowStyle)
    walker.nextRow()
  
    for {
      injured <- accidents.listAllInjuredWorkers
    } yield {
      for ( acc <- injured ) {
        injuredDatasetCols.foreach( c => {c.write(acc, walker); walker.next()} )
        walker.setRowStyle(rowStyle)
        walker.nextRow()
      }
      fastOdsToOkFile(writer, "injured-workers.ods")
    }
  }
  
  private def fastOdsToOkFile(writer:AnonymousOdsFileWriter, filename:String ) = {
    var bytes:Array[Byte]=null
    Using( new ByteArrayOutputStream() ){ bas =>
      writer.save(bas)
      bas.flush()
      bytes = bas.toByteArray
    }
    Ok(bytes).as("application/vnd.oasis.opendocument.spreadsheet")
      .withHeaders("Content-Disposition"->s"attachment; filename=${"\""}${filename}${"\""}")
  }
  
}

