package actors

import org.apache.pekko.actor.{Actor, Props}
import com.github.jferard.fastods.{OdsDocument, OdsFactory}
import controllers.PublicCtrl.{integerDataStyle, rowStyle, titleStyle}
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey, WorkAccidentDAO}
import models.LongRunningProcessStatus.{Done, Started}
import models.{Column, LongRunningProcessMonitor, RichWalker, SafetyWarrant, Severity}
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.libs.Files.TemporaryFileCreator
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.util.Using

object DataProductsActor {
  def props:Props = Props[DataProductsActor]()
  case class PossiblyUpdateWarrantTable()
  case class ForceUpdateWarrantTable()
  case class UpdateTemporalViews()
  case class CreatePeriodicalReport( from:LocalDate, to:LocalDate, monitor:LongRunningProcessMonitor)
  
  import Column._
  val safetyWarrantCols = Seq(
    Column[SafetyWarrant]("id", (v,w)=>printLong(v.id, w)),
    Column[SafetyWarrant]("sent date", (v,w)=>printDate(v.sentDate, w)),
    Column[SafetyWarrant]("operator id", (v,w)=>w.setStringValue(v.operatorTextId)),
    Column[SafetyWarrant]("operator name", (v,w)=>w.setStringValue(v.operatorName)),
    Column[SafetyWarrant]("city name", (v,w)=>w.setStringValue(v.cityName)),
    Column[SafetyWarrant]("executor name", (v,w)=>w.setStringValue(v.executorName)),
    Column[SafetyWarrant]("category name", (v,w)=>w.setStringValue(v.categoryName)),
    Column[SafetyWarrant]("felony", (v,w)=>w.setStringValue(v.felony)),
    Column[SafetyWarrant]("law", (v,w)=>w.setStringValue(v.law)),
    Column[SafetyWarrant]("clause", (v,w)=>w.setStringValue(v.clause)),
    Column[SafetyWarrant]("scrape date", (v,w)=>printDate(v.scrapeDate, w))
  )
  
}


/**
 * Actor for making data products in the background.
 */
