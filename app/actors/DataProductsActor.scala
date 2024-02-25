package actors

import org.apache.pekko.actor.{Actor, Props}
import com.github.jferard.fastods.OdsFactory
import controllers.PublicCtrl.{rowStyle, titleStyle}
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey}
import models.{Column, SafetyWarrant}
import play.api.{Configuration, Logger}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.Locale
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.util.Using

object DataProductsActor {
  def props:Props = Props[DataProductsActor]()
  case class PossiblyUpdateWarrantTable()
  case class UpdateTemporalViews()
  
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
                                   config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  import DataProductsActor._
  private val D = Duration(5, duration.MINUTES)
  private val log = Logger(classOf[WarrantScrapingActor])
  
  
  override def receive: Receive = {
    case PossiblyUpdateWarrantTable() => {
      if ( ! settings.isTrueish(SettingKey.SafetyWarrantProductsNeedUpdate) ) {
        log.info("Data products do not need an update")

      } else {
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
}
