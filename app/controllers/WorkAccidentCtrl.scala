package controllers

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import com.typesafe.config.Config
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, WorkAccidentDAO}
import models.{BusinessEntity, Citizenship, Industry, InjuredWorker, InjuryCause, Region, Severity, WorkAccident}
import play.api.{Configuration, Logger}
import play.api.i18n.{I18nSupport, MessagesProvider}
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.data._
import play.api.data.Forms.{optional, _}

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class InjuredWorkerFD (
  id:Long,
  name:String,
  age:Option[Int],
  citizenship:Option[Int],
  industry:Option[Int],
  employerName:Option[String],
  from:String,
  injuryCause: Option[Int],
  injurySeverity: Option[Int],
  injuryDescription: String,
  publicRemarks:String,
  sensitiveRemarks:String
)
object InjuredWorkerFD {
  def make(iw:InjuredWorker) = InjuredWorkerFD(iw.id, iw.name, iw.age, iw.citizenship.map(_.id), iw.industry.map(_.id),
    iw.employer.map(_.name), iw.from, iw.injuryCause.map(_.id), iw.injurySeverity.map(_.id), iw.injuryDescription,
    iw.publicRemarks, iw.sensitiveRemarks
  )
}

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
object WorkAccidentFD{
  def make(wa: WorkAccident):WorkAccidentFD = {
    val time = wa.when.toLocalTime
    val timeOpt = if ( time.getHour==0 && time.getMinute==0) None else Some(time)
    WorkAccidentFD(wa.id, wa.when.toLocalDate, timeOpt, wa.entrepreneur.map(_.id), wa.entrepreneur.map(_.name), wa.region.map(_.id),
      wa.blogPostUrl, wa.details, wa.investigation, wa.mediaReports.toSeq.sorted, wa.publicRemarks, wa.sensitiveRemarks,
      wa.injured.toSeq.map(InjuredWorkerFD.make)
    )
  }
}

class WorkAccidentCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents, conf:Configuration,
                                 accidents:WorkAccidentDAO, regions:RegionsDAO, businesses:BusinessEntityDAO,
                                 citizenships: CitizenshipsDAO, causes:InjuryCausesDAO, industries:IndustriesDAO
                                )(implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  private val log = Logger(classOf[WorkAccidentCtrl])
  
  private val injuredWorkerMapping = mapping(
    "id"->longNumber,
    "name"->text,
    "age"->optional(number),
    "citizenship"->optional(number),
    "industry"->optional(number),
    "employerName"->optional(text),
    "from"->text,
    "injuryCause" -> optional(number),
    "injurySeverity" -> optional(number),
    "injuryDescription" -> text,
    "publicRemarks" -> text,
    "sensitiveRemarks" -> text
  )(InjuredWorkerFD.apply)(InjuredWorkerFD.unapply)
  
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
    "injured"->seq(injuredWorkerMapping)
  )(WorkAccidentFD.apply)(WorkAccidentFD.unapply))
  
  def backofficeIndex() = deadbolt.SubjectPresent()() { implicit req =>
    Future(Ok(views.html.backoffice.workAccidentsIndex(Seq(), null)))
  }
  
  def showNew() = deadbolt.SubjectPresent()() { implicit req =>
    val wa = WorkAccidentFD(
      0, LocalDate.now(ZoneId.of(conf.get[String]("timeZoneId"))),
      None, None, None, None, "", "", "", Seq(), "","",Seq(
        InjuredWorkerFD(0, "", None, None, None, None, "", None, None, "", "", "")
      )
    )
    showEditAccidentForm(workAccidentForm.fill(wa))
  }
  
  def showEdit(id:Long) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      wa  <- accidents.getAccident(id)
      res <- wa match {
        case      None => Future(NotFound(s"Accident with id $id not found."))
        case Some(acc) => showEditAccidentForm(workAccidentForm.fill(WorkAccidentFD.make(acc)))
      }
    } yield res
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
      inds <- industries.list()
      ctzs <- citizenships.list()
      ijcs <- causes.list()
    } yield {
      Ok(views.html.backoffice.workAccidentEditor(aForm, rgns, bePr, inds, ctzs, ijcs))
    }
  }
  
}
