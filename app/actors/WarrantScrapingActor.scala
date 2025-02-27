package actors

import actors.WarrantScrapingActor.ldtFmt
import com.opencsv.{CSVReaderBuilder, CSVReaderHeaderAware, CSVReaderHeaderAwareBuilder}
import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import controllers.Assets
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey}
import models.ImportStatus.{Done, Started}
import models.{ImportMonitor, ImportStatus, SafetyWarrant}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsArray, JsBoolean, JsDefined, JsNull, JsNumber, JsObject, JsString, JsUndefined, JsValue}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import java.nio.file.{Files, OpenOption, Path}
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import scala.concurrent.duration.Duration
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

object WarrantScrapingActor {
  def props: Props = Props[WarrantScrapingActor]()
  
  private val dateFmt_old: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  val ldtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  case object StartScrape
  case class ScrapeRecords( endpoint:String, preScrapeCount:Int )
  case class ScrapeYear(year:Int)
  case class ImportFile( monitor:ImportMonitor, path:java.nio.file.Path )
}

@Singleton
class WarrantScrapingActor @Inject() (safetyWarrants:SafetyWarrantDAO, settings:SettingDAO, ws:WSClient,
                                      actorSystem:ActorSystem, cache: AsyncCacheApi,
                                      config:Configuration)(implicit anEc:ExecutionContext) extends Actor with JsonScraper {
  private val log = Logger(classOf[WarrantScrapingActor])
  private val D = Duration(5, duration.MINUTES)
  import WarrantScrapingActor._
  private val mutedCategories = config.get[Seq[String]]("scraper.safety.mutedCategories").toSet
  private var scrapingYear:Option[Int]=None
  
  override def receive: Receive = {
    // NOTE: Sorting by date does not work as the database field on gov.il's side acts like a text here, not a date(?)
    case StartScrape =>
      scrapingYear = None
      scrape(
        config.get[String]("scraper.safety.endpoint") + "&limit=" + config.get[String]("scraper.safety.limit"), // + "&sort=send_date desc",
        10
      )
    case ScrapeRecords( url, psCount) => scrape(url, psCount)
    case ScrapeYear(year) =>
      scrapingYear = Some(year)
      scrape(
        config.get[String]("scraper.safety.endpoint") + "&limit=" + config.get[String]("scraper.safety.limit"), // + "&sort=send_date desc",
        10
      )
    case ImportFile( monitor, path ) => importCsvFile( monitor, path )
  }

  def scrape(endpoint:String, preScrapeCount:Int ):Unit  = {
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
      parse(result.json.asInstanceOf[JsObject], preScrapeCount ) match {
        case Some( (nextUrl, psc) ) =>
          val minSeconds = config.get[Int]("scraper.safety.minDelay")
          val maxSeconds = config.get[Int]("scraper.safety.maxDelay")
          val seconds = minSeconds + util.Random.nextInt(maxSeconds-minSeconds)
          log.info(s"Scheduling next warrants scrape in $seconds sec.")
          actorSystem.scheduler.scheduleOnce(Duration( seconds, TimeUnit.SECONDS), self, ScrapeRecords(nextUrl, psc))
        
        case None =>
          log.info(s"Scraping safety warrants done for today")
        
      }
    } catch {
      case e:Exception => {
        log.warn(s"Error parsing warrants response: ${e.getMessage}", e)
        log.warn("Response:\n" + result.toString)
        log.warn("Response Body:\n" + result.body)
      }
    }
    settings.set(SettingKey.LastSafetyWarrantScrapeTime, WarrantScrapingActor.ldtFmt.format(LocalDateTime.now()))
  }
  
  def parse( res:JsObject, preScrapeCount:Int ):Option[(String, Int)] = {
    // check it's all ok
    
    val success = (res \ "success").get.as[JsBoolean].value
    if ( ! success ) {
      log.warn(s"Error scraping safety warrants: /success != true")
      log.warn(s"Response JSON body: \n${res}")
      return None
    }
    
    // parse and store actual records
    val records = (res \ "result" \ "records").as[JsArray]
    val recResults = records.value
      .filter( r => r.isInstanceOf[JsObject]).map(r => r.asInstanceOf[JsObject])
      .map( r => parseWarrantRec(r) ).filter( _.isSuccess ).map(_.get)
    
    val minDate = recResults.map(_.sentDate).minOption
    val maxDate = recResults.map(_.sentDate).maxOption
    log.info(s"Scraped range this batch: $minDate - $maxDate")
    
    val foundExisting = records.value.map( storeSingleRecord ).fold(false)(_||_)
    
    // if some records where new, return Some("_links/next") else return None.
    val nextLink = (res \ "result" \ "_links"  \ "next").toOption
    nextLink match {
      case None => None
      case Some(url) =>
        scrapingYear match {
          case None => {
            if (foundExisting) {
              if (preScrapeCount == 0) {
                None
              } else {
                Some(url.asInstanceOf[JsString].value, preScrapeCount - 1)
              }
            } else {
              Some(url.asInstanceOf[JsString].value, preScrapeCount)
            }
          }
          case Some(minYear) => {
            minDate.map(_.getYear) match {
              case None => None
              case Some(aYear) =>
                if ( aYear < minYear ) None
                else Some(url.asInstanceOf[JsString].value, preScrapeCount)
            }
          }
        }
    }
  }
  
  def importCsvFile(monitor: ImportMonitor, path: Path):Unit = {
    log.info( s"Started import of ${path.toAbsolutePath.normalize()}");
    var myMon = monitor.copy(status = Started)
    cache.set(monitor.id, myMon);
    
    // validate headers
    val expectedHeaders = Set(
      "send_date", "warrent_id", "work_id",
      "work_name", "city_name", "executor_name",
      "category_name", "felony_name", "law_name", "clause_name")
    
    val headRdr = Files.newBufferedReader(path)
    val headerLine = headRdr.readLine()
    headRdr.close()
    val headerSeq = headerLine.split(",").map( _.trim.toLowerCase ).filter(_.nonEmpty)
      .map( _.filter( c => c>='_' && c<='z' ) ).toSeq // Removing BOM etc.
    
    if ( ! expectedHeaders.subsetOf(headerSeq.toSet) ) {
      log.warn(s"Wrong headers. Actual:\n" + headerSeq.sorted + "\nExpected:\n" + expectedHeaders.toSeq.sorted)
      var missingHeaders = expectedHeaders -- headerSeq.toSet
      log.warn(s"Missing headers:\n" + missingHeaders.toSeq.sorted)
      
      myMon = myMon.copy( status=ImportStatus.Error, message=Some("Missing headers. This might be a wrong file or the government format has changed."))
      cache.set(myMon.id, myMon);
      return
      
    } else {
      log.info("Headers OK")
    }
    
    // import records
    val headerIdx = headerSeq.zipWithIndex.map( p => p._1->p._2 ).toMap
    Using( new CSVReaderBuilder(Files.newBufferedReader(path)).build ){ rdr =>
      rdr.readNext() // skip headers
      var curRow:Array[String]=rdr.readNext()
      var go = true
      var count = 0
      val importComment = s"Imported from ${myMon.originalFilename}, ${WarrantScrapingActor.ldtFmt.format(LocalDateTime.now())}"
      while( go ) {
        try {
          parseWarrant(curRow, headerIdx, importComment) match {
            case Failure(e) =>
              log.warn(e.getMessage, e)
              myMon = myMon.copy(errorCount = myMon.errorCount + 1)
            case Success(warrant) =>
              if (mutedCategories(warrant.categoryName)) {
                myMon = myMon.copy(ignored = myMon.ignored + 1)
              } else {
                if (Await.result(safetyWarrants.exists(warrant.id), D)) {
                  myMon = myMon.copy(existed = myMon.existed + 1)
                  
                } else {
                  Await.result(safetyWarrants.store(warrant), D)
                  myMon = myMon.copy(added = myMon.added + 1)
                }
              }
          }
        } catch {
          case exception: Exception =>
            myMon = myMon.copy(errorCount = myMon.errorCount + 1)
            log.warn(s"Error parsing row ${curRow}: ${exception.getMessage}", exception)
        }
        
        curRow = rdr.readNext()
        go = (curRow != null)
        count = count+1
        if ( count%100 == 0 ) cache.set(myMon.id, myMon)
      }
      if ( myMon.added > 0 ) {
        settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "yes")
      }
      myMon = myMon.copy(status=Done)
      cache.set(myMon.id, myMon)
      settings.set(SettingKey.LastSafetyWarrantScrapeTime, WarrantScrapingActor.ldtFmt.format(LocalDateTime.now()))
    }
  }
  
  private def parseWarrant(row: Array[String], cols: Map[String, Int], importComment:String): Try[SafetyWarrant] = {
    try {
      val scrapeDate = LocalDateTime.now()
      
      Success(SafetyWarrant(
        id = row(cols("warrent_id")).toLong,
        sentDate = safeParseDate(row(cols("send_date"))).getOrElse(LocalDate.of(1970, 1, 1)),
        operatorTextId = row(cols("work_id")),
        operatorName = row(cols("work_name")),
        cityName = row(cols("city_name")),
        executorName = row(cols("executor_name")),
        categoryName = row(cols("category_name")),
        felony = row(cols("felony_name")),
        law = row(cols("law_name")),
        clause = row(cols("clause_name")),
        scrapeDate = scrapeDate,
        None, None, None,
        importComment
      ))
    } catch {
      case e: Exception =>
        log.warn(s"Error parsing SafetyWarrant line: ${e.getMessage}")
        log.warn(row.mkString(", "))
        Failure(e)
    }
  }
  
  /**
   * Parses and possibly stores a single record. Parsing failures will be logged.
   * @param jsVal value to be parsed
   * @return `true` if the record already existed, `false` otherwise.
   */
  private def storeSingleRecord(jsVal:JsValue ):Boolean = {
    try {
      val jsonRec = jsVal.asInstanceOf[JsObject]
      parseWarrantRec(jsonRec) match {
        case Failure(e) =>
          log.warn(s"Failure parsing safety warrant: ${e.getMessage}", e)
          false
        case Success( warrant ) =>
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
    } catch {
      case e:Exception =>
        log.warn(s"Exception while converting a safety warrant JsValue to JsObject: ${e.getMessage}", e)
        return false
    }
  }
  
  private def parseWarrantRec(rec:JsObject ):Try[SafetyWarrant] = {
    val warrantIdKey = if (rec.keys("warrant_id")) "warrant_id" else "warrent_id"
    try {
      val scrapeDate = LocalDateTime.now()
      Success(SafetyWarrant(
        id             = safeExtractLong(rec, warrantIdKey).get,
        sentDate       = safeExtractDate(rec, "send_date").getOrElse(LocalDate.of(1970,1,1)),
        operatorTextId = safeExtractStr(rec,  "work_id").getOrElse(""),
        operatorName   = safeExtractStr(rec,  "work_name").getOrElse(""),
        cityName       = safeExtractStr(rec,  "city_name").getOrElse(""),
        executorName   = safeExtractStr(rec,  "executor_name").getOrElse(""),
        categoryName   = safeExtractStr(rec,  "category_name").getOrElse(""),
        felony         = safeExtractStr(rec,  "felony_name").getOrElse(""),
        law            = safeExtractStr(rec,  "law_name").getOrElse(""),
        clause         = safeExtractStr(rec,  "clause_name").getOrElse(""),
        scrapeDate     = scrapeDate,
        None, None, None,
        s"Scraped ${WarrantScrapingActor.ldtFmt.format(scrapeDate)}"
      ))
    } catch {
      case e:Exception =>
        log.warn(s"Error extracting SafetyWarrant object: ${e.getMessage}")
        log.warn(rec.toString)
        Failure(e)
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
    import WarrantScrapingActor.dateFmt_old
    val dataObj = (jsv.asInstanceOf[JsObject] \ "Data").get.asInstanceOf[JsObject]
    try {
      Some(SafetyWarrant(
        //            typo is in JSON schema
        (dataObj \ "warrent_id").get.as[JsString].value.toInt,
        LocalDate.parse( (dataObj \ "send_date").get.as[JsString].value, dateFmt_old ),
        (dataObj \ "work_id").get.as[JsString].value,
        (dataObj \ "work_name").get.as[JsString].value,
        (dataObj \ "city_name").get.as[JsString].value,
        (dataObj \ "executor_name").get.as[JsString].value,
        (dataObj \ "category_name").get.as[JsString].value,
        (dataObj \ "felony_name").get.as[JsString].value,
        (dataObj \ "law_name").get.as[JsString].value,
        (dataObj \ "clause_name").get.as[JsString].value,
        timestamp,
        None, None, None,
        s"Scraped ${WarrantScrapingActor.ldtFmt.format(timestamp)}"
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

