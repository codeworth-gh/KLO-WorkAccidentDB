package dataaccess

import models.SafetyWarrant
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.DatabasePublisher
import slick.jdbc.{JdbcProfile, ResultSetConcurrency, ResultSetType}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SafetyWarrantDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                  industries: IndustriesDAO
                                 )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  
  val safetyWarrantTbl = TableQuery[SafetyWarrantsTable]
  
  /**
   * True iff a warrant with that id exists in the DB
   */
  def exists( id:Long ):Future[Boolean] = db.run(
    safetyWarrantTbl.filter(_.id === id).exists.result
  )
  
  def store( wnt: SafetyWarrant ):Future[Try[SafetyWarrant]] = db.run(
    safetyWarrantTbl.insertOrUpdate(wnt).asTry
  ).map( r => r.map(_ => wnt) )
  
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
  
  def listWarrants(skip:Int, fetchSize:Int, startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ):Future[Seq[SafetyWarrant]] = {
    val tq = filterWarrants(startDate, endDate, industryId).take(fetchSize)
    db.run( (if (skip==0) tq else tq.drop(skip)).sortBy(_.sentDate.desc).result )
  }
  
  def listAll():DatabasePublisher[SafetyWarrant] = db.stream(
    safetyWarrantTbl
      .sortBy( _.id )
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 1000)
      .transactionally)
  
  def countWarrants(startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ):Future[Int] =
    db.run( filterWarrants(startDate, endDate, industryId).length.result )
  
  private def filterWarrants(startDate:Option[LocalDate], endDate:Option[LocalDate], industryId:Option[Int] ) = {
    var q: Query[SafetyWarrantsTable, SafetyWarrant, Seq] = safetyWarrantTbl
    startDate.foreach( d => q=q.filter(r=>r.sentDate>=d) )
    endDate.foreach( d => q=q.filter(r=>r.sentDate<=d) )
    industryId.foreach( d => q=q.filter(r=>r.kloIndustryId.inSet(Set(d))) )
    q
  }
  
}
