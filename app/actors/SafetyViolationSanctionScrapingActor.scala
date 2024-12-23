package actors

import actors.SafetyViolationSanctionScrapingActor.{NO_FOUND_MAX, ScrapeRecords, StartScrape}
import controllers.Assets
import dataaccess.{BusinessEntityDAO, SafetyViolationSanctionDAO, SafetyWarrantDAO, SettingDAO}
import models.{BusinessEntity, SafetyViolationSanction}
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props}
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue}

import java.time.LocalDate
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.concurrent.duration.{Duration, SECONDS}

object SafetyViolationSanctionScrapingActor {
  def props:Props = Props[SafetyViolationSanctionScrapingActor]()
  case object StartScrape
  case class ScrapeRecords( endpoint:String )
  val NO_FOUND_MAX=10
}

@Singleton
class SafetyViolationSanctionScrapingActor @Inject() (svsDAO:SafetyViolationSanctionDAO, settings:SettingDAO,
                                                      ws:WSClient, bizDAO:BusinessEntityDAO,
                                                      actorSystem:ActorSystem,
                                                      config:Configuration)(implicit anEc:ExecutionContext)
  extends Actor with JsonScraper {
  private val log = Logger(classOf[SafetyViolationSanctionScrapingActor])
  private val D = Duration(5, duration.MINUTES)
  private var noFoundCount = 0;
  
  override def receive: Receive = {
    case StartScrape =>
      noFoundCount = 0
      scrape(
        config.get[String]("scraper.sanctions.endpoint") + "&limit=" + config.get[String]("scraper.sanctions.limit") + "&sort=date desc"
      )
    case ScrapeRecords( url ) => scrape(url)
  }
  
  def scrape(endpoint:String ):Unit = {
    val server = config.get[String]("scraper.sanctions.server")
    if ( !config.get[Boolean]("scraper.sanctions.active") ) {
        log.info(s"Ignoring call to scrape sanctions from $endpoint - actor deactivated (scraper.sanctions.active != true)")
        return
    }
    
    log.info( s"Scraping safety violation sanctions. Url: ${server}${endpoint}")
    
    // scrape
    val response = ws.url(server+endpoint).withHttpHeaders("Content-Type" -> "application/json")
      .withHttpHeaders("Accept" -> "application/json")
      .withHttpHeaders("User-Agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:95.0) Gecko/20100101 Firefox/95.0")
      .withFollowRedirects(true)
    
    val result = Await.result( response.get(), D )
    
    try {
      // parse and decide whether we need to go back
      parse(result.json.asInstanceOf[JsObject]) match {
        case Some(nextUrl) =>
          val minSeconds = config.get[Int]("scraper.sanctions.minDelay")
          val maxSeconds = config.get[Int]("scraper.sanctions.maxDelay")
          val seconds = minSeconds + util.Random.nextInt(maxSeconds-minSeconds)
          log.info(s"Scheduling next safety violation sanction scrape in $seconds sec.")
          actorSystem.scheduler.scheduleOnce(Duration( seconds, SECONDS), self, ScrapeRecords(nextUrl))
        
        case None =>
          log.info(s"Scraping safety violation sanctions done for today")
          
      }
    } catch {
      case e:Exception => {
        log.warn(s"Error parsing sanctions response: ${e.getMessage}", e)
        log.warn("Response:\n" + result.toString)
        log.warn("Response Body:\n" + result.body)
      }
    }
    
  }
  
  def parse( res:JsObject ):Option[String] = {
    // check it's all ok
    
    val success = (res \ "success").get.as[JsBoolean].value
    if ( ! success ) {
      log.warn(s"Error scraping sanctions: /success != true")
      log.warn(s"Response JSON body: \n${res}")
      return None
    }
    
    // parse and store actual records
    val records = (res \ "result" \ "records").as[JsArray]
    val foundOnlyExisting = records.value.map( parseSingleRecord ).fold(false)(_||_)
    
    // if all records where new, return Some("_links/next") else return None.
    if ( foundOnlyExisting ) {
      noFoundCount = noFoundCount+1
      log.info(s"No new violations found (count: $noFoundCount)")
    } else {
      noFoundCount = 0
    }
    
    if ( noFoundCount==NO_FOUND_MAX )
      None
    else
      (res \ "result" \ "_links"  \ "next").toOption match {
        case None => None
        case Some(url) => Some(url.asInstanceOf[JsString].value)
      }
  }
  
  /**
   * Parse and store a single SVS.
   * @param jsVal
   * @return true iff the SVS was already scraped.
   */
  private def parseSingleRecord(jsVal:JsValue ):Boolean = {
    val jsonRec = jsVal.asInstanceOf[JsObject]
    
    // There's a typo in the key name that they might fix sometime
    val violationSiteKey = if (jsonRec.keys("adress")) "adress" else "address"
    val violationClauseKey = if (jsonRec.keys("volationclause")) "volationclause" else "violationclause"
    
    val svsRec = SafetyViolationSanction(
      id = 0,
      sanctionNumber = safeExtractLong( jsonRec, "number" ).getOrElse(0L).toInt,
      date =  safeExtractDate(jsonRec, "date").getOrElse(LocalDate.of(1970,1,1)),
      companyName = safeExtractStr(jsonRec, "companyname").getOrElse(""),
      pcNumber = safeExtractLong(jsonRec, "hpnumber"),
      violationSite = safeExtractStr(jsonRec, violationSiteKey).getOrElse(""),
      violationClause = safeExtractStr(jsonRec, violationClauseKey).getOrElse(""),
      sum = safeExtractLong( jsonRec, "sum").getOrElse(0L).toInt,
      commissionersDecision = safeExtractStr(jsonRec, "commissionersdecision"),
      kloBizEntId = None
    )
    
    if ( Await.result( svsDAO.isScraped(svsRec.sanctionNumber),D) ) {
      log.info(s"Safety violation sanction ${svsRec.sanctionNumber} already scrapped.")
      true
      
    } else {
      // link to existing company or enter a new one
      getKloBizIdFor( svsRec ) match {
        case Some(bizId) =>
          Await.result(svsDAO.store(svsRec.copy(kloBizEntId = Some(bizId))), D)
          log.info(s"Added safety violation sanction ${svsRec.sanctionNumber}")
          false
        case None =>
          val newBusiness = BusinessEntity(id = 0, name = svsRec.companyName,
            pcNumber = svsRec.pcNumber, phone = None, email = None, website = None,
            isPrivatePerson = svsRec.pcNumber.isEmpty, isKnownContractor = false, memo = None)
          val storedBusiness = Await.result(bizDAO.store(newBusiness), D)
          log.info(s"Added new business ${storedBusiness.id} (PC num: ${storedBusiness.pcNumber})")
          Await.result(svsDAO.store(svsRec.copy(kloBizEntId = Some(storedBusiness.id))), D)
          log.info(s"Added safety violation sanction ${svsRec.sanctionNumber}")
          false
      }
    }
  }
  
  private def getKloBizIdFor(sanction: SafetyViolationSanction):Option[Long] = {
    Await.result( bizDAO.findByPcNumOrName(sanction.pcNumber.getOrElse(-1), sanction.companyName), D ).map(_.id)
  }
  
}
