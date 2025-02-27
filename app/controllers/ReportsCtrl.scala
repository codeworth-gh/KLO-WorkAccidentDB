package controllers

import be.objectify.deadbolt.scala.DeadboltActions
import org.apache.pekko.actor.ActorRef
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, ControllerComponents}

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

class ReportsCtrl @Inject()(deadbolt:DeadboltActions, cc:ControllerComponents,
                           @Named("WarrantScrapingActor") safetyWarrantImporter:ActorRef,
                           cache:AsyncCacheApi, conf:Configuration)
                          (implicit ec:ExecutionContext) extends AbstractController(cc) with I18nSupport with JsonApiHelper {
  private val log = Logger(classOf[ReportsCtrl])
  
  def showReportsIndex() = deadbolt.SubjectPresent()(){ implicit req =>
    Future(Ok(views.html.backoffice.reportsIndex()))
  }
  
}
