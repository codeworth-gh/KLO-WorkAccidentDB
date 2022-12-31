package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import dataaccess.BusinessEntityDAO
import models.BusinessEntity
import play.api.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import views.PaginationInfo

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object BusinessEntityCtrl {
  val PAGE_SIZE=30
}

class BusinessEntityCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                                   businessEntities:BusinessEntityDAO)
          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  private val log = Logger(classOf[BusinessEntityCtrl])
  
  def backofficeIndex(pName:Option[String], pSortBy:Option[String]=None, pAsc:Option[String]=None, pStart:Option[Int]=None) = deadbolt.SubjectPresent()() { implicit req =>
    val start = pStart.getOrElse(1)
    val asc = pAsc.getOrElse("t").trim=="t"
    val sortBy = BusinessEntityDAO.SortKey.named(pSortBy.getOrElse("Name") ).getOrElse(BusinessEntityDAO.SortKey.Name)
    for {
      ents <- businessEntities.list((start-1)*BusinessEntityCtrl.PAGE_SIZE, BusinessEntityCtrl.PAGE_SIZE, sortBy, asc)
      count <- businessEntities.countAll
    } yield {
      Ok( views.html.backoffice.businessEntitiesIndex(ents,PaginationInfo(start,Math.ceil(count/BusinessEntityCtrl.PAGE_SIZE.toDouble).toInt ), sortBy, asc) )
    }
  }
  
  private val bizEntityForm = Form(mapping(
    "id"->longNumber,
    "name" -> nonEmptyText,
    "phone" -> optional(text),
    "email" -> optional(text),
    "website" -> optional(text),
    "isPrivatePerson" -> boolean,
    "isKnownContractor" -> boolean,
    "memo" -> optional(text)
  )(BusinessEntity.apply)(BusinessEntity.unapply))
  
 
  def showNew() = deadbolt.SubjectPresent()() { implicit req =>
    Future( Ok(
      views.html.backoffice.businessEntitiesEditor(bizEntityForm.fill(new BusinessEntity(0,"",None, None, None, false, false, None)))
    ))
  }
  
  def showEdit(id:Long) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      nttOpt <- businessEntities.get(id)
    } yield {
      nttOpt match {
        case None => NotFound("Business Entity not found.")
        case Some(ntt) => Ok(views.html.backoffice.businessEntitiesEditor(bizEntityForm.fill(ntt)))
      }
    }
  }
  
  def doSaveEntity() = deadbolt.SubjectPresent()(){ implicit req =>
    bizEntityForm.bindFromRequest().fold(
      fwe => {
        fwe.errors.foreach( fe => log.info(fe.key + ": " + fe.message) )
        Future(BadRequest(views.html.backoffice.businessEntitiesEditor(fwe)))
      },
      bizEnt => {
        val msgs = request2Messages(req)
        businessEntities.store( bizEnt ).map( _ =>
          Redirect(routes.BusinessEntityCtrl.backofficeIndex(None,None,None,None)
          ).flashing(FlashKeys.MESSAGE->Informational(Informational.Level.Success, msgs("businessEntities.saved", bizEnt.name) ,"").encoded)
        )
      }
    )
  }
  
  def doDeleteEntity(id:Long) = deadbolt.SubjectPresent()(){ req =>
    businessEntities.delete(id).map({
      case Failure(exception) => log.warn("Error while deleting business entity", exception)
        internalServerErrorJson(exception.getMessage)
      case Success(value) => okJson("deleted");
    })
  }
  
}