class DataProductsActor @Inject() (safetyWarrants:SafetyWarrantDAO,
                                   workAccidents: WorkAccidentDAO,
                                   settings:SettingDAO,
                                   cache:AsyncCacheApi,
                                   messagesApi: MessagesApi,
                                   config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  import DataProductsActor._
  private val D = Duration(5, duration.MINUTES)
  private val log = Logger(classOf[WarrantScrapingActor])
  private val messages = messagesApi.preferred(Seq(Lang("IW"), Lang("EN")))
  
  
  override def receive: Receive = {
    case PossiblyUpdateWarrantTable() => {
      if ( settings.isTrueish(SettingKey.SafetyWarrantProductsNeedUpdate) ) {
        settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "no")
        log.info("Updating safety warrant ODS")
        updateSafetyWarrantDownloadable()
        log.info("Refreshing materialized views")
        safetyWarrants.refreshViews()
        log.info("Done")
      }
    }
    
    case UpdateTemporalViews() =>
      log.info("Refreshing temporal views")
      safetyWarrants.refreshTemporalViews()
    
    case ForceUpdateWarrantTable() =>
      updateSafetyWarrantDownloadable()
      safetyWarrants.refreshViews()
      sender() ! "OK"
    
    case CreatePeriodicalReport(f,t,m) => composePeriodicalReport(f,t,m)
  }
  
  private def updateSafetyWarrantDownloadable():Unit = {
    
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("DataProductsActor"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    val table = document.addTable("Safety Warrants")
    val walker = table.getWalker
    
    // add title row
    safetyWarrantCols.foreach(c => {
      walker.setStringValue(c.name)
      walker.setStyle(titleStyle)
      walker.next()
    })
    walker.setRowStyle(rowStyle)
    walker.nextRow()
    
    // add rows
    log.info("Writing rows")
    val src = safetyWarrants.listAll()
    val done = src.foreach(sw => {
      safetyWarrantCols.foreach(c => {
        c.write(sw, walker);
        walker.next()
      })
      walker.setRowStyle(rowStyle)
      walker.nextRow()
    })
    Await.result(done, D)
    
    // write a temp file
    log.info("Writing temp file")
    val tempPath = Paths.get(config.get[String]("klo.dataProductFolder")).resolve("safetyWarrants.ods.temp")
    
    Using(Files.newOutputStream(tempPath)) {
      writer.save
    }
    
    // Move to
    log.info("Moving temp file to place")
    Files.move(tempPath, tempPath.resolveSibling("safetyWarrants.ods"),
      StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  
  private def composePeriodicalReport(from: LocalDate, to: LocalDate, anLpm: LongRunningProcessMonitor): Unit = {
    var lpm = anLpm.copy(status = Started)
    cache.set(lpm.id, lpm)
    log.info( s"Composing periodical report ${from}-${to}")
    
    Await.result(safetyWarrants.refreshViews(), D)
    
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("ReportsCtrl"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    
    accidentsByMonthTable(from, to, document)
    casualtiesByIndustryTable(from, to, document)
    casualtiesByYearTable(from.getMonthValue, to.getMonthValue, document)
    
    System.getProperty("java.io.tmpdir")
    val tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(s"${lpm.id}.ods")
//    fileCreator.create(tempPath) // ensure later deletion by the reaper
    
    Using(Files.newOutputStream(tempPath)){
      writer.save
    }
    log.info(s"File created at: ${tempPath.toAbsolutePath}")
    lpm = lpm.copy(status = Done)
    cache.set(lpm.id, lpm)
    
    log.info( s"Done composing periodical report ${from}-${to}")
  }
  
  private def casualtiesByYearTable( startMonth:Int, endMonth:Int, document:OdsDocument ): Unit = {
    val tbl = document.addTable("Casualties by Year")
    val walker = RichWalker(tbl.getWalker)
    
    val title = messages("reports.ods.casualtiesByPeriod.title", messages("month."+startMonth), messages("month."+endMonth))
    walker.th(title).nextRow()
    walker.nextRow();
    tbl.setCellMerge(0,0,1,5)
    
    walker.th(messages("year"))
    walker.th(messages("reports.ods.sev.medium"))
    walker.th(messages("reports.ods.sev.severe"))
    walker.th(messages("reports.ods.sev.nearFatal"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    Await.result(workAccidents.getCasualtiesCountByYear(startMonth, endMonth).map(rows => {
      log.info(s"getCasualtiesCountByYear Got ${rows.length} rows")
      val byYear = rows.groupBy(_._1)
      
      byYear.toSeq.sortBy(_._1).foreach( yearData => {
        walker.bold.td(yearData._1).plain
        Range.inclusive(Severity.medium.id, Severity.fatal.id).foreach( severity => {
          yearData._2.find(_._2 == severity) match {
            case None => walker.td(0)
            case Some(_, _, count) => walker.td(count)
          }
        })
        walker.nextRow()
      })
    }), D)
    
  }
  
  private def casualtiesByIndustryTable(from: LocalDate, to: LocalDate, document:OdsDocument):Unit = {
    val tbl = document.addTable("Casualties by Industry")
    val walker = RichWalker(tbl.getWalker)
    
    walker.th(messages("reports.ods.casualtiesByIndustry.title")).nextRow().nextRow()
    tbl.setCellMerge(0,0,1,3)
    
    walker.th(messages("industry"))
    walker.th(messages("reports.ods.sev.mediumAndUp"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    Await.result(workAccidents.getCasualtiesByIndustry(from, to).map(rows => {
      log.info(s"casualtiesByIndustryTable Got ${rows.length} rows")
      val byInd = rows.groupBy(_._1)
      byInd.foreach( itms => {
        walker.bold.td(Option(itms._1).getOrElse("אחר/לא צויין")).plain
        itms._2.find(_._2 == false) match {
          case None => walker.td(0)
          case Some(_,_,count) => walker.td(count)
        }
        itms._2.find(_._2 == true) match {
          case None => walker.td(0)
          case Some(_,_,count) => walker.td(count)
        }
        walker.nextRow()
      })
    }), D)
  }
  
  private def accidentsByMonthTable(from: LocalDate, to: LocalDate, document:OdsDocument):Unit = {
    val tbl = document.addTable("Accidents by month")
    
    val walker = RichWalker(tbl.getWalker)
    walker.th(messages("reports.ods.accidentsByMonthTable.title")).nextRow().nextRow()
    tbl.setCellMerge(0,0,1,4)
    
    walker.th(messages("year"))
    walker.th(messages("month"))
    walker.th(messages("reports.ods.sev.mediumAndUp"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    val injuredSum = new AtomicInteger()
    val killedSum = new AtomicInteger()
    Await.result( workAccidents.getCasualtiesByMonth(from, to).map( rows => {
      log.info( s"accidentsByMonthTable Got ${rows.length} rows")
      rows.foreach( row => {
        walker.td(row._2)
        walker.td(row._3)
        walker.td(row._4)
        walker.td(row._5)
        walker.nextRow()
        injuredSum.addAndGet(row._4)
        killedSum.addAndGet(row._5)
      })
    }), D)
    walker.skip().skip().bold
    walker.td(injuredSum.get())
    walker.td(killedSum.get())
  }
}
