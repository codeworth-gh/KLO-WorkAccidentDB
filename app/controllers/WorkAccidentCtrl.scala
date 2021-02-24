package controllers

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import com.typesafe.config.Config
import dataaccess.{BusinessEntityDAO, RegionsDAO, WorkAccidentDAO}
import models.{BusinessEntity, Citizenship, Industry, InjuredWorker, InjuryCause, Region, Severity, WorkAccident}
import play.api.{Configuration, Logger}
import play.api.i18n.{I18nSupport, MessagesProvider}
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.data._
import play.api.data.Forms._

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId}
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
  date: LocalDate,
  time: Option[LocalTime],
  entrepreneurId:Option[Long],
  entrepreneurName:Option[String],
  region: Option[Int],
  blogPostUrl: String,
  details: String,
  investigation:String,
  mediaReports:Seq[String],
  publicRemarks:String,
  sensitiveRemarks:String,
  injured:Seq[InjuredWorkerFD]
)

class WorkAccidentCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents, conf:Configuration,
                                 accidents:WorkAccidentDAO, regions:RegionsDAO, businesses:BusinessEntityDAO)
                                (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  private val log = Logger(classOf[WorkAccidentCtrl])
  
  private val workAccidentForm = Form(mapping(
    "id"->longNumber,
    "date"->localDate,
    "time"->optional(localTime("HH:mm")),
    "entrepreneurId"->optional(longNumber),
    "entrepreneurName"->optional(text),
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
      0, LocalDate.now(ZoneId.of(conf.get[String]("timeZoneId"))),
      None, None, None, None, "", "", "", Seq(), "","",Seq()
    )
    showEditAccidentForm(workAccidentForm.fill(wa))
    
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
  
  def doSaveAccident() = deadbolt.SubjectPresent()(){ implicit req =>
    workAccidentForm.bindFromRequest().fold(
      fwe=>{
        log.warn( fwe.errors.mkString("\n"))
        showEditAccidentForm(fwe)
      },
      wafd => {
        Future(Ok(wafd.toString.replaceAll(",", "\n")))
      }
    )
  }
  
  private def showEditAccidentForm( aForm:Form[WorkAccidentFD] )(implicit req:AuthenticatedRequest[_], msgs:MessagesProvider) = {
    for {
      rgns <- regions.list()
      bePr <- businesses.listIdNamePairs()
    } yield {
      Ok(views.html.backoffice.workAccidentEditor(aForm, rgns, bePr))
    }
  }
  
}
