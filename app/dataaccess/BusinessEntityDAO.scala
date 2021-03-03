package dataaccess

import models.BusinessEntity
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.model.Table

import javax.inject.Inject
import javax.swing.event.RowSorterListener
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BusinessEntityDAO {
  object SortKey extends Enumeration {
    val Name=Value
    val Phone=Value
    val Email=Value
    
    def named(name:String):Option[SortKey.Value] = {
      try {
        Some(SortKey.withName(name))
      } catch {
        case _:Exception => None
      }
    }
  }
}

class BusinessEntityDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val Entities = TableQuery[BusinessEntityTable]
  private val log = Logger(classOf[BusinessEntityDAO])
  
  def store( bizEnt:BusinessEntity ):Future[BusinessEntity] = {
    bizEnt.id match {
      case 0 => db.run((Entities returning Entities.map(_.id) ).into((b,newId)=>b.copy(id=newId)) += bizEnt)
      case _ => db.run(Entities.filter(_.id===bizEnt.id).update(bizEnt)).map( _ => bizEnt )
    }
  }
  
  // get by id
  def get(id:Long):Future[Option[BusinessEntity]] = db.run( Entities.filter( r => r.id === id ).take(1).result ).map( _.headOption )
  
  def listIdNamePairs():Future[Map[Long,String]] = db.run(
    Entities.map(r=>(r.id, r.name)).result
  ).map( _.toMap )
  
  // delete
  def delete(id:Long):Future[Try[Int]] = db.run(
    Entities.filter( _.id===id ).delete.asTry
  )
  
  // list
  def list(start:Int, pageSize:Int,
           sortBy:BusinessEntityDAO.SortKey.Value=BusinessEntityDAO.SortKey.Name, asc:Boolean=true):Future[Seq[BusinessEntity]] = {
    import BusinessEntityDAO.SortKey
    db.run(
      Entities.sortBy( r => {
        val f = sortBy match {
          case SortKey.Name  => r.name.asColumnOf[Option[String]].nullsFirst
          case SortKey.Phone => if (asc) r.phone.nullsLast else r.phone.nullsFirst
          case SortKey.Email => if (asc) r.email.nullsLast else r.email.nullsFirst
        }
        if ( asc ) f.asc else f.desc
      }).drop(start).take(pageSize).result).map( _.toSeq )
  }
  
  def countByName( namePart:String ):Future[Int] = db.run(
    Entities.filter( makeNameFilter(namePart) ).length.result
  )
  
  def countAll:Future[Int] = db.run( Entities.length.result )
  
  def listByName(namePart:String):Future[Seq[BusinessEntity]] = db.run(
    Entities.filter( makeNameFilter(namePart) ).sortBy(_.name).result
    ).map( _.toSeq )
  
  
  def findOrCreateNames(names:Set[String]):Future[Map[String,BusinessEntity]] = {
    val cleanNames = names.map(_.trim).filter(_.nonEmpty)
    for {
      existing <- db.run( Entities.filter( _.name inSet cleanNames ).result )
      missingNames  = cleanNames.removedAll( existing.map(_.name) )
      toAdd = missingNames.map( name => BusinessEntity(0, name, None, None, None, false, None) )
      added <- db.run( (Entities returning Entities)++=toAdd )
    } yield {
      (existing++added).map( a => a.name -> a ).toMap
    }
  }
  
  private def makeNameFilter(namePart: String) = {
    (r:BusinessEntityTable) => r.name.startsWith(namePart) ||
         r.name.like("%" + namePart + "%") ||
         r.name.endsWith(namePart)
  }
  
}
