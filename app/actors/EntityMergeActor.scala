package actors

import org.apache.pekko.actor.{Actor, Props}
import controllers.BusinessEntityCtrl
import dataaccess.{BusinessEntityDAO, SafetyWarrantDAO, SanctionsDAO, SettingDAO, WorkAccidentDAO}
import models.EntityMergeLogEntry
import play.api.{Configuration, Logger}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import scala.concurrent.duration.Duration

object EntityMergeActor {
  def props:Props = Props[EntityMergeActor]()
  case class MergeEntities(from:Long, into:Long, mergeId:UUID)
}

class EntityMergeActor @Inject() (settings:SettingDAO, accidentsDao:WorkAccidentDAO,
                                  sanctionsDao:SanctionsDAO,
                                  bizEntDao: BusinessEntityDAO,
                                  safetyWarrantDao:SafetyWarrantDAO,
                                  config:Configuration)(implicit anEc:ExecutionContext) extends Actor {
  import EntityMergeActor._
  implicit private val D = Duration(5, duration.MINUTES)
  private val log = Logger(classOf[EntityMergeActor])
  
  override def receive: Receive = {
    case MergeEntities(from, into, id) => mergeEntities(from, into, id)
    
  }
  
  private def mergeEntities(from: Long, into: Long, mergeId:UUID):Unit = {
    log.info(s"$mergeId: Merging entity $from into $into")
    bizEntDao.store( EntityMergeLogEntry(mergeId, "*", "Starting merge") )
    BusinessEntityCtrl.ACTIVE_ENTITY_MERGES.put(mergeId, "AT_WORK")
    
    // injured workers
    val workers = w(accidentsDao.listWorkersForBizEnt(from))
    if ( workers.nonEmpty ) {
      workers.foreach( w => bizEntDao.store(EntityMergeLogEntry(mergeId, "injured_workers", s"id: " + w.id)))
      w(accidentsDao.batchUpdateEmployerId(from, into).map( r => log.info(s"$mergeId: Updated $r records for ${workers.size} workers")))
    } else log.info(s"$mergeId: no workers found for $from")
    
    // sanctions
    val sanctions = w(sanctionsDao.sanctionsForEntity(from))
    if ( sanctions.nonEmpty ) {
      sanctions.foreach( s => bizEntDao.store(EntityMergeLogEntry(mergeId, "sanctions", s"id: ${s.id}")))
      sanctionsDao.batchUpdateEntities(from, into).map( c => s"Updates $c records for ${sanctions.size} sanctions")
    }
    
    // safety warrants
    val warrants = w(safetyWarrantDao.getForBizEnt(from))
    if ( warrants.nonEmpty ) {
      warrants.foreach( w => bizEntDao.store(EntityMergeLogEntry(mergeId, "safety_warrants", s"id: ${w.id}")))
      w(safetyWarrantDao.batchUpdateBizEnts(from, into))
    } else log.info(s"$mergeId: No warrants found")
    
    // bart (business-accident relation table) accident
    val accidents = w(accidentsDao.accidentsForBizEnt(from))
    if ( accidents.nonEmpty ) {
      accidents.flatMap( acc => acc._2.toSeq.map( r => (acc._1.id,r.id)))
        .foreach( r => bizEntDao.store(EntityMergeLogEntry(mergeId, "bart_accident", s"accident: ${r._2}, bart_id: ${r._2}")))
      w(accidentsDao.batchUpdateBart(from, into))
      
    } else log.info( s"$mergeId: No accidents found")
    
    // business entities
    val doomedOpt = w(bizEntDao.get(from))
    if ( doomedOpt.isDefined ) {
      val doomed = doomedOpt.get
      var intoEnt = w(bizEntDao.get(into)).get
      if ( intoEnt.email.isEmpty && doomed.email.nonEmpty ) intoEnt = intoEnt.copy(email = doomed.email)
      if ( intoEnt.phone.isEmpty && doomed.phone.nonEmpty ) intoEnt = intoEnt.copy(phone = doomed.phone)
      if ( intoEnt.website.isEmpty && doomed.website.nonEmpty ) intoEnt = intoEnt.copy(website = doomed.website)
      if ( doomed.memo.nonEmpty ) {
        val memo = intoEnt.memo match {
          case None => doomed.memo
          case Some(text) => Some( text + "\n" + doomed.memo.get )
        }
        intoEnt = intoEnt.copy(memo = memo)
      }
      w( bizEntDao.store(intoEnt) )
    }
    w( bizEntDao.delete(from) )
    
    // update map
    BusinessEntityCtrl.ACTIVE_ENTITY_MERGES.put(mergeId, "DONE")
    log.info(s"$mergeId: Done merging entity $from into $into")
    bizEntDao.store(EntityMergeLogEntry(mergeId, "*", "Merge done"))
  }
  
  private def w[T](task:Future[T]):T = Await.result(task,D)
}
