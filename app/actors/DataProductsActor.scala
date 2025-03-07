package actors

import org.apache.pekko.actor.{Actor, Props}
import com.github.jferard.fastods.{OdsDocument, OdsFactory}
import controllers.PublicCtrl.{integerDataStyle, rowStyle, titleStyle}
import dataaccess.{SafetyWarrantDAO, SettingDAO, SettingKey, WorkAccidentDAO}
import models.LongRunningProcessStatus.{Done, Started}
import models.{Column, LongRunningProcessMonitor, RichWalker, SafetyWarrant, Severity}
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.libs.Files.TemporaryFileCreator
import play.api.{Configuration, Logger}

import java.nio.file.{Files, Paths, StandardCopyOption}
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.util.Using

object DataProductsActor {
  def props:Props = Props[DataProductsActor]()
  case class PossiblyUpdateWarrantTable()
  case class ForceUpdateWarrantTable()
  case class UpdateTemporalViews()
  case class CreatePeriodicalReport( from:LocalDate, to:LocalDate, monitor:LongRunningProcessMonitor)
  
  import Column._
  val safetyWarrantCols = Seq(
    Column[SafetyWarrant]("id", (v,w)=>printLong(v.id, w)),
    Column[SafetyWarrant]("sent date", (v,w)=>printDate(v.sentDate, w)),
    Column[SafetyWarrant]("operator id", (v,w)=>w.setStringValue(v.operatorTextId)),
    Column[SafetyWarrant]("operator name", (v,w)=>w.setStringValue(v.operatorName)),
    Column[SafetyWarrant]("city name", (v,w)=>w.setStringValue(v.cityName)),
    Column[SafetyWarrant]("executor name", (v,w)=>w.setStringValue(v.executorName)),
    Column[SafetyWarrant]("category name", (v,w)=>w.setStringValue(v.categoryName)),
    Column[SafetyWarrant]("felony", (v,w)=>w.setStringValue(v.felony)),
    Column[SafetyWarrant]("law", (v,w)=>w.setStringValue(v.law)),
    Column[SafetyWarrant]("clause", (v,w)=>w.setStringValue(v.clause)),
    Column[SafetyWarrant]("scrape date", (v,w)=>printDate(v.scrapeDate, w))
  )
  
}


/**
 * Actor for making data products in the background.
 */
