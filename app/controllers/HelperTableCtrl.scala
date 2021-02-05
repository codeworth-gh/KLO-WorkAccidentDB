package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import dataaccess.{CitizenshipsDAO, IdNameDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO}
import models.{Citizenship, Industry, InjuryCause, Region}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.libs.json.{Format, JsError, JsSuccess, JsValue, Json, Writes}
import play.api.mvc.{AbstractController, ControllerComponents, Request}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.JsonConverters._

import scala.util.{Failure, Success}

class HelperTableCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
          regions:RegionsDAO, citizenships:CitizenshipsDAO, injuryCauses: InjuryCausesDAO, industries:IndustriesDAO)
                               (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  private val log = Logger(classOf[HelperTableCtrl])
  
  def helperTablesIndex = deadbolt.SubjectPresent()(){ implicit req =>
    for {
      regionList <- regions.list()
      citizenshipList <- citizenships.list()
      injuryCauseList <- injuryCauses.list()
      industryList <- industries.list()
    } yield {
      Ok( views.html.backoffice.helperTablesIndex(regionList, citizenshipList, injuryCauseList, industryList) )
    }
  }
  
  private def apiList[T](store:IdNameDAO[T, _])(implicit tjs:Writes[T]) = {
    for {
      regs:Seq[T] <- store.list()
    } yield {
      Ok( Json.toJson(regs) )
    }
  }
  
  private def apiGet[T](store:IdNameDAO[T, _], id:Int)(implicit tjs:Writes[T]) = {
    for {
      itmOpt <- store.get(id)
    } yield {
      itmOpt match {
        case None => notFoundJson(s"No item with id $id")
        case Some(itm) => Ok(Json.toJson(itm))
      }
    }
  }
  
  def apiAdd[T](req:Request[JsValue], store:IdNameDAO[T, _])(implicit tjs:Format[T]) = {
    req.body.validate[T] match {
      case JsSuccess(item,_) => store.put(item).map(newR => Created(Json.toJson(newR)))
      case JsError(errors) => Future(badRequestJson(errors))
    }
  }
  
  def apiEdit[T](id:Int, req:Request[JsValue], store:IdNameDAO[T, _], nm:T=>T)(implicit tjs:Format[T]) = {
    req.body.validate[T] match {
      case JsSuccess(itm,_) => store.put(nm(itm)).map(newItem => Created(Json.toJson(newItem)))
      case JsError(errors) => Future(badRequestJson(errors))
    }
  }
  
  def apiDelete[T](id:Int, store:IdNameDAO[T,_]) = {
    for {
      delRes <- store.delete(id)
    } yield {
      delRes match {
        case Success(value) => okJson(s" $id deleted")
        case Failure(t) => {
          log.warn(s"Error while deleting id: $id on $store", t)
          internalServerErrorJson("Deletion failed.")
        }
      }
    }
  }
  
  def apiListRegions = Action.async{ req => apiList(regions) }
  def apiGetRegion(id:Int) = Action.async{ req => apiGet(regions, id) }
  def apiAddRegion() = Action.async(cc.parsers.tolerantJson){ req => apiAdd(req, regions ) }
  def apiEditRegion(id:Int) = Action.async(cc.parsers.tolerantJson){ req => apiEdit(id, req, regions, (r:Region)=>r.copy(id=id)) }
  def apiDeleteRegion(id:Int) = Action.async{ req => apiDelete(id, regions) }
  
  def apiListCitizenships = Action.async{ req => apiList(citizenships) }
  def apiGetCitizenship(id:Int) = Action.async{ req => apiGet(citizenships, id) }
  def apiAddCitizenship() = Action.async(cc.parsers.tolerantJson){ req => apiAdd(req, citizenships ) }
  def apiEditCitizenship(id:Int) = Action.async(cc.parsers.tolerantJson){ req => apiEdit(id, req, citizenships, (r:Citizenship)=>r.copy(id=id)) }
  def apiDeleteCitizenship(id:Int) = Action.async{ req => apiDelete(id, citizenships) }
  
  def apiListIndustries = Action.async{ req => apiList(industries) }
  def apiGetIndustry(id:Int) = Action.async{ req => apiGet(industries, id) }
  def apiAddIndustry() = Action.async(cc.parsers.tolerantJson){ req => apiAdd(req, industries ) }
  def apiEditIndustry(id:Int) = Action.async(cc.parsers.tolerantJson){ req => apiEdit(id, req, industries, (r:Industry)=>r.copy(id=id)) }
  def apiDeleteIndustry(id:Int) = Action.async{ req => apiDelete(id, industries) }

  def apiListInjuryCauses = Action.async{ req => apiList(injuryCauses) }
  def apiGetInjuryCause(id:Int) = Action.async{ req => apiGet(injuryCauses, id) }
  def apiAddInjuryCause() = Action.async(cc.parsers.tolerantJson){ req => apiAdd(req, injuryCauses ) }
  def apiEditInjuryCause(id:Int) = Action.async(cc.parsers.tolerantJson){ req => apiEdit(id, req, injuryCauses, (r:InjuryCause)=>r.copy(id=id)) }
  def apiDeleteInjuryCause(id:Int) = Action.async{ req => apiDelete(id, injuryCauses) }
  
}
