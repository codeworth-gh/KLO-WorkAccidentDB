package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO}
import models.BusinessEntity
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}
import views.PaginationInfo

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object BusinessEntityCtrl {
  val PAGE_SIZE=40
}

class BusinessEntityCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                                   businessEntities:BusinessEntityDAO)
          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  def backofficeIndex(name:Option[String]) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      ents <- name.map(businessEntities.listByName).getOrElse(businessEntities.list(0, 1000))
    } yield {
      Ok( views.html.backoffice.businessEntitiesIndex(ents,PaginationInfo(1,1)) )
    }
  }
  
  private val bizEntityForm = Form(mapping(
    "id"->longNumber,
    "name" -> nonEmptyText,
    "phone" -> optional(text),
    "email" -> optional(text),
    "website" -> optional(text),
    "isPrivatePerson" -> boolean,
    "memo" -> optional(text)
  )(BusinessEntity.apply)(BusinessEntity.unapply))
  
 
  def showNew() = deadbolt.SubjectPresent()() { implicit req =>
    Future( Ok(
      views.html.backoffice.businessEntitiesEditor(bizEntityForm.fill(new BusinessEntity(0,"",None, None, None, false, None)))
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
  
}