class DataProductsActor @Inject() (safetyWarrants:SafetyWarrantDAO,
                                   workAccidents: WorkAccidentDAO,
                                   settings:SettingDAO,
                                   cache:AsyncCacheApi,
                                   messagesApi: MessagesApi,
                                   config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  import DataProductsActor._
  private val D = Duration(5, duration.MINUTES)
  private val log = Logger(classOf[WarrantScrapingActor])
  private val messages = messagesApi.preferred(Seq(Lang("IW"), Lang("EN")))
  
  
  override def receive: Receive = {
    case PossiblyUpdateWarrantTable() => {
      if ( settings.isTrueish(SettingKey.SafetyWarrantProductsNeedUpdate) ) {
        settings.set(SettingKey.SafetyWarrantProductsNeedUpdate, "no")
        log.info("Updating safety warrant ODS")
        updateSafetyWarrantDownloadable()
        log.info("Refreshing materialized views")
        safetyWarrants.refreshViews()
        log.info("Done")
      }
    }
    
    case UpdateTemporalViews() =>
      log.info("Refreshing temporal views")
      safetyWarrants.refreshTemporalViews()
    
    case ForceUpdateWarrantTable() =>
      updateSafetyWarrantDownloadable()
      safetyWarrants.refreshViews()
      sender() ! "OK"
    
    case CreatePeriodicalReport(f,t,m) => composePeriodicalReport(f,t,m)
  }
  
  private def updateSafetyWarrantDownloadable():Unit = {
    
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("DataProductsActor"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    val table = document.addTable("Safety Warrants")
    val walker = table.getWalker
    
    // add title row
    safetyWarrantCols.foreach(c => {
      walker.setStringValue(c.name)
      walker.setStyle(titleStyle)
      walker.next()
    })
    walker.setRowStyle(rowStyle)
    walker.nextRow()
    
    // add rows
    log.info("Writing rows")
    val src = safetyWarrants.listAll()
    val done = src.foreach(sw => {
      safetyWarrantCols.foreach(c => {
        c.write(sw, walker);
        walker.next()
      })
      walker.setRowStyle(rowStyle)
      walker.nextRow()
    })
    Await.result(done, D)
    
    // write a temp file
    log.info("Writing temp file")
    val tempPath = Paths.get(config.get[String]("klo.dataProductFolder")).resolve("safetyWarrants.ods.temp")
    
    Using(Files.newOutputStream(tempPath)) {
      writer.save
    }
    
    // Move to
    log.info("Moving temp file to place")
    Files.move(tempPath, tempPath.resolveSibling("safetyWarrants.ods"),
      StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  
  private def composePeriodicalReport(from: LocalDate, to: LocalDate, anLpm: LongRunningProcessMonitor): Unit = {
    var lpm = anLpm.copy(status = Started)
    cache.set(lpm.id, lpm)
    log.info( s"Composing periodical report ${from}-${to}")
    
    Await.result(safetyWarrants.refreshViews(), D)
    
    val odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("ReportsCtrl"), Locale.US)
    val writer = odsFactory.createWriter
    val document = writer.document()
    
    accidentsByMonthTable(from, to, document)
    casualtiesByIndustryTable(from, to, document)
    casualtiesByYearTable(from.getMonthValue, to.getMonthValue, document)
    casualtiesByYearAndIndustryTable(from.getMonthValue, to.getMonthValue, false, document)
    casualtiesByYearAndIndustryTable(from.getMonthValue, to.getMonthValue, true, document)
    fatalitiesPerCitizenshipTable(from, to, document)
    casualtiesByCausesTable(from, to, false, document)
    casualtiesByCausesTable(from, to, true, document)
    causesByIndustryTable(from, to, document)
    warrantCountsTable(from, to, document)
    warrantCountByYearAndCategory(from.getMonthValue, to.getMonthValue, document)
    commonWarrantBases(from, to, document)
    companiesWithMostWarrants(from, to, document)
    
    System.getProperty("java.io.tmpdir")
    val tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(s"${lpm.id}.ods")
//    fileCreator.create(tempPath) // ensure later deletion by the reaper
    
    Using(Files.newOutputStream(tempPath)){
      writer.save
    }
    log.info(s"File created at: ${tempPath.toAbsolutePath}")
    lpm = lpm.copy(status = Done)
    cache.set(lpm.id, lpm)
    
    log.info( s"Done composing periodical report ${from}-${to}")
  }
  
  private def companiesWithMostWarrants( from:LocalDate, to:LocalDate, document:OdsDocument):Unit = {
    val tbl = document.addTable("Most Warrants")
    val walker = RichWalker(tbl.getWalker)
    walker.th(messages("reports.ods.mostWarrants.title")).nextRow()
    tbl.setCellMerge(0, 0, 1, 2)
    walker.bold
      .td(messages("reports.ods.executor")).td(messages("reports.ods.warrantCount")).nextRow()
      .plain
    Await.result(for {
      rawRows <- safetyWarrants.countsByExecutor(from, to)
      rows = rawRows.map(r => (nulls2unknown(r._1), r._2))
    } yield {
      val cutoff = getCutoff(rows.map(_._2), 10)
      rows.filter(_._2 > cutoff).foreach(r => {
        walker.td(r._1).td(r._2).nextRow()
      })
    }, D)
  }
  
  private def commonWarrantBases( from:LocalDate, to:LocalDate, document:OdsDocument ):Unit = {
    val tbl = document.addTable("Common Warrant Bases")
    val walker = RichWalker(tbl.getWalker)
    walker.th(messages("reports.ods.commonWarrantFelonies.title")).nextRow()
    tbl.setCellMerge(0,0,1,2)
    walker.bold
      .td(messages("safetyWarrants.table.felony")).td(messages("reports.ods.warrantCount")).nextRow()
      .plain
    
    Await.result( for {
      rawRows <- safetyWarrants.mostCommonFelonies(from, to)
      rows = rawRows.map( r => (nulls2unknown(r._1), r._2) )
    } yield {
      val cutoff = getCutoff(rows.map(_._2), 10)
      rows.filter(_._2 > cutoff).foreach( r => {
        walker.td(r._1).td(r._2).nextRow()
      })
    }, D)
    
    walker.nextRow()
    walker.th(messages("reports.ods.commonWarrantLaws.title"))
    tbl.setCellMerge(walker.rowIdx, 0, 1, 2)
    
    walker.nextRow().bold
      .td(messages("safetyWarrants.index.law")).td(messages("reports.ods.warrantCount")).nextRow()
      .plain
    
    Await.result(for {
      rawRows <- safetyWarrants.mostCommonLaws(from, to)
      rows = rawRows.map(r => (nulls2unknown(r._1), r._2))
    } yield {
      val cutoff = getCutoff(rows.map(_._2), 10)
      rows.filter(_._2 > cutoff).foreach(r => {
        walker.td(r._1).td(r._2).nextRow()
      })
    }, D)
  }
  
  private def warrantCountByYearAndCategory(startMonth:Int, endMonth:Int, document:OdsDocument):Unit = {
    val tbl = document.addTable("Warrants by Industry, Year")
    val walker = RichWalker(tbl.getWalker)
    walker.th(
      messages("reports.ods.warrantsByIndustryAndYear.title",
        messages("month."+startMonth), messages("month."+endMonth))
    ).nextRow()
    
    Await.result(for {
      rawRows <- safetyWarrants.warrantCountByCategoryAndYear(startMonth, endMonth)
      rows = rawRows.map( r => (r._1, nulls2unknown(r._2), r._3) )
    } yield {
      val categories = rows.map(_._2).distinct.sorted
      val years = rows.map(_._1).distinct.sorted
      
      tbl.setCellMerge(0,0,1,categories.length+1)
      
      walker.bold
      (messages("year") +: categories).foreach( walker.td )
      walker.nextRow().plain
      
      for ( year <- years ) {
        walker.bold.td(year).plain
        for ( cat <- categories ) {
          walker.td( rows.find( r=>r._1==year && r._2==cat).map(_._3).getOrElse(0) )
        }
        walker.nextRow()
      }
    }, D)
  }
  
  private def warrantCountsTable( from:LocalDate, to:LocalDate, document:OdsDocument ):Unit = {
    val tbl = document.addTable("Warrants by Industry")
    val walker = RichWalker(tbl.getWalker)
    walker.th(messages("reports.ods.warrantsByIndustry.title")).nextRow()
    
    walker.nextRow().plain
    Await.result( for {
      rawRows <- safetyWarrants.warrantCountByMonthAndBranch(from, to)
      rows    = rawRows.map(r => (r._1, r._2, nulls2unknown(r._3), r._4) )
      
    } yield {
      val branches = rows.map(_._3).distinct.sorted
      val dates = rows.map( r => (r._1, r._2) ).sortBy(r=>r._1.toDouble+(r._2/100.0)).distinct
      
      tbl.setCellMerge(0,0,1,branches.length+2)
      
      walker.bold
      Seq("year", "month")
        .map(messages(_))
        .foreach(walker.td)
      branches.foreach( walker.td )
      walker.plain.nextRow()
      
      dates.foreach( d => {
        val rowsForDate = rows.filter( r => r._1==d._1 && r._2==d._2 )
        walker.bold.td(d._1).td(d._2).plain
        branches.foreach( b => {
          walker.td( rowsForDate.find(r=>r._3==b).map(_._4).getOrElse(0) )
        })
        walker.nextRow()
      })
      walker.bold.td(messages("total")).skip().plain
      branches.foreach(b => {
        walker.td(rows.filter(r => r._3 == b).map(_._4).sum)
      })
    }, D)
  }
  
  private def causesByIndustryTable(from: LocalDate, to: LocalDate, document:OdsDocument ): Unit = {
    val tbl = document.addTable("Causes by Industry")
    val walker = RichWalker(tbl.getWalker)
    
    Await.result( workAccidents.getCasualtyCountByCauseFatalityIndustry(from, to).map( rawRows => {
      val rows = rawRows.map( r => (nulls2unknown(r._1), nulls2unknown(r._2), r._3, r._4))
      val causes = rows.map(_._1).distinct.sorted
      val industries = rows.map(_._2).distinct.sorted
      
      walker.th(messages("reports.ods.causesByIndustry.injuries.title")).nextRow().nextRow()
      tbl.setCellMerge(0,0,1,1+industries.length)
      
      walker.bold
      walker.td(messages("injuryCause"))
      industries.foreach(walker.td)
      walker.plain
      walker.nextRow()
      
      val injData = rows.filter( _._3 != true )
      for ( cause <- causes ) {
        walker.bold.td(cause).plain
        for ( ind <- industries ) {
          walker.td( injData.filter(d => d._1==cause&&d._2==ind).map(_._4).sum )
        }
        walker.nextRow()
      }
      
      walker.nextRow()
      walker.th(messages("reports.ods.causesByIndustry.fatalities.title"))
      tbl.setCellMerge(walker.rowIdx, 0, 1, 1+industries.length)
      walker.nextRow().nextRow()
      
      walker.bold
      walker.td(messages("injuryCause"))
      industries.foreach(walker.td)
      walker.plain
      walker.nextRow()
      val ftlData = rows.filter(_._3 == true)
      for (cause <- causes) {
        walker.bold.td(cause).plain
        for (ind <- industries) {
          walker.td(ftlData.filter(d => d._1 == cause && d._2 == ind).map(_._4).sum)
        }
        walker.nextRow()
      }
      
    }),D)
  }
  
  private def fatalitiesPerCitizenshipTable(from: LocalDate, to: LocalDate, document:OdsDocument ): Unit = {
    val tbl = document.addTable("Fatalities per Citizenship")
    val walker = RichWalker(tbl.getWalker)
    
    val title = messages("reports.ods.fatalitiesByCitizenship.title")
    walker.th(title).nextRow()
    walker.nextRow();
    tbl.setCellMerge(0, 0, 1, 6)
    
    walker.bold
      .td(messages("citizenship")).td(messages("reports.ods.sev.fatal"))
      .plain.nextRow()
    
    Await.result(
      workAccidents.getCasualtiesByCitizenship(from,to).map( rows => {
        for ( row <- rows.sortBy(_._2) ) {
          walker.bold.td(Option(row._1).getOrElse(messages("reports.ods.unknown"))).plain.td(row._2).nextRow()
        }
      }), D
    )
  }
  
  private def casualtiesByCausesTable(from:LocalDate, to:LocalDate, isFatal:Boolean, document:OdsDocument):Unit = {
    val tbl = document.addTable( if ( isFatal ) "Fatalities per Cause" else "Injuries per Cause")
    
    val walker = RichWalker(tbl.getWalker)
    val title = messages(if (isFatal) "reports.ods.fatalitiesByCause.title" else "reports.ods.injuriesByCause.title")
    walker.th(title).nextRow().nextRow()
    tbl.setCellMerge(0, 0, 1, 6)
    
    val min = if (isFatal) Severity.fatal else Severity.medium
    val max = if (isFatal) Severity.fatal else Severity.nearFatal
    Await.result(
      workAccidents.getCausesBySeverity(from, to, min, max).map( rows => {
            for ( row <- rows.sortBy(_._2).reverse ) {
              walker.td(Option(row._1).getOrElse(messages("reports.ods.unknown")))
              walker.td(row._2)
              walker.nextRow()
            }
      }),D
    )
    
  }
  
  private def casualtiesByYearAndIndustryTable( startMonth:Int, endMonth:Int, isFatal:Boolean, document:OdsDocument ): Unit = {
    val tbl = document.addTable( if ( isFatal ) "Fatalities per Year, Industry" else "Injured per Year, Industry")
    val walker = RichWalker(tbl.getWalker)
    
    val title = messages(s"reports.ods.${if (isFatal) "killed" else "injured" }ByPeriodAndIndustry.title", messages("month." + startMonth), messages("month." + endMonth))
    walker.th(title).nextRow().nextRow()
    tbl.setCellMerge(0, 0, 1, 6)
    
    val min = if (isFatal) Severity.fatal else Severity.medium
    val max = if (isFatal) Severity.fatal else Severity.nearFatal
    
    Await.result(
      workAccidents.getCasualtiesCountByYearAndIndustry(startMonth, endMonth, min, max).map(rawRows=>{
        val rows = rawRows.map( r => (r._1, if (r._2!=null) r._2 else messages("reports.ods.unknown"), r._3 ))
        val years = rows.map(_._1).toSet.toSeq.sorted
        val inds = rows.map(_._2).toSet.toSeq.sorted
        
        // table head
        walker.th(messages("industry"))
        years.foreach( walker.td )
        walker.nextRow()
        
        // table body
        for ( ind <- inds ) {
          walker.bold.td(ind).plain
          for ( year <- years ) {
            rows.find( r => r._1 == year && r._2 == ind ) match {
              case None => walker.td(0)
              case Some(_,_,count) => walker.td(count)
            }
          }
          walker.nextRow()
        }
        
        // totals line
        walker.bold.td(messages("total")).plain
        for ( year <- years ) {
          val total = rows.filter( _._1 == year ).map(_._3).sum
          walker.td(total)
        }
        
    }), D)
  }
  private def casualtiesByYearTable( startMonth:Int, endMonth:Int, document:OdsDocument ): Unit = {
    val tbl = document.addTable("Casualties by Year")
    val walker = RichWalker(tbl.getWalker)
    
    val title = messages("reports.ods.casualtiesByPeriod.title", messages("month."+startMonth), messages("month."+endMonth))
    walker.th(title).nextRow()
    walker.nextRow();
    tbl.setCellMerge(0,0,1,5)
    
    walker.th(messages("year"))
    walker.th(messages("reports.ods.sev.medium"))
    walker.th(messages("reports.ods.sev.severe"))
    walker.th(messages("reports.ods.sev.nearFatal"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    Await.result(workAccidents.getCasualtiesCountByYear(startMonth, endMonth).map(rows => {
      log.info(s"getCasualtiesCountByYear Got ${rows.length} rows")
      val byYear = rows.groupBy(_._1)
      
      byYear.toSeq.sortBy(_._1).foreach( yearData => {
        walker.bold.td(yearData._1).plain
        Range.inclusive(Severity.medium.id, Severity.fatal.id).foreach( severity => {
          yearData._2.find(_._2 == severity) match {
            case None => walker.td(0)
            case Some(_, _, count) => walker.td(count)
          }
        })
        walker.nextRow()
      })
    }), D)
    
  }
  
  private def casualtiesByIndustryTable(from: LocalDate, to: LocalDate, document:OdsDocument):Unit = {
    val tbl = document.addTable("Casualties by Industry")
    val walker = RichWalker(tbl.getWalker)
    
    walker.th(messages("reports.ods.casualtiesByIndustry.title")).nextRow().nextRow()
    tbl.setCellMerge(0,0,1,3)
    
    walker.th(messages("industry"))
    walker.th(messages("reports.ods.sev.mediumAndUp"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    Await.result(workAccidents.getCasualtiesByIndustry(from, to).map(rows => {
      log.info(s"casualtiesByIndustryTable Got ${rows.length} rows")
      val byInd = rows.groupBy(_._1)
      byInd.foreach( itms => {
        walker.bold.td(Option(itms._1).getOrElse(messages("reports.ods.unknown"))).plain
        itms._2.find(_._2 == false) match {
          case None => walker.td(0)
          case Some(_,_,count) => walker.td(count)
        }
        itms._2.find(_._2 == true) match {
          case None => walker.td(0)
          case Some(_,_,count) => walker.td(count)
        }
        walker.nextRow()
      })
    }), D)
  }
  
  private def accidentsByMonthTable(from: LocalDate, to: LocalDate, document:OdsDocument):Unit = {
    val tbl = document.addTable("Accidents by month")
    
    val walker = RichWalker(tbl.getWalker)
    walker.th(messages("reports.ods.accidentsByMonthTable.title")).nextRow().nextRow()
    tbl.setCellMerge(0,0,1,4)
    
    walker.th(messages("year"))
    walker.th(messages("month"))
    walker.th(messages("reports.ods.sev.mediumAndUp"))
    walker.th(messages("reports.ods.sev.fatal"))
    walker.nextRow()
    
    val injuredSum = new AtomicInteger()
    val killedSum = new AtomicInteger()
    Await.result( workAccidents.getCasualtiesByMonth(from, to).map( rows => {
      log.info( s"accidentsByMonthTable Got ${rows.length} rows")
      rows.foreach( row => {
        walker.td(row._2)
        walker.td(row._3)
        walker.td(row._4)
        walker.td(row._5)
        walker.nextRow()
        injuredSum.addAndGet(row._4)
        killedSum.addAndGet(row._5)
      })
    }), D)
    walker.skip().skip().bold
    walker.td(injuredSum.get())
    walker.td(killedSum.get())
  }
  
  private def getCutoff( valueItr:IterableOnce[Int], topCount:Int):Int = {
    val values = valueItr.iterator.toSet.toSeq.sorted.reverse
    if (values.length > topCount ) values(topCount) else 0
  }
  private def nulls2unknown(s:String ):String = if (s==null || s.isBlank) messages("reports.ods.unknown") else s
}
