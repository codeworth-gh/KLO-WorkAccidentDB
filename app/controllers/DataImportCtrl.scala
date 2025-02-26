package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import models.{ImportMonitor, ImportMonitors}
import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logger}
import play.api.i18n.{I18nSupport, MessagesProvider}
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import views.JsonConverters

import java.nio.file.Paths
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}
import JsonConverters.importMonitorWrt
import actors.WarrantScrapingActor
import org.apache.pekko.actor.ActorRef

class DataImportCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                              @Named("WarrantScrapingActor") safetyWarrantImporter:ActorRef,
                               cache:AsyncCacheApi, conf:Configuration)
                              (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  private val log = Logger(classOf[DataImportCtrl])
  
  def showDataImportIndex() = deadbolt.SubjectPresent()() { implicit req =>
    Future(
      Ok(views.html.backoffice.dataImport(conf.get[String]("scraper.safety.csvUrl")))
      .withHeaders("Access-Control-Allow-Origin"->"*", "Access-Control-Allow-Methods"->"POST")
    )
  }
  
  def apiImportSafetyWarrants() = deadbolt.SubjectPresent()(cc.parsers.multipartFormData) { implicit req =>
    
    req.body.file("csvFile") match {
      case Some(csvTempFile) =>
        val dest = Paths.get( conf.get[String]("klo.dataProductFolder") ).resolve(csvTempFile.filename)
        csvTempFile.ref.moveTo(dest, true)
        log.info(s"Got Safety warrant csv file: ${dest}")
        val monitor = ImportMonitors.create(csvTempFile.filename)
        cache.set(monitor.id, monitor)
        safetyWarrantImporter ! WarrantScrapingActor.ImportFile(monitor, dest)
        Future( Ok(Json.toJson(monitor)) )
      case None => Future( BadRequest("Missing file") )
    }
  }
  
  def apiSafetyImportStatus( monitorId:String ) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      res <- cache.get[ImportMonitor](monitorId)
    } yield res match {
      case None => notFoundJson(s"$monitorId not found")
      case Some(m) => Ok(Json.toJson(m))
    }
  }
}
