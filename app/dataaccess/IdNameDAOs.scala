package dataaccess

import models.{Citizenship, Industry, InjuryCause, Region}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IdNameDAO[O,T<:IdNameTable[O]] (table: slick.lifted.TableQuery[T], protected val dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  
  def get(id:Int):Future[Option[O]] = db.run( table.filter( r => r.id === id ).take(1).result ).map( _.headOption )
  
  def put(rgn:O):Future[O] = {
    db.run(
      (table returning table).insertOrUpdate(rgn)
    ).map( insertRes => {
      insertRes.getOrElse(rgn)
    })
  }
  
  def list():Future[Seq[O]] = db.run(
    table.sortBy(_.name).result
  ).map( _.toSeq )
  
  def delete(id:Int):Future[Try[Int]] = db.run(
    table.filter( _.id === id ).delete.asTry
  )
}

class CitizenshipsDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext)
  extends IdNameDAO[Citizenship, CitizenshipsTable](slick.lifted.TableQuery[CitizenshipsTable],dbConfigProvider)

class RegionsDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext)
  extends IdNameDAO[Region, RegionsTable](slick.lifted.TableQuery[RegionsTable],dbConfigProvider)

class InjuryCausesDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext)
  extends IdNameDAO[InjuryCause, InjuryCausesTable](slick.lifted.TableQuery[InjuryCausesTable],dbConfigProvider)

class IndustriesDAO @Inject() (dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext)
  extends IdNameDAO[Industry, IndustriesTable](slick.lifted.TableQuery[IndustriesTable],dbConfigProvider)

