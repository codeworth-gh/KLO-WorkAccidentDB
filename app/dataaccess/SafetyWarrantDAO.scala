package dataaccess

import controllers.PublicCtrl
import models.{CountByCategoryAndYear, ExecutorCountPerYearRow, ExecutorCountRow, SafetyWarrant}
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.DatabasePublisher
import slick.dbio.DBIOAction
import slick.jdbc.{JdbcProfile, ResultSetConcurrency, ResultSetType}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SafetyWarrantDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                  industries: IndustriesDAO, config:Configuration, cache:AsyncCacheApi
                                 )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  private val log = Logger(classOf[SafetyWarrantDAO])
  private val safetyWarrantTbl = TableQuery[SafetyWarrantsTable]
  private val rawSafetyWarrantTbl = TableQuery[RawSafetyWarrantsTable]
  private val swWorst20 = TableQuery[SWWorst20Table]
  private val swPerExecutor = TableQuery[SWPerExecutor]
  private val swPerExecutorPerYear = TableQuery[SWPerExecutorPerYear]
  private val swByCategoryAll = TableQuery[SafetyWarrantByCategoryAll]
  private val swByCategory24mo = TableQuery[SafetyWarrantByCategory24Mo]
  private val swByLawAll = TableQuery[SafetyWarrantByLaw]
  private val swByCategoryAndYear = TableQuery[SWPerCategoryPerYear]
  private val executorsWithOver4In24 = TableQuery[ExecutorsWithOver4In24]
  
  private val mutedCategories = config.get[Seq[String]]("scraper.safety.mutedCategories").toSet
  log.info(s"Muted categories: $mutedCategories")
  
  /**
   * True iff a warrant with that id exists in the DB
   */
  def exists( id:Long ):Future[Boolean] = db.run(
    rawSafetyWarrantTbl.filter(_.id === id).exists.result
  )
  
  def store( wnt: SafetyWarrant ):Future[Try[SafetyWarrant]] = {
    val plan = for {
      rw <- rawSafetyWarrantTbl.insertOrUpdate(wnt).asTry
      sw <- if (mutedCategories(wnt.categoryName)) {
        DBIO.successful(wnt)
      } else {
        safetyWarrantTbl.insertOrUpdate(wnt)
      }
    } yield rw
    
    db.run( plan ).map( r => r.map(_ => wnt) )
  }
  
  def get( id:Long ):Future[Option[SafetyWarrant]] = db.run(
    safetyWarrantTbl.filter(_.id === id ).result.headOption
  )
  
  def getForBizEnt( bizEntId:Long ):Future[Seq[SafetyWarrant]] = {
    val bes = Set(bizEntId)
    db.run(
      safetyWarrantTbl.filter( r => r.kloOperatorId.inSet(bes) || r.kloExecutorId.inSet(bes) )
        .sortBy( _.sentDate.desc )
        .result
    )
  }
  
  def batchUpdateBizEnts( from:Long, into:Long ):Future[Unit] = db.run(
    DBIO.seq(
      safetyWarrantTbl.filter( _.kloExecutorId===from).map(_.kloExecutorId).update(Some(into)),
      safetyWarrantTbl.filter( _.kloOperatorId===from).map(_.kloOperatorId).update(Some(into))
    )
  )
  
  /**
   * List of warrants that are not yet bound to business entities.
   * @param skip
   * @param fetchSize
   * @return
   */
  def getUnboundBizEnts(skip:Int, fetchSize:Int):Future[Seq[SafetyWarrant]] = {
    var qry = safetyWarrantTbl.filter( r => r.kloOperatorId.isEmpty || r.kloExecutorId.isEmpty )
      .take(fetchSize)
    if ( skip>0 ) {
      qry = qry.drop(skip)
    }
    db.run(  qry.result )
  }
  
  def unknownExecutorCount():Future[Int] = db.run(
    swPerExecutor.filter( r => r.execName === "" ).map(_.count).result.headOption
  ).map( _.getOrElse(0) )
  
  def listWarrants(skip:Int, fetchSize:Int, searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], executorName:Option[String] ):Future[Seq[SafetyWarrant]] = {
    db.run(
      filterWarrants(searchStr, startDate, endDate, executorName)
        .sortBy(_.sentDate.desc.nullsLast).drop(skip).take(fetchSize).result
    )
  }
  
  def countWarrants(searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], executorName:Option[String] ):Future[Int] =
    db.run( filterWarrants(searchStr, startDate, endDate, executorName).length.result )
  
  def executorsOver4In24( skip:Int, fetchSize:Int ):Future[Seq[(String, Int)]] = db.run(
    executorsWithOver4In24.sortBy(r=>(r.count.desc, r.name.asc)).drop(skip).take(fetchSize).result
  )
  
  def executorsOver4In24Count():Future[Int] = db.run(
    executorsWithOver4In24.size.result
  )
  
  def countDatelessWarrants(): Future[Int] = db.run(
    safetyWarrantTbl.filter(_.sentDate.isEmpty ).size.result
  )
  
  private val LOCAL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  def warrantDates():Future[(LocalDate,LocalDate)] = db.run(
    sql"select to_char(min(sent_date), 'yyyy-mm-dd'), to_char(max(sent_date), 'yyyy-mm-dd') from safety_warrants".as[(String, String)]
  ).map( s =>
    ( LocalDate.parse(s(0)._1, LOCAL_DATE_FMT), LocalDate.parse(s(0)._2, LOCAL_DATE_FMT) )
  )
  
  def warrantCountByCategoryAll():Future[Map[String, Int]] = db.run( swByCategoryAll.result ).map( _.toMap )
  def warrantCountByCategory24Mo():Future[Map[String, Int]] = db.run( swByCategory24mo.result ).map( _.toMap )
  def warrantCountByLaw(fetchSize:Int):Future[Seq[(String,Int)]] = db.run( swByLawAll.sortBy(_.count.desc).take(fetchSize).result )
  def warrantCountByCategoryAndYear():Future[Seq[CountByCategoryAndYear]] = db.run( swByCategoryAndYear.result )
  
  def getExecutorYearlyCounts( execName:String ):Future[Seq[ExecutorCountPerYearRow]] = for {
    rows <- db.run(swPerExecutorPerYear.filter( _.execName === execName ).sortBy( _.year.asc ).result)
  } yield {
    val years = rows.flatMap(_.year).toSet
    if ( years.isEmpty ) {
      Seq()
    } else if ( years.max - years.min == years.size-1 ) {
      rows // all years are present
    } else {
      val rowsWithYear = rows.filter( _.year.isDefined )
      Range.inclusive(years.min, years.max)
        .map( year => rows.find( _.year.contains(year) ).getOrElse(ExecutorCountPerYearRow(execName,Some(year),0)))
    }
  }
  
  def warrantCountByMonthAndBranch( from:LocalDate, to:LocalDate ):Future[Seq[(Int, Int, String, Int)]] = {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startStr = fmt.format(from)
    val endStr = fmt.format(to)
    db.run(
      sql"""select date_part('year', sent_date) as year, date_part('month', sent_date) as month, category_name, count(*)
          from safety_warrants
          where sent_date >= '#${startStr}' AND sent_date <= '#${endStr}'
          group by date_part('year', sent_date), date_part('month', sent_date), category_name;
         """.as[(Int, Int, String, Int)]
    )
  }
  
  def warrantCountByCategoryAndYear( fromMonth:Int, toMonth:Int ):Future[Seq[(Int, String, Int)]] = db.run(
    sql"""select date_part('year', sent_date) as year, category_name, count(*)
         from safety_warrants
         where date_part('month', sent_date) >= #$fromMonth and date_part('month', sent_date) <= #$toMonth
         group by date_part('year', sent_date), category_name;
         """.as[(Int, String, Int)]
  )
  
  def mostCommonFelonies(from:LocalDate, to:LocalDate):Future[Seq[(String, Int)]] = {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startStr = fmt.format(from)
    val endStr = fmt.format(to)
    db.run(
      sql"""select felony, count(*) as count
           from safety_warrants
           where sent_date >= '#$startStr' and sent_date <= '#$endStr'
           group by felony
           order by count desc
           limit 25""".as[(String, Int)]
    )
  }
  
  def mostCommonLaws(from:LocalDate, to:LocalDate):Future[Seq[(String, Int)]] = {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startStr = fmt.format(from)
    val endStr = fmt.format(to)
    db.run(
      sql"""select law, count(*) as count
             from safety_warrants
             where sent_date >= '#$startStr' and sent_date <= '#$endStr'
             group by law
             order by count desc
             limit 25""".as[(String, Int)]
    )
  }
  
  def count():Future[Int] = db.run(safetyWarrantTbl.size.result)
  
  def listAll():DatabasePublisher[SafetyWarrant] = db.stream(
    safetyWarrantTbl
      .sortBy( _.id )
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 512)
      .transactionally)
  
  def refreshViews():Future[Unit] = {
    db.run(
      sql"""
           REFRESH MATERIALIZED VIEW safety_warrants_per_executor;
           REFRESH MATERIALIZED VIEW safety_warrants_per_executor_per_year;
           REFRESH MATERIALIZED VIEW safety_warrant_over_10_after_2018;
           REFRESH MATERIALIZED VIEW safety_warrants_top_20_executors;
           REFRESH MATERIALIZED VIEW executors_with_4_plus_24mo;
           REFRESH MATERIALIZED VIEW safety_warrant_by_category_24mo;
           REFRESH MATERIALIZED VIEW safety_warrant_by_category_all;
           REFRESH MATERIALIZED VIEW safety_warrant_by_law;
           REFRESH MATERIALIZED VIEW safety_warrant_by_category_and_year;
         """.as[Int]
    ).map(_=>{
      cache.remove(PublicCtrl.SW_INDEX_PAGE_CACHE_KEY)
      ()
    })
  }
  
  
  def refreshTemporalViews():Future[Unit] = db.run(
    sql"REFRESH MATERIALIZED VIEW executors_with_4_plus_24mo;".as[Int]
  ).map(_=>{
    cache.remove(PublicCtrl.SW_INDEX_PAGE_CACHE_KEY)
    ()})
  
  def worst20ExecutorsAllTime():Future[Seq[ExecutorCountRow]] = db.run( swWorst20.result )
  def worst20ExecutorsAllTime(fetchSize:Int):Future[Seq[ExecutorCountRow]] = db.run( swWorst20.sortBy(_.count.desc).take(fetchSize).result )
  
  def getForExecutor(execName:String):Future[Seq[SafetyWarrant]] = db.run(
    safetyWarrantTbl.filter(_.executorName===execName).sortBy(_.sentDate.desc).result
  )
  
  def countsByExecutor(from:LocalDate, to:LocalDate):Future[Seq[(String, Int)]] = {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val startStr = fmt.format(from)
    val endStr = fmt.format(to)
    db.run(
      sql"""SELECT sw.executor_name AS name, count(*) AS warrant_count
            FROM safety_warrants sw
            WHERE sw.sent_date >= '#$startStr' and sw.sent_date <='#$endStr'
            GROUP BY sw.executor_name
            HAVING count(*) >= 3
            ORDER BY warrant_count DESC;""".as[(String, Int)]
    )
  }
  
  private def filterWarrants(searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], executorName:Option[String] ) = {
    var q: Query[SafetyWarrantsTable, SafetyWarrant, Seq] = safetyWarrantTbl
    startDate.foreach( d => q=q.filter(r=>r.sentDate>=d) )
    endDate.foreach( d => q=q.filter(r=>r.sentDate<=d) )
    executorName.foreach( e => q=q.filter(r=>r.executorName.like(s"%$e%")))
    searchStr.foreach( s => q=q.filter(r=>r.executorName.like(s"%$s%") || r.cityName.like(s"%$s%")
                                          || r.operatorName.like(s"%$s%") || r.felony.like(s"%$s%")
                                          || r.clause.like(s"%$s%") || r.law.like(s"%$s%") ))
    q
  }
  
}
