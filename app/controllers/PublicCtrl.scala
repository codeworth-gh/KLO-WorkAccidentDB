package controllers

import com.github.miachm.sods.{Sheet, SpreadSheet}
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, WorkAccidentDAO}
import models.{InjuredWorker, InjuredWorkerRow, Severity, WorkAccidentSummary}
import play.api.{Configuration, Logger}
import play.api.cache.Cached
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import views.{Helpers, PaginationInfo}

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class Column[T](val name:String, extractor:T=>Any) {
  def apply(t:T)=extractor(t)
}
object Column {
  def apply[T](name:String, extractor:T=>Any) = new Column(name, extractor)
}

object PublicCtrl {
  val PAGE_SIZE = 50
}



class PublicCtrl @Inject()(cc: ControllerComponents, accidents:WorkAccidentDAO, regions:RegionsDAO,
                           cached:Cached, conf:Configuration)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport {
  
  import PublicCtrl._
  private val logger = Logger(classOf[PublicCtrl])
  
  val accidentsDatasetCols:Seq[Column[WorkAccidentSummary]] = Seq(
    Column("id", _.id),
    Column("date", _.date),
    Column("time", _.time.map( t=>t.format(Helpers.dateFormats(Helpers.DateFmt.HR_Time)) ).getOrElse("")),
    Column("region_id", w=>w.regionId.getOrElse("")),
    Column("region_name", w=>w.regionId.flatMap( r => regions(r).map(_.name)).getOrElse("")),
    Column("location", _.location ),
    Column("entrepreneur_id", w=>w.entrepreneurId.getOrElse("")),
    Column("entrepreneur_name", w=>w.entrepreneurName.getOrElse("")),
    Column("details", _.details),
    Column("investigation", _.investigation),
    Column("injured_count", _.injuredCount),
    Column("killed_count",  _.killedCount)
  )
  
  val injuredDatasetCols:Seq[Column[InjuredWorkerRow]] = Seq(
    Column("id", _.worker.id),
    Column("accident_id", _.accidentId),
    Column("date", _.accidentDate),
    Column("name", w=> if (w.worker.injurySeverity.contains(Severity.fatal)) w.worker.name else ""),
    Column("age", w=>w.worker.age.getOrElse("") ),
    Column("citizenship_id", w=>w.worker.citizenship.map(_.id).getOrElse("") ),
    Column("citizenship_name", w=>w.worker.citizenship.map(_.name).getOrElse("") ),
    Column("industry_id", w=>w.worker.industry.map(_.id).getOrElse("") ),
    Column("industry_name", w=>w.worker.industry.map(_.name).getOrElse("") ),
    Column("employer_id", w=>w.worker.employer.map(_.id).getOrElse("") ),
    Column("employer_name", w=>w.worker.employer.map(_.name).getOrElse("") ),
    Column("injury_cause_id", w=>w.worker.injuryCause.map(_.id).getOrElse("") ),
    Column("injury_cause_name", w=>w.worker.injuryCause.map(_.name).getOrElse("") ),
    Column("injury_severity_code", w=>w.worker.injurySeverity.map(_.id).getOrElse("") ),
    Column("injury_severity_name", w=>w.worker.injurySeverity.map(_.toString).getOrElse("") ),
    Column("injury_description", w=>w.worker.injuryDescription ),
    Column("remarks", w=>w.worker.publicRemarks )
  )
  
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
  
  def accidentIndex(pSortBy:Option[String]=None, pAsc:Option[String]=None, pPage:Option[Int]=None) = Action.async{ implicit req =>
    val page = pPage.getOrElse(1)
    val asc = pAsc.getOrElse("f").trim=="t"
    val sortBy = WorkAccidentDAO.SortKey.named(pSortBy.getOrElse("Datetime") ).getOrElse(WorkAccidentDAO.SortKey.Datetime)
    for {
      accRows <- accidents.listAccidents((page-1)*PAGE_SIZE, PAGE_SIZE, sortBy, asc)
      accCount <- accidents.accidentCount()
    } yield {
      Ok(views.html.publicside.accidentsList(accRows,
        regions.apply,
        PaginationInfo(page, Math.ceil(accCount/PAGE_SIZE.toDouble).toInt), sortBy, asc))
    }
  }
  
  def accidentDetails( id:Long ) = Action.async{ implicit req =>
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
    val sheet = new Sheet("accidents")
    // add title row
    sheet.appendColumns(accidentsDatasetCols.size)
    sheet.appendRow()
    val titleRow = sheet.getRange(0,0,1,accidentsDatasetCols.size)
    accidentsDatasetCols.zipWithIndex.foreach( c => {
      titleRow.getCell(0,c._2).setValue(c._1.name)
    } )
    // add rows
    for {
      workAccidents <- accidents.listAllAccidents()
    } yield {
      for ( acc <- workAccidents ) {
        sheet.appendRow()
        val row = sheet.getRange(sheet.getLastRow,0, 1, accidentsDatasetCols.size)
        accidentsDatasetCols.zipWithIndex.foreach( c => row.getCell(0,c._2).setValue(c._1(acc)) )
      }
      sheetToOkFile(sheet, "work-accidents.ods")
    }
  }
  
  def injuriesDataset = Action.async{ req =>
    val sheet = new Sheet("injuries")
    // add title row
    sheet.appendColumns(injuredDatasetCols.size)
    sheet.appendRow()
    val titleRow = sheet.getRange(0,0,1,injuredDatasetCols.size)
    injuredDatasetCols.zipWithIndex.foreach( c => {
      titleRow.getCell(0,c._2).setValue(c._1.name)
    } )
    // add rows
    for {
      injured <- accidents.listAllInjuredWorkers
    } yield {
      for ( acc <- injured ) {
        sheet.appendRow()
        val row = sheet.getRange(sheet.getLastRow,0, 1, injuredDatasetCols.size)
        injuredDatasetCols.zipWithIndex.foreach( c => row.getCell(0,c._2).setValue(c._1(acc)) )
      }
      sheetToOkFile(sheet, "injured-workers.ods")
    }
  }
  
  private def sheetToOkFile( sheet:Sheet, filename:String ) = {
    val sprd = new SpreadSheet()
    sprd.addSheet(sheet, 0)
    var bytes:Array[Byte]=null
    Using( new ByteArrayOutputStream() ){ bas =>
      sprd.save(bas)
      bas.flush()
      bytes = bas.toByteArray
    }
    Ok(bytes).as("application/vnd.oasis.opendocument.spreadsheet")
      .withHeaders("Content-Disposition"->s"attachment; filename=${"\""}${filename}${"\""}")
  }
}
