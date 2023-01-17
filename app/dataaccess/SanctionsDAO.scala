package dataaccess

import models.Sanction
import play.api.{Configuration, Logger}
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SanctionsDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                              config:Configuration, cache:AsyncCacheApi
                             )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  private val log = Logger(classOf[SanctionsDAO])
  private val sanctions = TableQuery[SanctionTable]
  
  def store(s:Sanction):Future[Sanction] = {
    if ( s.id == 0 ) {
      db.run( sanctions.returning( sanctions.map(_.id) ).into( (s,newId)=>s.copy(id=newId) ) += s )
    } else {
      db.run( sanctions.update(s) ).map( _ => s )
    }
  }
  
  def delete( sanctionId:Long ):Future[Int] = db.run(
    sanctions.filter( _.id === sanctionId ).delete
  )
  
  def sanctionsForEntity( entityId:Long ):Future[Seq[Sanction]] = db.run(
    sanctions.filter( _.businessEntityId === entityId )
      .sortBy(_.applicationDate.desc )
      .result
  )
  
}
