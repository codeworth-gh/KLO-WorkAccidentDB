package controllers

import actors.DataProductsActor
import be.objectify.deadbolt.scala.DeadboltActions
import com.github.jferard.fastods.OdsFactory
import dataaccess.SafetyWarrantDAO
import models.LongRunningProcessStatus.Done
import models.{Column, LongRunningProcessMonitor}
import org.apache.pekko.actor.ActorRef
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import views.Helpers

import java.nio.file.Paths
import java.time.LocalDate
import java.util.Locale
import javax.inject.{Inject, Named}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, duration}
import views.JsonConverters.*

class ReportsCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                            @Named("DataProductsActor")dataProductActor:ActorRef,
                           cache:AsyncCacheApi, conf:Configuration)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  private val log = Logger(classOf[ReportsCtrl])
  private val D = Duration(5, duration.MINUTES)
  
  def showReportsIndex() = deadbolt.SubjectPresent()(){ implicit req =>
    Future(Ok(views.html.backoffice.reportsIndex()))
  }
  
  def apiGenerateReport() = deadbolt.SubjectPresent()(cc.parsers.tolerantJson){ implicit req =>
    val data = req.body.validate[Map[String,String]].get
    val dateFmt = Helpers.dateFormats(Helpers.DateFmt.ISO_Date)
    val startDate = LocalDate.parse(data("start"), dateFmt )
    val endDate = LocalDate.parse(data("end"), dateFmt )
    log.info( s"Producing report for ${startDate} to ${endDate}")
    val monitor = LongRunningProcessMonitor.create(Some( s"${dateFmt.format(startDate)}_${dateFmt.format(endDate)}"))
    cache.set(monitor.id, monitor)
    dataProductActor ! DataProductsActor.CreatePeriodicalReport(startDate, endDate, monitor)
    Future(Ok(Json.toJson(monitor)))
  }
  
  def apiReportStatus( monitorId: String ) = deadbolt.SubjectPresent()(){ req =>
    cache.get[LongRunningProcessMonitor](monitorId).map {
      case None => notFoundJson(s"Can't find monitor with id $monitorId")
      case Some(m) => Ok(Json.toJson(m))
    }
  }
  
  def getReportFile(monitorId: String) = deadbolt.SubjectPresent()() { req =>
    cache.get[LongRunningProcessMonitor](monitorId).map {
      case None => notFoundJson(s"Can't find monitor with id $monitorId")
      case Some(m) => if ( m.status != Done ) {
        BadRequest("Current report status:\n"+m.toString)
      } else {
        val reportFile = Paths.get(conf.get[String]("klo.dataProductFolder")).resolve(s"${monitorId}.ods").toFile
        log.info("Sending file " + reportFile.toPath.toAbsolutePath.normalize())
        cache.remove(monitorId)
        Ok.sendFile(reportFile, inline=false, fileName = f=>Some(s"${m.message.get}.ods") )
      }
    }
  }
  
  
  
}
