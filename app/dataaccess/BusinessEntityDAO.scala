package dataaccess

import dataaccess.BusinessEntityDAO.StatsSortKey
import models.{BusinessEntity, BusinessEntityStats, EntityMergeLogEntry}
import play.api.{Configuration, Logger}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, duration}
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
  
  object StatsSortKey extends Enumeration {
    val Name      = Value
    val Accidents = Value
    val Killed    = Value
    val Injured   = Value
    
    def named(name:String):Option[StatsSortKey.Value] = {
      try {
        Some(withName(name))
      } catch {
        case _:Exception => None
      }
    }
  }
}

class BusinessEntityDAO @Inject()(protected val dbConfigProvider:DatabaseConfigProvider, conf:Configuration)(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val Entities = TableQuery[BusinessEntityTable]
  private val Stats = TableQuery[BusinessEntityStatsTable]
  private val MergeRecs = TableQuery[EntityMergeLogRecordTable]
  
  private val log = Logger(classOf[BusinessEntityDAO])
  private val stopWords = conf.get[Seq[String]]("klo.bizEntStopWords").toSet
  implicit private val D: FiniteDuration = Duration(5, duration.MINUTES)
  
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
      toAdd = missingNames.map( name => BusinessEntity(0, name, None, None, None, None,
                                                          isPrivatePerson = false, isKnownContractor = false, None) )
      added <- db.run( (Entities returning Entities)++=toAdd )
    } yield {
      (existing++added).map( a => a.name -> a ).toMap
    }
  }
  
  def listStats(searchStr:String, knownOnly:Boolean, start:Int, pageSize:Int, sortBy:StatsSortKey.Value=StatsSortKey.Accidents, isAsc:Boolean=false):Future[Seq[BusinessEntityStats]] = {
    val base = if ( searchStr.isBlank ) Stats else Stats.filter(r=>r.name.like("%"+searchStr+"%"))
    val p2 = if ( knownOnly ) base.filter( _.isKnownContractor === true ) else base
    db.run(p2.sortBy(makeStatsSorter(sortBy, isAsc)).drop(start).take(pageSize).result)
  }
  
  def countStats(searchStr:String, knownOnly:Boolean):Future[Int] = {
    val base = if (searchStr.isBlank) Stats else Stats.filter(r => r.name.like("%" + searchStr + "%"))
    val p2 = if (knownOnly) base.filter(_.isKnownContractor === true) else base
    db.run(p2.length.result)
  }
  
  private def makeStatsSorter( sk:StatsSortKey.Value, asc:Boolean ) = sk match {
    case StatsSortKey.Name      => (r:BusinessEntityStatsTable) => if (asc) r.name.asc else r.name.desc
    case StatsSortKey.Accidents => (r:BusinessEntityStatsTable) => if (asc) r.accCnt.asc else r.accCnt.desc
    case StatsSortKey.Killed    => (r:BusinessEntityStatsTable) => if (asc) r.kldCnt.asc else r.kldCnt.desc
    case StatsSortKey.Injured   => (r:BusinessEntityStatsTable) => if (asc) r.injCnt.asc else r.injCnt.desc
  }
  
  private def makeNameFilter(namePart: String) = {
    (r:BusinessEntityTable) => r.name.startsWith(namePart) ||
         r.name.like("%" + namePart + "%") ||
         r.name.endsWith(namePart)
  }
  
  def findSimilarNames(name:String):Future[Seq[(Long, String)]] = {
    val effName = name.trim()
    if ( effName.isEmpty ) return Future(Seq())
    
    val lvnQuery = sql"""select id, name
                         |from business_entities be
                         |where (levenshtein(be.name, ${effName}::varchar)::real)/(greatest(length(be.name), length(${effName}))::real) < 0.55;
                         |""".stripMargin.as[(Long, String)]
    val baseWords = effName.split(" ").filter(s => !s.isBlank)
    val words = baseWords.filter(w => !stopWords(w))
    val likeQueries = words.filter( _.length > 2 ).map( w => {
      val likeClause = s"%$w%"
      sql"""select id, name from business_entities where name like ${likeClause}""".as[(Long, String)]
    })
    val dbCmd = DBIO.sequence(likeQueries.toVector)
    for {
      l1 <- db.run(lvnQuery)
      l2 <- if ( likeQueries.nonEmpty) db.run(dbCmd) else Future(Seq())
    } yield {
      val pairSet:Set[(Long,String)] = (l1 ++ l2.flatten).toSet
      pairSet.toSeq.sortBy( p=>p._2 )
    }
  }
  
  def store( emr:EntityMergeLogEntry ):Future[Int] = {
    db.run(
      MergeRecs += emr
    )
  }
  
  def enrichPCNums():Unit = {
    val counter = new java.util.concurrent.atomic.AtomicInteger(0)
    val possiblyUpdateable = Await.result(db.run(
      Entities.filter( rec => !rec.pcNumber.isDefined || rec.pcNumber.isEmpty )
        .filter( rec => ! rec.memo.isEmpty ).result
    ), D)
    val pcNumDetector = "(ח.פ.|חפ|ח.פ|ח\"פ)\\s*([0-9]+)".r
    possiblyUpdateable.filter(ent => ent.memo.nonEmpty && !ent.memo.get.isBlank)
      .map( ent  => (ent, pcNumDetector.findAllMatchIn(ent.memo.get).toSeq))
      .filter( p => p._2.size == 1)
      .map( p    => (p._1, p._2.head) )
      .filter( p => p._2.groupCount==2 )
      .map( p => (p._1, p._2.group(2)) ) // now it's (entity, new pcNum)
      .map( p => p._1.copy( pcNumber = Some(p._2.toLong)))
      .foreach( p=> {
        log.info(s"Updating ${p.id} with pcNumber ${p.pcNumber.get}. Memo: '${p.memo}'")
        store( p )
        counter.incrementAndGet()
      })
      log.info(s"Updated ${counter.get()} records.")
  }
}
