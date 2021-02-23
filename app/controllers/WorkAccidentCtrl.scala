package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import com.typesafe.config.Config
import dataaccess.WorkAccidentDAO
import models.{BusinessEntity, Citizenship, Industry, InjuredWorker, InjuryCause, Region, Severity, WorkAccident}
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.data._
import play.api.data.Forms._

import java.time.{LocalDateTime, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class InjuredWorkerFD(
  id:Long,
  name:String,
  age:Option[Int],
  citizenship:Option[Int],
  industry:Option[Int],
  from:String,
  injuryCause: Option[Int],
  injurySeverity: Option[Int],
  injuryDescription: String,
  publicRemarks:String,
  sensitiveRemarks:String
)

case class WorkAccidentFD(
  id:Long,
  when: LocalDateTime,
  entrepreneur:Option[Long],
  region: Option[Int],
  blogPostUrl: String,
  details: String,
  investigation:String,
  mediaReports:Seq[String],
  publicRemarks:String,
  sensitiveRemarks:String,
  injured:Seq[InjuredWorkerFD]
)

class WorkAccidentCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                                 conf:Configuration,
                                 accidents:WorkAccidentDAO)
                                (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  
  private val workAccidentFrom = Form(mapping(
    "id"->longNumber,
    "when"->localDateTime,
    "entrepreneur"->optional(longNumber),
    "region"->optional(number),
    "blogPostUrl"->text,
    "details"->text,
    "investigation"->text,
    "mediaReports"->seq(text),
    "publicRemarks"->text,
    "sensitiveRemarks"->text,
    "injured"->ignored(Seq[InjuredWorkerFD]())
  )(WorkAccidentFD.apply)(WorkAccidentFD.unapply))
  
  def backofficeIndex() = deadbolt.SubjectPresent()() { implicit req =>
    Future(Ok(views.html.backoffice.workAccidentsIndex(Seq(), null)))
  }
  
  def showNew() = deadbolt.SubjectPresent()() { implicit req =>
    val wa = WorkAccidentFD(
      0, LocalDateTime.now(ZoneId.of(conf.get[String]("timeZoneId"))), None, None, "", "", "", Seq(), "","",Seq()
    )
    Future(Ok(views.html.backoffice.workAccidentEditor(workAccidentFrom.fill(wa))))
  }
  
  def showEdit(id:Long) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      wa <- accidents.getAccident(id)
    } yield {
      wa match {
        case None => NotFound(s"Accident with id $id not found.")
        case Some(acc) => Ok( acc.toString )
      }
    }
  }
  
  def doSaveEntity() = deadbolt.SubjectPresent()(){ implicit req =>
    Future(Redirect(routes.WorkAccidentCtrl.backofficeIndex()))
  }
  
}
