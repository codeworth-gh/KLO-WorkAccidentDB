package actors

import actors.WarrantScrapingActor.ldtFmt
import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import controllers.Assets
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey}
import models.SafetyWarrant
import play.api.libs.json.{JsArray, JsBoolean, JsDefined, JsNull, JsNumber, JsObject, JsString, JsUndefined, JsValue}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import scala.concurrent.duration.Duration
import scala.io.Source

object WarrantScrapingActor {
  def props: Props = Props[WarrantScrapingActor]()
  
  val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  val ldtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  case object StartScrape
  case class ScrapeRecords( endpoint:String )
}

@Singleton
class WarrantScrapingActor @Inject() (safetyWarrants:SafetyWarrantDAO, settings:SettingDAO, ws:WSClient,
                                      actorSystem:ActorSystem,
                                      config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  private val log = Logger(classOf[WarrantScrapingActor])
  private val D = Duration(5, duration.MINUTES)
  import WarrantScrapingActor._
  private val mutedCategories = config.get[Seq[String]]("scraper.safety.mutedCategories").toSet
  
  
  override def receive: Receive = {
    case StartScrape => scrape(
      config.get[String]("scraper.safety.endpoint") + "&limit=" + config.get[String]("scraper.safety.limit") + "&sort=send_date desc"
    )
    case ScrapeRecords( url ) => scrape(url)
  }

  def scrape(endpoint:String ):Unit  = {
    val server = config.get[String]("scraper.safety.server")
    if ( !config.get[Boolean]("scraper.safety.active") ) {
      log.info(s"Ignoring call to scrape safety warrants from $endpoint - actor deactivated (scraper.safety.active != true)")
      return
    }
    
    log.info( s"Scraping safety warrants. Url: ${server}${endpoint}")
    
    // Query service
    val response = ws.url(server+endpoint).withHttpHeaders("Content-Type" -> "application/json")
      .withHttpHeaders("Accept" -> "application/json")
      .withHttpHeaders("User-Agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:95.0) Gecko/20100101 Firefox/95.0")
      .withFollowRedirects(true)
    val result = Await.result( response.get(), D )
    
    // Parse result
    try {
      // parse and decide whether we need to go back
      parse(result.json.asInstanceOf[JsObject]) match {
        case Some(nextUrl) =>
          val minSeconds = config.get[Int]("scraper.safety.minDelay")
          val maxSeconds = config.get[Int]("scraper.safety.maxDelay")
          val seconds = minSeconds + util.Random.nextInt(maxSeconds-minSeconds)
          log.info(s"Scheduling next warrants scrape in $seconds sec.")
          actorSystem.scheduler.scheduleOnce(Duration( seconds, TimeUnit.SECONDS), self, ScrapeRecords(nextUrl))
        
        case None =>
          log.info(s"Scraping safety violation sanctions done for today")
        
      }
    } catch {
      case e:Exception => {
        log.warn(s"Error parsing warrants response: ${e.getMessage}", e)
        log.warn("Response:\n" + result.toString)
        log.warn("Response Body:\n" + result.body)
      }
    }
  }
  
  def parse( res:JsObject ):Option[String] = {
    // check it's all ok
    
    val success = (res \ "success").get.as[JsBoolean].value
    if ( ! success ) {
      log.warn(s"Error scraping safety warrants: /success != true")
      log.warn(s"Response JSON body: \n${res}")
      return None
    }
    
    // parse and store actual records
    val records = (res \ "result" \ "records").as[JsArray]
    val foundExisting = records.value.map( parseSingleRecord ).fold(false)(_||_)
    
    // if all records where new, return Some("_links/next") else return None.
    if ( foundExisting )
      None
    else
      Some((res \ "result" \ "_links"  \ "next").get.asInstanceOf[JsString].value)
  }
  
  private def parseSingleRecord(jsVal:JsValue ):Boolean = {
    val jsonRec = jsVal.asInstanceOf[JsObject]
    // in case they fix spelling at some point
    val warrantIdKey = if (jsonRec.keys("warrant_id")) "warrant_id" else "warrent_id"
    val warrant = SafetyWarrant(
      id = (jsonRec \ warrantIdKey ).get.asInstanceOf[JsString].value.trim.toLong,
      sentDate = LocalDate.parse( (jsonRec \ "send_date").get.as[JsString].value, dateFmt ),
      operatorTextId = (jsonRec \ "work_id").get.as[JsNumber].value.toString(),
      operatorName   = (jsonRec \ "work_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      cityName = (jsonRec \ "city_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      executorName = (jsonRec \ "executor_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      categoryName = (jsonRec \ "category_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      felony       = (jsonRec \ "felony_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      law          = (jsonRec \ "law_name").toOption.map( _.as[JsString].value ).getOrElse(""),
      clause       = (jsonRec \ "clause_name").toOption.map({
        case JsNull => ""
        case s: JsString => s.value
      }).getOrElse(""),
      scrapeDate = LocalDateTime.now(),
      None, None, None
    )
    if ( mutedCategories(warrant.categoryName) ) {
      log.info(s"Skipping scraped warrant ${warrant.id} since its category, ${warrant.categoryName} is muted.")
      false
      
    } else {
      if ( Await.result(safetyWarrants.exists(warrant.id), D) ) {
        log.info(s"Warrant ${warrant.id} already scrapped.")
        true
        
      } else {
        Await.result(safetyWarrants.store(warrant), D)
        settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "yes")
        log.info(s"Adding scraped warrant ${warrant.id}.")
        false
      }
    }
  }
  
  // -- old
  
  private def parse_old(jsRes:JsValue, timestamp:LocalDateTime):Seq[SafetyWarrant] = {
    if ( ! jsRes.isInstanceOf[JsObject] ) {
      log.warn("Error scraping safety warrants: did not receive a JSON object. Value: '" + jsRes.toString + "'")
      return Seq()
    }
    val baseObj = jsRes.asInstanceOf[JsObject]
    (baseObj \ "Results") match {
      case JsUndefined()    => {
        log.warn("Error scraping safety warrants: received object does not contain 'Results' field.")
        log.warn(baseObj.toString())
        Seq()
      }
    
      case JsDefined(value) => try {
        parseResultArray(value.asInstanceOf[JsArray], timestamp)
      } catch {
        case e: Exception => {
          log.warn("Error scraping safety warrants: " + e.getMessage, e)
          log.warn("received obj: " + value.toString)
          Seq()
        }
      }
    }
  }
  
  
  private def delay():Unit = {
    val min = config.get[Int]("scraper.safety.minDelay")
    val max = config.get[Int]("scraper.safety.maxDelay")
    val point = scala.util.Random.nextInt(max-min)
    val secDelay = (min+point)
    log.info(s"Sleeping $secDelay sec")
    Thread.sleep(1000*secDelay)
  }
  
  private def parseResultArray(value: JsArray, timestamp:LocalDateTime):Seq[SafetyWarrant] = {
    val asSeq = value.validate[Seq[JsValue]]
    asSeq.get.flatMap( w => parseSingleWarrant(w, timestamp) )
  }
  
  private def parseSingleWarrant( jsv:JsValue, timestamp:LocalDateTime ):Option[SafetyWarrant] = {
    import WarrantScrapingActor.dateFmt
    val dataObj = (jsv.asInstanceOf[JsObject] \ "Data").get.asInstanceOf[JsObject]
    try {
      Some(SafetyWarrant(
        //            typo is in JSON schema
        (dataObj \ "warrent_id").get.as[JsString].value.toInt,
        LocalDate.parse( (dataObj \ "send_date").get.as[JsString].value, dateFmt ),
        (dataObj \ "work_id").get.as[JsString].value,
        (dataObj \ "work_name").get.as[JsString].value,
        (dataObj \ "city_name").get.as[JsString].value,
        (dataObj \ "executor_name").get.as[JsString].value,
        (dataObj \ "category_name").get.as[JsString].value,
        (dataObj \ "felony_name").get.as[JsString].value,
        (dataObj \ "law_name").get.as[JsString].value,
        (dataObj \ "clause_name").get.as[JsString].value,
        timestamp,
        None, None, None
      ))
    } catch {
      case e:Exception => {
        log.warn("Error parsing single warrant: " + e.getMessage, e)
        log.warn( jsv.toString() )
        None
      }
    }
  }
  
  private def eval(skip:Int, src:String):String = {
    val values = Map( "SKIP"->skip.toString, "DYNAMIC_TEMPLATE_ID"->config.get[String]("scraper.safety.dynamicTemplateId") )
    var res = src
    values.foreach( kv => res=res.replaceAll(kv._1,kv._2) )
    res
  }
}

