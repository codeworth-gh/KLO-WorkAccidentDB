package controllers

import actors.{DataProductsActor, ImportDataActor}
import org.apache.pekko.actor.ActorRef
import be.objectify.deadbolt.scala.DeadboltActions
import org.apache.pekko.util.ByteString
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.nio.file.{Files, Paths}
import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}


/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */

object HomeCtrl {

  private val feRouteSeq = Seq(
    routes.javascript.HelperTableCtrl.apiListRegions,
    routes.javascript.HelperTableCtrl.apiGetRegion,
    routes.javascript.BusinessEntityCtrl.apiListSanctionsFor
  )

  val feRouteHash:Int = Math.abs(feRouteSeq.map( r => r.f + r.name ).map( _.hashCode ).sum)

  private val beRouteSeq = Seq(
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
    routes.javascript.PublicCtrl.bizEntDetails,
    routes.javascript.ReportsCtrl.apiGenerateReport,
    routes.javascript.ReportsCtrl.apiReportStatus,
    routes.javascript.ReportsCtrl.getReportFile,
    routes.javascript.DataImportCtrl.apiImportSafetyWarrants,
    routes.javascript.DataImportCtrl.apiSafetyImportStatus
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
  def frontEndRoutes:Action[AnyContent] =
    Action { implicit request =>
      Ok(
        routing.JavaScriptReverseRouter("feRoutes")(
          HomeCtrl.feRouteSeq*
        )).as("text/javascript")
    }

  /**
    * Routes for the back-office part
    * @return
    */
  def backEndRoutes = deadbolt.SubjectPresent()() { implicit request =>
      Future(Ok(
        routing.JavaScriptReverseRouter("beRoutes")(
          HomeCtrl.beRouteSeq*
        )).as("text/javascript"))
  }

  def importDataFromFile:Action[ByteString] = localAction(cc.parsers.byteString){req =>
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
  
  def updateSafetyWarrantsOds():Action[ByteString] = localAction(cc.parsers.byteString){ req =>
    
    dataProductActor ! DataProductsActor.PossiblyUpdateWarrantTable()
    
    Accepted("data product updates started")
  }
  
  def notImplYet = TODO
  
}
