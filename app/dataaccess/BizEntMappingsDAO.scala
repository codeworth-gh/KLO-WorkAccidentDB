package dataaccess

import models.BusinessEntityMapping
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * DAO for mappings from .gov strings to klo ids.
 *
 * FIXME: If this ever gets used, update EntityMergeActor to fix the mappings as well.
 */
class BizEntMappingsDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile]  {
  import slick.jdbc.PostgresProfile.api._
  
  val beMapTbl = TableQuery[BusinessEntityMappingTable]
  val safetyWarrantTbl = TableQuery[SafetyWarrantsTable]
  
  def store( mp:BusinessEntityMapping ):Future[BusinessEntityMapping] = {
    mp.id match {
      case 0 => db.run( (beMapTbl returning beMapTbl.map(_.id)).into((m,id)=>m.copy(id=id)) += mp )
      case id:Long => db.run( beMapTbl.update(mp) ).map( _ => mp )
    }
  }
  
  def deleteMap( id:Long ):Future[Boolean] = db.run(
    beMapTbl.filter( _.id === id ).delete
  ).map( c => c>0 )
  
  def countAll():Future[Int] = db.run( beMapTbl.length.result )
  
  def countNamed( n:String ):Future[Int] = db.run( beMapTbl.filter(_.name.like("%"+n+"%") ).length.result )
  
  def list(skip:Int, fetchSize:Int, nameFilter:Option[String]):Future[Seq[BusinessEntityMapping]] = {
    var qry = beMapTbl.take(fetchSize)
    if ( skip>0 ) qry = qry.drop(skip)
    nameFilter.foreach( nf => qry = qry.filter(_.name.like("%"+nf+"%") ) )
    
    db.run(qry.sortBy(_.name).result)
  }
  
  def listForBizEnt( beId:Long ):Future[Seq[BusinessEntityMapping]] = db.run(
    beMapTbl.filter( _.bizEntityId === beId ).sortBy( _.name).result
  )
  
  /**
   * Apply the mapping on the existing warrants.
   * @param m mapping to apply
   */
  def applyMapping(m:BusinessEntityMapping):Future[Int] = {
    for {
      c1 <- db.run( safetyWarrantTbl.filter(_.operatorName===m.name).map(_.kloOperatorId).update( Some(m.bizEntId)) )
      c2 <- db.run( safetyWarrantTbl.filter(_.executorName===m.name).map(_.kloExecutorId).update( Some(m.bizEntId)) )
    } yield c1+c2
  }
}
