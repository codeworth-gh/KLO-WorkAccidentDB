package dataaccess

import models.{ExecutorCountRow, SafetyWarrant}
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
  val log = Logger(classOf[SafetyWarrant])
  val safetyWarrantTbl = TableQuery[SafetyWarrantsTable]
  val rawSafetyWarrantTbl = TableQuery[RawSafetyWarrantsTable]
  val swWorst20 = TableQuery[SWWorst20Table]
  
  val mutedCategories = config.get[Seq[String]]("scraper.safety.mutedCategories").toSet
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
  
  def countUnboundedBizEnts:Future[Int] = db.run(
    safetyWarrantTbl.filter( r => r.kloOperatorId.isEmpty || r.kloExecutorId.isEmpty ).length.result
  )
  
  def listWarrants(skip:Int, fetchSize:Int, searchStr:Option[String], startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ):Future[Seq[SafetyWarrant]] = {
    db.run(
      filterWarrants(searchStr, startDate, endDate, industryId)
        .drop(skip).take(fetchSize).sortBy(_.sentDate.desc).result
    )
  }
  
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
  
  def refreshViews:Future[Unit] = db.run(
    sql"""
        |REFRESH MATERIALIZED VIEW safety_warrants_per_executor;
        |REFRESH MATERIALIZED VIEW safety_warrants_per_executor_per_year;
        |REFRESH MATERIALIZED VIEW safety_warrant_over_10_after_2018;
        |REFRESH MATERIALIZED VIEW safety_warrants_top_20_executors;
         """.stripMargin.as[(Int)]
  ).map(_=>())
  
  def worst20ExecutorsAllTime():Future[Seq[ExecutorCountRow]] = db.run( swWorst20.result )
  
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
