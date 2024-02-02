package modules


import actors.{DataProductsActor, SafetyViolationSanctionScrapingActor, WarrantScrapingActor}
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import play.api.{Configuration, Logger}
import play.api.inject.SimpleModule
import play.api.inject._

import java.time.LocalDateTime
import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class InitDataProductsUpdates @Inject() (actorSystem:ActorSystem, conf:Configuration, @Named("svsScrapingActor") svsActor:ActorRef,
                                         @Named("DataProductsActor") dpActor:ActorRef, @Named("WarrantScrapingActor") swsActor:ActorRef)(
                                        implicit ec:ExecutionContext
) {
  
  def scheduleTasks():Unit = {
    val log = Logger( classOf[InitDataProductsUpdates])
    log.info("Init module started")
    val dpUpdateInterval = conf.get[Int]("klo.dataProductUpdate")
    actorSystem.scheduler.scheduleWithFixedDelay(dpUpdateInterval.seconds, dpUpdateInterval.seconds,
      dpActor, DataProductsActor.PossiblyUpdateWarrantTable()
    )
    log.info("DataProducts refresh scheduled")
    
    val safetyWarrantScrapingInterval = conf.get[Int]("scraper.safety.scrapeInterval").minutes
    val now = LocalDateTime.now()
    val minutesToMidnight = (23-now.getHour)*60+(60-now.getMinute)
    actorSystem.scheduler.scheduleAtFixedRate(minutesToMidnight.minutes, safetyWarrantScrapingInterval,
      swsActor, WarrantScrapingActor.StartScrapingSafety(0)
    )
    log.info( s"Safety warrant scraping scheduled, staring $minutesToMidnight from now." )
    actorSystem.scheduler.scheduleAtFixedRate(minutesToMidnight.minutes, 24.hours, dpActor, DataProductsActor.UpdateTemporalViews())
    log.info( s"Temporal views update scheduled, staring $minutesToMidnight from now." )
    
    val hourAfterMidnight = minutesToMidnight+60
    actorSystem.scheduler.scheduleAtFixedRate(hourAfterMidnight.minutes, 24.hours, svsActor, SafetyViolationSanctionScrapingActor.StartScrape)
    log.info(s"Safety violations sanctions scraping scheduled, staring $hourAfterMidnight from now.")
  
  }
  
  scheduleTasks()
  
}

class TasksModule extends SimpleModule(bind[InitDataProductsUpdates].toSelf.eagerly())