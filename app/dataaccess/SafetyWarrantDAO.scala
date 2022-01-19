package dataaccess

import models.{CountByCategoryAndYear, ExecutorCountRow, SafetyWarrant}
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.DatabasePublisher
import slick.dbio.DBIOAction
import slick.jdbc.{JdbcProfile, ResultSetConcurrency, ResultSetType}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SafetyWarrantDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                  industries: IndustriesDAO, config:Configuration
                                 )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  private val log = Logger(classOf[SafetyWarrant])
  private val safetyWarrantTbl = TableQuery[SafetyWarrantsTable]
  private val rawSafetyWarrantTbl = TableQuery[RawSafetyWarrantsTable]
  private val swWorst20 = TableQuery[SWWorst20Table]
  private val swPerExecutor = TableQuery[SWPerExecutor]
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
  
  def listWarrants(skip:Int, fetchSize:Int, searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ):Future[Seq[SafetyWarrant]] = {
    db.run(
      filterWarrants(searchStr, startDate, endDate, industryId)
        .drop(skip).take(fetchSize).sortBy(_.sentDate.desc).result
    )
  }
  
  def executorsOver4In24( skip:Int, fetchSize:Int ):Future[Seq[(String, Int)]] = db.run(
    executorsWithOver4In24.sortBy(r=>(r.count.desc, r.name.asc)).drop(skip).take(fetchSize).result
  )
  
  def executorsOver4In24Count():Future[Int] = db.run(
    executorsWithOver4In24.size.result
  )
  
  def warrantCountByCategoryAll():Future[Map[String, Int]] = db.run( swByCategoryAll.result ).map( _.toMap )
  def warrantCountByCategory24Mo():Future[Map[String, Int]] = db.run( swByCategory24mo.result ).map( _.toMap )
  def warrantCountByLaw(fetchSize:Int):Future[Seq[(String,Int)]] = db.run( swByLawAll.sortBy(_.count.desc).take(fetchSize).result )
  def warrantCountByCategoryAndYear():Future[Seq[CountByCategoryAndYear]] = db.run( swByCategoryAndYear.result )
  
  def countWarrants(searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ):Future[Int] =
    db.run( filterWarrants(searchStr, startDate, endDate, industryId).length.result )
  
  def count():Future[Int] = db.run(safetyWarrantTbl.size.result)
  
  def listAll():DatabasePublisher[SafetyWarrant] = db.stream(
    safetyWarrantTbl
      .sortBy( _.id )
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 1000)
      .transactionally)
  
  def refreshViews():Future[Unit] = db.run(
    sql"""
        |REFRESH MATERIALIZED VIEW safety_warrants_per_executor;
        |REFRESH MATERIALIZED VIEW safety_warrants_per_executor_per_year;
        |REFRESH MATERIALIZED VIEW safety_warrant_over_10_after_2018;
        |REFRESH MATERIALIZED VIEW safety_warrants_top_20_executors;
        |REFRESH MATERIALIZED VIEW executors_with_4_plus_24mo;
        |REFRESH MATERIALIZED VIEW safety_warrant_by_category_24mo;
        |REFRESH MATERIALIZED VIEW safety_warrant_by_category_all;
        |REFRESH MATERIALIZED VIEW safety_warrant_by_law;
        |REFRESH MATERIALIZED VIEW safety_warrant_by_category_and_year;
         """.stripMargin.as[Int]
  ).map(_=>())
  
  
  def refreshTemporalViews():Future[Unit] = db.run(
    sql"""
         |REFRESH MATERIALIZED VIEW executors_with_4_plus_24mo;
         """.stripMargin.as[Int]
  ).map(_=>())
  
  def worst20ExecutorsAllTime():Future[Seq[ExecutorCountRow]] = db.run( swWorst20.result )
  def worst20ExecutorsAllTime(fetchSize:Int):Future[Seq[ExecutorCountRow]] = db.run( swWorst20.sortBy(_.count.desc).take(fetchSize).result )
  
  def getForExecutor(execName:String):Future[Seq[SafetyWarrant]] = db.run(
    safetyWarrantTbl.filter(_.executorName===execName).sortBy(_.sentDate.desc).result
  )
  
  private def filterWarrants(searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ) = {
    var q: Query[SafetyWarrantsTable, SafetyWarrant, Seq] = safetyWarrantTbl
    startDate.foreach( d => q=q.filter(r=>r.sentDate>=d) )
    endDate.foreach( d => q=q.filter(r=>r.sentDate<=d) )
    industryId.foreach( d => q=q.filter(r=>r.kloIndustryId.inSet(Set(d))) )
    searchStr.foreach( s => q=q.filter(r=>r.executorName.like(s"%$s%") || r.cityName.like(s"%$s%")
                                          || r.operatorName.like(s"%$s%") || r.felony.like(s"%$s%")) )
    q
  }
  
}
