package dataaccess

import models.SafetyViolationSanction
import play.api.Configuration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, duration}

/**
 * DB I/O for SafetyViolationSanctions.
 */
class SafetyViolationSanctionDAO @Inject()(protected val dbConfigProvider:DatabaseConfigProvider, conf:Configuration)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  implicit private val D: FiniteDuration = Duration(5, duration.MINUTES)
  
  private val svsTable = TableQuery[SafetyViolationSanctionTable]
  
  def store(svs:SafetyViolationSanction):Future[SafetyViolationSanction] = {
    svs.id match {
      case 0 => db.run( (svsTable.returning( svsTable.map(_.id) ).into( (svs, newId)=>svs.copy(id=newId) )) += svs )
      case existingId:Int => db.run( svsTable.filter(_.id===svs.id).update(svs) ).map( _ => svs )
    }
  }
  
  def get( svsId:Int ):Future[Option[SafetyViolationSanction]] = db.run(
    svsTable.filter(_.id === svsId).result.headOption
  )
  
  def getForBusinessEntity( bizEntId:Long ): Future[Seq[SafetyViolationSanction]] = db.run(
    svsTable.filter(_.klo_businessEntityId === bizEntId).sortBy( _.sanctionDate ).result
  )
  
  def listAll():Future[Seq[SafetyViolationSanction]] = db.run(
    svsTable.sortBy( _.sanctionDate ).result
  )
  
  def isScraped( sanctionGovId:Int ): Future[Boolean] = db.run(
    svsTable.filter( _.sanctionNumber === sanctionGovId ).exists.result
  )
  
}
