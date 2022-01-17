package actors

import actors.WarrantScrapingActor.{StartScrapingSafety, ldtFmt}
import akka.actor.{Actor, Props}
import controllers.Assets
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey}
import models.SafetyWarrant
import play.api.libs.json.{JsArray, JsDefined, JsObject, JsString, JsUndefined, JsValue}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import scala.concurrent.duration.Duration
import scala.io.Source

object WarrantScrapingActor {
  def props: Props = Props[WarrantScrapingActor]()
  
  val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  val ldtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  case class StartScrapingSafety( skip:Int )
}

@Singleton
class WarrantScrapingActor @Inject() (safetyWarrants:SafetyWarrantDAO, settings:SettingDAO, ws:WSClient, assets:Assets,
                                      config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  private val log = Logger(classOf[WarrantScrapingActor])
  private val D = Duration(5, duration.MINUTES)
  
  
  override def receive: Receive = {
    case StartScrapingSafety( skip ) => {
      if ( config.get[Boolean]("scraper.safety.active") ) {
        scrapeSafety( skip )
      } else {
        log.info( s"Ignoring call to scrape from $skip - actor deactivated (scraper.safety.active != true)")
      }
    }
  }

  def scrapeSafety(skip: Int):Unit = {
    log.info(s"Safety warrant scraping with skip: $skip")
    
    // prepare call
    val payloadSrc = Source.fromResource("public/warrant-safety-payload.json").getLines().mkString("\n")
    val payload = eval( skip, payloadSrc )
    val url = eval( skip, config.get[String]("scraper.safety.url") )
    
    log.info("payload: " + payload)
    
    val request = ws.url(url).withHttpHeaders("Content-Type"->"application/json")
      .withHttpHeaders("Accept"->"application/json")
      .withHttpHeaders("User-Agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:95.0) Gecko/20100101 Firefox/95.0")
      .withFollowRedirects(true)
      .addQueryStringParameters("skip"->eval(skip, "SKIP"))
    
    log.info("URL: " + request.uri.toString)
    
    log.info("Submitting request")
    val timestamp = LocalDateTime.now()
    val res = Await.result( request.post(payload), D )
    log.info("Got response")
    
    // parse
    try {
      val json = res.json.asInstanceOf[JsObject]
      val records = parse(json, timestamp)
  
      // See if we have new records
      val newRecs = records.filter(r => !Await.result(safetyWarrants.exists(r.id), D))
      
      // TODO apply mappings
  
      if (newRecs.nonEmpty) {
        settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "pending")
        val fw = newRecs.map(r => safetyWarrants.store(r))
        Await.result(Future.sequence(fw), D) // wait until all entries are done
        log.info(s"${newRecs.size} new records found, requesting another scrape")
        
        // skip +20 if new found
        val newSkip = skip + config.get[Int]("scraper.safety.skipDelta")
        delay()
        self ! StartScrapingSafety(newSkip)
    
      } else {
        if ( settings.get(SettingKey.SafetyWarrantProductsNeedUpdate).contains("pending") ) {
          settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "yes")
        }
        log.info("No new records found, scraping done")
      }
      settings.set(SettingKey.LastSafetyWarrantScrapeTime, ldtFmt.format(LocalDateTime.now()))
      
    } catch {
      case e:Exception => {
        log.warn("Error scraping safety warrants: " + e.getMessage, e)
        log.info( "=== Result body ===")
        log.info( res.body )
        log.info( "=== /Result body ===")
      }
    }
  }
  
  private def parse(jsRes:JsValue, timestamp:LocalDateTime):Seq[SafetyWarrant] = {
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

