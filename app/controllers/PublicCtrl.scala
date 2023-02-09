package controllers

import actors.WarrantScrapingActor
import be.objectify.deadbolt.scala.DeadboltActions
import com.github.jferard.fastods.{AnonymousOdsFileWriter, ObjectToCellValueConverter, OdsFactory, Table, TableCellWalker}
import com.github.jferard.fastods.style.TableCellStyle
import com.github.jferard.fastods.attribute.SimpleLength
import com.github.jferard.fastods.datastyle.{DataStyle, FloatStyleBuilder}
import com.github.jferard.fastods.style.TableRowStyle
import dataaccess.BusinessEntityDAO.StatsSortKey
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, RelationToAccidentDAO, SafetyWarrantDAO, SanctionsDAO, SettingDAO, SettingKey, TableRefs, WorkAccidentDAO}
import models.{Column, InjuredWorker, InjuredWorkerRow, Severity, WorkAccidentSummary}
import play.api.{Configuration, Logger}
import play.api.cache.Cached
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{AbstractController, ControllerComponents}
import views.{Helpers, PaginationInfo}

import java.util.Locale
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.{Date, Locale}
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Try, Using}




object PublicCtrl {
  val PAGE_SIZE = 50
  val INDEX_PAGE_CACHE_KEY = "PublicCtrl::publicMain"
  val SW_INDEX_PAGE_CACHE_KEY = "PublicCtrl::SWIndex"
  val integerDataStyle = new FloatStyleBuilder("int", Locale.US).decimalPlaces(0).groupThousands(false).build()
  val rowStyle = TableRowStyle.builder("okRow").rowHeight(SimpleLength.pt(16.0)).build()
  val titleStyle = TableCellStyle.builder("title").fontWeightBold().build()
}



