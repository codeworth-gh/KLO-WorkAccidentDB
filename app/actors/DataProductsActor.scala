package actors

import org.apache.pekko.actor.{Actor, Props}
import com.github.jferard.fastods.OdsFactory
import controllers.PublicCtrl.{rowStyle, titleStyle}
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey}
import models.LongRunningProcessStatus.{Done, Started}
import models.{Column, LongRunningProcessMonitor, SafetyWarrant}
import play.api.cache.AsyncCacheApi
import play.api.libs.Files.TemporaryFileCreator
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.LocalDate
import java.util.Locale
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
                                   settings:SettingDAO,
                                   cache:AsyncCacheApi, fileCreator: TemporaryFileCreator,
                                   config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  import DataProductsActor._
  private val D = Duration(5, duration.MINUTES)
  private val log = Logger(classOf[WarrantScrapingActor])
  
  
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
  
  def composePeriodicalReport(from: LocalDate, to: LocalDate, anLpm: LongRunningProcessMonitor): Unit = {
    var lpm = anLpm.copy(status = Started)
    cache.set(lpm.id, lpm)
    log.info( s"Composing periodical report ${from}-${to}")
    
    Await.result(safetyWarrants.refreshViews(), D)
    
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("ReportsCtrl"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    
    val table = document.addTable("Safety Warrants")
    val walker = table.getWalker
    Range(0, 11).foreach(i => {
      Column.printLong(i, walker)
      walker.next()
    })
    
    val tempPath = Paths.get(config.get[String]("klo.dataProductFolder")).resolve(s"${lpm.id}.ods")
    fileCreator.create(tempPath) // ensure later deletion by the reaper
    
    Using(Files.newOutputStream(tempPath)){
      writer.save
    }
    
    lpm = lpm.copy(status = Done)
    cache.set(lpm.id, lpm)
    
    log.info( s"Done composing periodical report ${from}-${to}")
    
  }
}
