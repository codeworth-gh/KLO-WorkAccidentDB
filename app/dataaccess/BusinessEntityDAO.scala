package dataaccess

import models.BusinessEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class BusinessEntityDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val Entities = TableQuery[BusinessEntityTable]
  
  def store( bizEnt:BusinessEntity ):Future[BusinessEntity] = {
    bizEnt.id match {
      case 0 => db.run((Entities returning Entities.map(_.id) ).into((b,newId)=>b.copy(id=newId))+= bizEnt)
      case _ => db.run(Entities.update(bizEnt)).map( _ => bizEnt )
    }
  }
  
  // get by id
  def get(id:Long):Future[Option[BusinessEntity]] = db.run( Entities.filter( r => r.id === id ).take(1).result ).map( _.headOption )
  
  
  // delete
  def delete(id:Long):Future[Try[Int]] = db.run(
    Entities.filter( _.id===id ).delete.asTry
  )
  
  // list
  def list(start:Int, pageSize:Int):Future[Seq[BusinessEntity]] = db.run(
    Entities.drop(start).take(pageSize).sortBy(_.name).result
  ).map( _.toSeq )
  
  def countByName( namePart:String ):Future[Int] = db.run(
    Entities.filter( makeNameFilter(namePart) ).length.result
  )
  
  def listByName(namePart:String):Future[Seq[BusinessEntity]] = db.run(
    Entities.filter( makeNameFilter(namePart) ).sortBy(_.name).result
    ).map( _.toSeq )
  
  private def makeNameFilter(namePart: String) = {
    (r:BusinessEntityTable) => r.name.startsWith(namePart) ||
         r.name.like("%" + namePart + "%") ||
         r.name.endsWith(namePart)
  }
  
}
