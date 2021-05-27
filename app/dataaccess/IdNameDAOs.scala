package dataaccess

import models.{Citizenship, Industry, InjuryCause, Region, RelationToAccident}
import play.api.cache.SyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import scala.reflect.ClassTag
import scala.util.Try

class IdNameDAO[O:ClassTag,T<:IdNameTable[O]] (table: slick.lifted.TableQuery[T],
                                      cache: SyncCacheApi,
                                      protected val dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val cacheKey = this.getClass.getCanonicalName + "%"
  
  def get(id:Int):Future[Option[O]] = db.run( table.filter( r => r.id === id ).take(1).result ).map( resRows => {
    val res = resRows.headOption
    res.foreach( t => cache.set(cacheKey + id, t, Duration(5, duration.MINUTES)) )
    res
  })
  
  def apply( id:Int ):Option[O] = {
    cache.get[O](cacheKey + id) match {
      case None => {
        Await.result( get(id), Duration(1, duration.MINUTES) )
      }
      case Some(p) => Some(p)
    }
  }
  
  def put(itm:O):Future[O] = {
    db.run(
      (table returning table).insertOrUpdate(itm)
    ).map( insertRes => {
      insertRes.getOrElse(itm)
    })
  }
  
  def list():Future[Seq[O]] = db.run(
    table.sortBy(_.name).result
  ).map( _.toSeq )
  
  def delete(id:Int):Future[Try[Int]] = {
    db.run(
      table.filter( _.id === id ).delete.asTry
    ).map( res => {
      cache.remove(cacheKey + id)
      res
    })
  }
}

class CitizenshipsDAO @Inject() (dbConfigProvider:DatabaseConfigProvider, sc:SyncCacheApi)(implicit ec:ExecutionContext)
  extends IdNameDAO[Citizenship, CitizenshipsTable](slick.lifted.TableQuery[CitizenshipsTable], sc,dbConfigProvider)

class RegionsDAO @Inject() (dbConfigProvider:DatabaseConfigProvider, sc:SyncCacheApi)(implicit ec:ExecutionContext)
  extends IdNameDAO[Region, RegionsTable](slick.lifted.TableQuery[RegionsTable], sc, dbConfigProvider)

class InjuryCausesDAO @Inject() (dbConfigProvider:DatabaseConfigProvider, sc:SyncCacheApi)(implicit ec:ExecutionContext)
  extends IdNameDAO[InjuryCause, InjuryCausesTable](slick.lifted.TableQuery[InjuryCausesTable], sc, dbConfigProvider)

class IndustriesDAO @Inject() (dbConfigProvider:DatabaseConfigProvider, sc:SyncCacheApi)(implicit ec:ExecutionContext)
  extends IdNameDAO[Industry, IndustriesTable](slick.lifted.TableQuery[IndustriesTable], sc, dbConfigProvider)

class RelationToAccidentDAO @Inject() (dbConfigProvider:DatabaseConfigProvider, sc:SyncCacheApi)(implicit ec:ExecutionContext)
  extends IdNameDAO[RelationToAccident, RelationToAccidentTable](slick.lifted.TableQuery[RelationToAccidentTable], sc, dbConfigProvider)

object RelationToAccidentDAO {
  val DIRECT_EMPLOYMENT_ID = 1024
}