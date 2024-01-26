package controllers

import org.apache.pekko.actor.ActorRef
import be.objectify.deadbolt.scala.DeadboltActions
import controllers.BusinessEntityCtrl.ACTIVE_ENTITY_MERGES
import dataaccess.{BusinessEntityDAO, SanctionsDAO}
import models.{BusinessEntity, Sanction}
import play.api.{Configuration, Logger}
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import views.{JsonConverters, PaginationInfo}

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success}
import actors.EntityMergeActor

object BusinessEntityCtrl {
  val PAGE_SIZE=30
  val ACTIVE_ENTITY_MERGES = new ConcurrentHashMap[UUID,String]()
}

class BusinessEntityCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                                   businessEntities:BusinessEntityDAO, sanctions:SanctionsDAO,
                                   @Named("EntityMergeActor") entityMergeActor:ActorRef,
                                   conf:Configuration, localAction:LocalAction
                                  )
          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  
  private val log = Logger(classOf[BusinessEntityCtrl])
  private val sanctionOptions:Map[String, Seq[String]] = loadSanctions(conf.get[String]("klo.sanctions"))
  
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
    "pcNumber" -> optional(longNumber),
    "phone" -> optional(text),
    "email" -> optional(text),
    "website" -> optional(text),
    "isPrivatePerson" -> boolean,
    "isKnownContractor" -> boolean,
    "memo" -> optional(text)
  )(BusinessEntity.apply)(BusinessEntity.unapply))
  
 
  def showNew() = deadbolt.SubjectPresent()() { implicit req =>
    Future( Ok(
      views.html.backoffice.businessEntitiesEditor(bizEntityForm.fill(new BusinessEntity(0,"",None, None, None, None, false, false, None)), sanctionOptions)
    ))
  }
  
  def showEdit(id:Long) = deadbolt.SubjectPresent()() { implicit req =>
    for {
      nttOpt <- businessEntities.get(id)
    } yield {
      nttOpt match {
        case None => NotFound("Business Entity not found.")
        case Some(ntt) => Ok(views.html.backoffice.businessEntitiesEditor(bizEntityForm.fill(ntt), sanctionOptions))
      }
    }
  }
  
  def doSaveEntity() = deadbolt.SubjectPresent()(){ implicit req =>
    bizEntityForm.bindFromRequest().fold(
      fwe => {
        fwe.errors.foreach( fe => log.info(fe.key + ": " + fe.message) )
        Future(BadRequest(views.html.backoffice.businessEntitiesEditor(fwe, sanctionOptions)))
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
  
  def getSimilarlyNamedEntities( baseName:String ) = deadbolt.SubjectPresent()(){ req =>
    for {
      names <- businessEntities.findSimilarNames(baseName)
    } yield {
      Ok(
        Json.toJson( names.map( p => Json.obj("id"->p._1, "name"->p._2) ) )
      ).as("application/json")
    }
  }
  
  def apiStoreSanction( entId:Long ) = deadbolt.SubjectPresent()(cc.parsers.tolerantJson){ req =>
    import views.JsonConverters.sanctionFmt
    
    req.body.validate[Sanction] match {
      case JsError(errors) =>
        log.warn(s"Error parsing sanction: " + (errors.mkString("\n")))
        Future(badRequestJson("Parse Error"))
      case JsSuccess(sanction, _) =>
        sanctions.store(sanction).map( r => Ok(Json.toJson(r)))
    }
  }
  
  def apiDeleteSanction( entId:String, sanctionId:Long ) = deadbolt.SubjectPresent()() { req =>
    for {
      _ <- sanctions.delete(sanctionId)
    } yield {
      okJson("deleted")
    }
  }
  
  def apiListSanctionsFor( entId:Long ) = Action.async{ implicit req =>
    import JsonConverters.sanctionFmt
    for {
      sancs <- sanctions.sanctionsForEntity(entId)
    } yield {
      Ok( Json.toJson(sancs) )
    }
  }
  
  def apiMergeEntities( from:Long, into:Long ) = deadbolt.SubjectPresent()() { req =>
    val mergeId = UUID.randomUUID()
    ACTIVE_ENTITY_MERGES.put( mergeId, "START")
    log.info(s"Starting entity merge: $from into $into under id $mergeId")
    entityMergeActor ! EntityMergeActor.MergeEntities(from, into, mergeId)
    
    Future(Accepted(Json.obj("mergeId"->mergeId)))
  }
  
  def apiGetEntityMergeStatus( id:String ) = deadbolt.SubjectPresent()() { req =>
    Future(
      try {
        val uuid = UUID.fromString(id)
        if ( ACTIVE_ENTITY_MERGES.containsKey(uuid) ) {
          ACTIVE_ENTITY_MERGES.get(uuid) match {
            case "DONE" => {
              ACTIVE_ENTITY_MERGES.remove(uuid)
              Ok(Json.obj("status"->"DONE"))
            }
            case x:String => Ok(Json.obj("status"->x))
          }
        } else {
          log.info( ACTIVE_ENTITY_MERGES.entrySet().asScala.map( p => p.getKey->p.getValue).mkString(" , ") )
          log.info(s"Requested: -$uuid-")
          NotFound("not in map")
        }
      } catch {
        case e:Exception => NotFound("invalid id")
      }
    )
  }
  
  def apiEnrichPCNums() = localAction(cc.parsers.anyContent(None)){ req=>
    businessEntities.enrichPCNums()
    Accepted("Enriching")
  }
  
  private def loadSanctions(raw:String):Map[String,Seq[String]] = {
    val lines = raw.split("\n").map(_.trim).filter(_.nonEmpty)
    val agg = collection.mutable.Map[String, Seq[String]]()
    var curAuth:String=null
    var curReasons:collection.mutable.Buffer[String] = collection.mutable.Buffer[String]()
    
    for ( line <- lines ) {
      if ( line.startsWith("+") ) {
        if ( curAuth != null ) {
          agg(curAuth) = curReasons.toSeq;
          curReasons = collection.mutable.Buffer[String]();
        }
        curAuth = line.substring(2).trim
      } else {
        curReasons += line.trim
      }
    }
    agg(curAuth) = curReasons.toSeq;
    agg.toMap
  }
  
}
