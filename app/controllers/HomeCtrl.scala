package controllers

import actors.{DataProductsActor, ImportDataActor}
import actors.WarrantScrapingActor.StartScrapingSafety
import akka.actor.ActorRef
import be.objectify.deadbolt.scala.DeadboltActions
import play.api._
import play.api.i18n.I18nSupport
import play.api.mvc._

import java.nio.file.{Files, Paths}
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}


/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */

object HomeCtrl {

  val feRouteSeq = Seq(
    routes.javascript.HelperTableCtrl.apiListRegions,
    routes.javascript.HelperTableCtrl.apiGetRegion,
    routes.javascript.BusinessEntityCtrl.apiListSanctionsFor
  )

  val feRouteHash:Int = Math.abs(feRouteSeq.map( r => r.f + r.name ).map( _.hashCode ).sum)

  val beRouteSeq = Seq(
    routes.javascript.HelperTableCtrl.apiAddRegion,
    routes.javascript.HelperTableCtrl.apiEditRegion,
    routes.javascript.HelperTableCtrl.apiDeleteRegion,
    routes.javascript.HelperTableCtrl.apiAddIndustry,
    routes.javascript.HelperTableCtrl.apiEditIndustry,
    routes.javascript.HelperTableCtrl.apiDeleteIndustry,
    routes.javascript.HelperTableCtrl.apiAddCitizenship,
    routes.javascript.HelperTableCtrl.apiEditCitizenship,
    routes.javascript.HelperTableCtrl.apiDeleteCitizenship,
    routes.javascript.HelperTableCtrl.apiAddInjuryCause,
    routes.javascript.HelperTableCtrl.apiEditInjuryCause,
    routes.javascript.HelperTableCtrl.apiDeleteInjuryCause,
    routes.javascript.HelperTableCtrl.apiAddRelationsToAccidents,
    routes.javascript.HelperTableCtrl.apiEditRelationsToAccidents,
    routes.javascript.HelperTableCtrl.apiDeleteRelationsToAccidents,
    routes.javascript.UserCtrl.apiAddUser,
    routes.javascript.UserCtrl.apiReInviteUser,
    routes.javascript.UserCtrl.apiDeleteInvitation,
    routes.javascript.UserCtrl.doDeleteUser,
    routes.javascript.WorkAccidentCtrl.doDeleteEntity,
    routes.javascript.WorkAccidentCtrl.backofficeIndex,
    routes.javascript.BusinessEntityCtrl.apiStoreSanction,
    routes.javascript.BusinessEntityCtrl.apiDeleteSanction,
    routes.javascript.BusinessEntityCtrl.apiListSanctionsFor,
    routes.javascript.BusinessEntityCtrl.doDeleteEntity,
    routes.javascript.BusinessEntityCtrl.getSimilarlyNamedEntities,
    routes.javascript.BusinessEntityCtrl.backofficeIndex,
    routes.javascript.BusinessEntityCtrl.apiMergeEntities,
    routes.javascript.BusinessEntityCtrl.apiGetEntityMergeStatus,
    routes.javascript.PublicCtrl.bizEntDetails
  )

  val beRouteHash:Int = Math.abs(beRouteSeq.map( r => r.f + r.name ).map( _.hashCode ).sum)
}

class HomeCtrl @Inject()(deadbolt:DeadboltActions, localAction:LocalAction,
                         @Named("ImportDataActor")importActor:ActorRef,
                         @Named("WarrantScrapingActor")swActor:ActorRef,
                         @Named("DataProductsActor")dataProductActor:ActorRef,
                         cc: ControllerComponents)
                        (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport {
  
  private val logger = Logger(classOf[HomeCtrl])
  
  /**
    * Routes for the public part
    * @return
    */
  def frontEndRoutes =
    Action { implicit request =>
      Ok(
        routing.JavaScriptReverseRouter("feRoutes")(
          HomeCtrl.feRouteSeq: _*
        )).as("text/javascript")
    }

  /**
    * Routes for the back-office part
    * @return
    */
  def backEndRoutes = deadbolt.SubjectPresent()() { implicit request =>
      Future(Ok(
        routing.JavaScriptReverseRouter("beRoutes")(
          HomeCtrl.beRouteSeq: _*
        )).as("text/javascript"))
  }

  def importDataFromFile = localAction(cc.parsers.byteString){req =>
    val fileName = new String(req.body.toArray)
    val path = Paths.get(fileName)
    if ( Files.exists(path) ) {
      logger.info("Importing " + path.toAbsolutePath.toString )
      importActor ! ImportDataActor.ImportFile(path)
      Ok("Import started")
    } else {
      BadRequest("Path not found on FS: " + path.toAbsolutePath.toString)
    }
  }
  
  def scrapeSafety(skip:Option[Int]) = localAction(cc.parsers.byteString){ req =>
    var msg = StartScrapingSafety(skip.getOrElse(0))
    swActor ! msg
    Accepted("Scraping started: " + msg)
  }
  
  def updateSafetyWarrantsOds() = localAction(cc.parsers.byteString){ req =>
    
    dataProductActor ! DataProductsActor.PossiblyUpdateWarrantTable()
    
    Accepted("data product updates started")
  }
  
  def notImplYet = TODO
  
}