class PublicCtrl @Inject()(cc: ControllerComponents, accidents:WorkAccidentDAO, regions:RegionsDAO,
                           relations:RelationToAccidentDAO, industries:IndustriesDAO, deadbolt:DeadboltActions,
                           businessEntities:BusinessEntityDAO, causes:InjuryCausesDAO, citizenships: CitizenshipsDAO,
                           safetyWarrants:SafetyWarrantDAO, sanctionsDAO: SanctionsDAO,
                           settings:SettingDAO, cached:Cached, conf:Configuration)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport {
  
  import PublicCtrl._
  import Column._
  
  private val logger = Logger(classOf[PublicCtrl])
  private val knownBizEntShort = messagesApi("businessEntities.knownContractorShort")(Lang("en"))
  
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
    Column("related_entities", (v,w)=>{
      val knownBizEts = v.relateds.map( k => k._2.id->(if(k._2.knownContractor){knownBizEntShort}else{""}) ).toMap
      w.setStringValue(
        v.relateds.map( r=>s"${r._2.id} ${r._2.name} ${knownBizEts(r._2.id)} (${relations(r._1.id).map(_.name).getOrElse("")})")
          .mkString(", "))
    }
    ),
    Column("details", (v,w)=>w.setStringValue(v.details)),
    Column("investigation", (v,w)=>w.setStringValue(v.investigation)),
    Column("injured_count", (v,w)=>printInt(v.injuredCount,w)),
    Column("killed_count",  (v,w)=>printInt(v.killedCount,w))
  )
  
  val injuredDatasetCols:Seq[Column[InjuredWorkerRow]] = Seq(
    Column[InjuredWorkerRow]("id", (v,w)=>printInt(v.worker.id, w) ),
    Column[InjuredWorkerRow]("accident_id", (v,w)=>printInt(v.accidentId, w)),
    Column[InjuredWorkerRow]("accident_region", (v,w)=>printStrOption(v.regionId.flatMap( regions(_) ).map(_.name), w)),
    Column[InjuredWorkerRow]("date", (v,w) => printDate(v.accidentDate, w)),
    Column[InjuredWorkerRow]("name", (v,w) => w.setStringValue( if (v.worker.injurySeverity.contains(Severity.fatal)) v.worker.name else "") ),
    Column[InjuredWorkerRow]("age", (v,w)  => printIntOption(v.worker.age, w)),
    Column[InjuredWorkerRow]("citizenship_id",   (v,w) => printIntOption(v.worker.citizenship.map(_.id), w)),
    Column[InjuredWorkerRow]("citizenship_name", (v,w) => printStrOption(v.worker.citizenship.map(_.name), w)),
    Column[InjuredWorkerRow]("industry_id",      (v,w) => printIntOption(v.worker.industry.map(_.id), w)),
    Column[InjuredWorkerRow]("industry_name",    (v,w) => printStrOption(v.worker.industry.map(_.name), w)),
    Column[InjuredWorkerRow]("employer_id",      (v,w) => printOption(v.worker.employer.map(_.id), w)),
    Column[InjuredWorkerRow]("employer_name",    (v,w) => printStrOption(v.worker.employer.map(_.name), w)),
    Column[InjuredWorkerRow]("injury_cause_id",  (v,w) => printOption(v.worker.injuryCause.map(_.id), w)),
    Column[InjuredWorkerRow]("injury_cause_name",    (v,w) => printStrOption(v.worker.injuryCause.map(_.name), w) ),
    Column[InjuredWorkerRow]("injury_severity_code", (v,w) => printOption(v.worker.injurySeverity.map(_.id), w) ),
    Column[InjuredWorkerRow]("injury_severity_name", (v,w) => printStrOption(v.worker.injurySeverity.map(_.toString), w) ),
    Column[InjuredWorkerRow]("injury_description",   (v,w) => w.setStringValue( v.worker.injuryDescription) ),
    Column[InjuredWorkerRow]("remarks", (v,w) => w.setStringValue(v.worker.publicRemarks) )
  )
  

  
  def main = cached(_=>PublicCtrl.INDEX_PAGE_CACHE_KEY, 120){
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
                    pCitizenships:Option[String], pCauses:Option[String],
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
    
    val selSeverities = pSeverities.map( _.split(",").map(_.trim).filter(_.nonEmpty).filter(_ != "-1").map(Severity.withName).toSet ).getOrElse(Set())
    val includeNullSevs = pSeverities.exists(_.contains("-1"))
    
    val rgnIds = mkIdSet( pRegions, regions(_).map(_.id))
    val indIds = mkIdSet(pIndustries, industries(_).map(_.id) )
    val selCitizenshipIds = mkIdSet(pCitizenships, citizenships(_).map(_.id))
    val selCausesIds = mkIdSet( pCauses, causes(_).map(_.id) )
    
    for {
      regionList <- regions.list()
      industryList <- industries.list()
      causeList <- causes.list()
      citizenshipsList <- citizenships.list()
      accCount <- accidents.accidentCount(start, end, rgnIds, indIds, selCitizenshipIds, selCausesIds, selSeverities, includeNullSevs)
      accRows  <- accidents.listAccidents(start, end, rgnIds, indIds, selCitizenshipIds, selCausesIds, selSeverities, includeNullSevs, (page-1)*PAGE_SIZE, PAGE_SIZE, sortBy, asc)
    } yield {
      Ok(views.html.publicside.accidentsList(accRows,
        regionList, industryList, causeList, citizenshipsList,
        regions.apply, rgnIds, indIds,
        selSeverities, includeNullSevs,
        selCitizenshipIds, selCausesIds,
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
  
  def bizEntIndex(pPage:Option[Int]=None, pSortBy:Option[String]=None, pAsc:Option[String]=None, searchStr:String="", isKnownContractor:String="") = Action.async { implicit req =>
    val sortBy = pSortBy.flatMap(StatsSortKey.named).getOrElse(BusinessEntityDAO.StatsSortKey.Accidents)
    val page = pPage.getOrElse(1)
    val asc = pAsc.getOrElse("f").trim=="t"
    val effKnownContractor = (isKnownContractor.trim.toLowerCase == "t" )
    for {
      rows <- businessEntities.listStats(searchStr, effKnownContractor, (page-1)*PAGE_SIZE, PAGE_SIZE, sortBy, asc)
      statCount <- businessEntities.countStats(searchStr, effKnownContractor)
    } yield {
      val pi = PaginationInfo(page, Math.ceil(statCount/PAGE_SIZE.toDouble).toInt)
      Ok( views.html.publicside.bizEntList(rows, statCount, pi, sortBy, asc, effKnownContractor, searchStr) )
    }
  }
  
  def bizEntDetails(id:Long ) = Action.async{ implicit req =>
    for {
      bizEntOpt <- businessEntities.get(id)
      accidents <- accidents.accidentsForBizEnt(id)
      sanctions <- sanctionsDAO.sanctionsForEntity(id)
    } yield {
      bizEntOpt match {
        case None => {
          val msgs = request2Messages(req)
          NotFound(views.html.errorPage(404, msgs("error.businessNotFound"), Some(msgs("error.businessNotFound.explanation")), None, req, msgs) )
        }
        case Some(bizEnt) => Ok( views.html.publicside.bizEntDetails(bizEnt, accidents, sanctions))
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
  
  def safetyWarrantsIndex() = cached( _ => PublicCtrl.SW_INDEX_PAGE_CACHE_KEY, 120) {
    Action.async{ implicit req =>
      for {
        top5 <- safetyWarrants.worst20ExecutorsAllTime(10) // we over-fetch to group later
        top5Last24 <- safetyWarrants.executorsOver4In24(0,10)
        byCatAll <- safetyWarrants.warrantCountByCategoryAll()
        byCat24Mo <- safetyWarrants.warrantCountByCategory24Mo()
        byLaw <- safetyWarrants.warrantCountByLaw(5)
        byCatYear <- safetyWarrants.warrantCountByCategoryAndYear()
        total <- safetyWarrants.count()
        totalUnknown <- safetyWarrants.unknownExecutorCount()
        dateRange <- safetyWarrants.warrantDates()
      } yield {
        val top5AllTime = top5.filter(_.name.trim.nonEmpty).groupBy(_.count).map( kv => kv._1->kv._2.map(_.name).sorted ).toSeq.sortBy(-_._1).take(5)
        val top5in24Mo = top5Last24.filter(_._1.trim.nonEmpty).groupBy(_._2).map( kv => kv._1->kv._2.map(_._1).sorted ).toSeq.sortBy(-_._1).take(5)
        val years = byCatYear.map(_.year).distinct.sorted
        val countPerYearByCat = byCatYear.map( r => (r.category, r.year)->r.count ).toMap
        Ok( views.html.publicside.safetywarrants.index(total, totalUnknown, top5AllTime,
          top5in24Mo, byCatAll, byCat24Mo, byLaw, years, countPerYearByCat, dateRange) )
      }
    }
  }
  
  def top20Executors() = Action.async{ implicit req =>
    for {
      total <- safetyWarrants.count()
      worst20 <- safetyWarrants.worst20ExecutorsAllTime()
    } yield {
      val groupedExecutors = worst20.groupBy(_.count).map( kv => kv._1->kv._2.map(_.name).sorted )
      val totalIn20 = worst20.map( _.count ).sum
      val pct = totalIn20.toDouble / total.toDouble
      Ok( views.html.publicside.safetywarrants.top20(total, totalIn20, pct, groupedExecutors) )
    }
  }
  
  def over4Last24(page:Option[Int]) = Action.async{ implicit req =>
    for {
      count <- safetyWarrants.executorsOver4In24Count()
      items <- safetyWarrants.executorsOver4In24( (page.getOrElse(1)-1)*PAGE_SIZE, PAGE_SIZE )
    } yield {
      val pi = PaginationInfo(page.getOrElse(1), Math.ceil(count/PAGE_SIZE.toDouble).toInt)
      Ok( views.html.publicside.safetywarrants.last24mo(items, count, pi))
    }
  }
  
  def safetyWarrantsList(searchStr:Option[String], pStartDate:Option[String], pEndDate:Option[String], executorName:Option[String], pPage:Option[Int]) = Action.async{ implicit req =>
    val fmt = Helpers.dateFormats(Helpers.DateFmt.ISO_Date)
    var startDate:Option[LocalDate] = pStartDate.flatMap( s => Try(LocalDate.parse(s, fmt)).toOption )
    var endDate:Option[LocalDate] = pEndDate.flatMap( s => Try(LocalDate.parse(s, fmt)).toOption )
  
    (startDate, endDate) match {
      case (Some(s),Some(e)) => if ( s.compareTo(e) > 0 ) {
        startDate = Some(e)
        endDate = Some(s)
      }
      case _ => ()
    }
//    logger.info(s"Start: $startDate end: $endDate")
    
    for {
      warrants <- safetyWarrants.listWarrants((pPage.getOrElse(1)-1)*PAGE_SIZE, PAGE_SIZE, searchStr, startDate, endDate, executorName )
      count <- safetyWarrants.countWarrants(searchStr, startDate, endDate, executorName)
    } yield {
      val pi = PaginationInfo(pPage.getOrElse(1), Math.ceil(count/PAGE_SIZE.toDouble).toInt)
      Ok( views.html.publicside.safetywarrants.warrantList(warrants, pi, count, searchStr, pStartDate, pEndDate, executorName))
    }
  }
  
  def safetyWarrantsForExec(execName:String) = Action.async{ implicit req =>
    for {
      sws <- safetyWarrants.getForExecutor(execName)
      yearlyCounts <- safetyWarrants.getExecutorYearlyCounts(execName)
    } yield {
      Ok( views.html.publicside.safetywarrants.execDetails(execName, sws, yearlyCounts) )
    }
  }
  
  def showSafetyWarrant(id: Long) = Action.async{ implicit req =>
    for {
      w <- safetyWarrants.get(id)
    } yield w match {
      case None => NotFound( views.html.errorPage(404, "Warrant not found", None, None, req, request2Messages(req)) )
      case Some(w) => Ok(views.html.publicside.safetywarrants.warrantDetails(w) )
    }
  }
  
  def datasets = Action.async{ implicit req =>
    for {
      lastUpdate <- accidents.getLastUpdateDate
    } yield {
      val lastScrape = settings.get(SettingKey.LastSafetyWarrantScrapeTime).map( v => LocalDateTime.parse(v, WarrantScrapingActor.ldtFmt) )
      Ok(views.html.publicside.datasets(lastUpdate, lastScrape ))
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
  
  def safetyWarrantsDataset = Action{ req =>
    Ok.sendFile( Paths.get(conf.get[String]("klo.dataProductFolder")).resolve("safetyWarrants.ods").toFile)
      .as("application/vnd.oasis.opendocument.spreadsheet")
      .withHeaders("Content-Disposition"->s"attachment; filename=safety-warrants.ods")
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
  
  private def mkIdSet( idStrOpt:Option[String], validator:(Int=>Option[Int])):Set[Int] = idStrOpt.map(
    _.split(",").map(_.trim).filter(_.nonEmpty).map(_.toInt).flatMap(i => if (i == -1) Some(-1) else validator(i) ).toSet
  ).getOrElse(Set())
}

