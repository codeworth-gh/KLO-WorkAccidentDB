package dataaccess

import controllers.PublicCtrl
import models.{BusinessEntity, Industry, InjuredWorker, InjuredWorkerRow, RelationToAccident, Severity, WorkAccident, WorkAccidentSummary}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.{GetResult, JdbcProfile, PostgresProfile}

import java.time.{LocalDate, LocalDateTime}
import javax.inject.Inject
import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object WorkAccidentDAO {
  object SortKey extends Enumeration {
    val Datetime = Value
    val Region   = Value
    val Injuries = Value
    val Fatalities    = Value
  
    def named(name:String):Option[SortKey.Value] = {
      try {
        Some(SortKey.withName(name))
      } catch {
        case _:Exception => None
      }
    }
  }
}

class WorkAccidentDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                 citizenships: CitizenshipsDAO, regions: RegionsDAO,
                                 industries: IndustriesDAO, injuryCauses: InjuryCausesDAO,
                                 relationTypes:RelationToAccidentDAO,  cacheApi:AsyncCacheApi
                                )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  import WorkAccidentDAO.SortKey
  private val workAccidents = TableQuery[WorkAccidentsTable]
  private val injuredWorkers = TableQuery[InjuredWorkersTable]
  private val businessEntities = TableQuery[BusinessEntityTable]
  private val businessEntitiesSummaries = TableQuery[BusinessEntitySummaryTable]
  private val workAccidentSummaries = TableQuery[WorkAccidentSummaryTable]
  private val accidentBizEntRelations = TableQuery[AccidentToBusinessEntityTable]
  private val workersAndEmployers = injuredWorkers.joinLeft(businessEntities).on( (w,e)=>w.employer_id === e.id )
  private val accidentAndBizEnts = accidentBizEntRelations.join(businessEntities).on( (a,b)=>a.bizEntId === b.id )
  private val accidentAndBizEntSums = accidentBizEntRelations.join(businessEntitiesSummaries).on( (a,b)=>a.bizEntId === b.id )
  
  // Filteration functions
  private val related = (w: InjuredWorkersTable, r: Rep[Long]) => w.accident_id === r
  
  private val injWkr_ind_allowNulls = (w: InjuredWorkersTable) => w.industry_id.isEmpty
  private val injWkr_ind_inSet = (w: InjuredWorkersTable, industryIds:Set[Int]) => w.industry_id.inSet(industryIds.filter(_ > -1))
  private val injWkr_ind_inSetOrNull = (w: InjuredWorkersTable, industryIds:Set[Int]) => injWkr_ind_allowNulls(w) || injWkr_ind_inSet(w, industryIds)
  
  private val injWkr_sev_allowNulls = (w: InjuredWorkersTable) => w.injury_severity.isEmpty
  private val injWkr_sev_inSet = (w: InjuredWorkersTable, severities:Set[Int]) => w.injury_severity.inSet(severities.filter(_ > -1))
  private val injWkr_sev_inSetOrNull = (w: InjuredWorkersTable, severities:Set[Int]) => injWkr_sev_allowNulls(w) || injWkr_sev_inSet(w, severities)
  
  private val injWkr_ctz_allowNulls = (w: InjuredWorkersTable) => w.citizenship_id.isEmpty
  private val injWkr_ctz_inSet = (w: InjuredWorkersTable, citizenshipIds:Set[Int]) => w.citizenship_id.inSet(citizenshipIds.filter(_ > -1))
  private val injWkr_ctz_inSetOrNull = (w: InjuredWorkersTable, citizenshipIds:Set[Int]) => injWkr_ctz_allowNulls(w) || injWkr_ctz_inSet(w, citizenshipIds)
  
  private val injWkr_ics_allowNulls = (w: InjuredWorkersTable) => w.injury_cause_id.isEmpty
  private val injWkr_ics_inSet = (w: InjuredWorkersTable, injuryCauseIds:Set[Int]) => w.injury_cause_id.inSet(injuryCauseIds.filter(_ > -1))
  private val injWkr_ics_inSetOrNull = (w: InjuredWorkersTable, injuryCauseIds:Set[Int]) => injWkr_ics_allowNulls(w) || injWkr_ics_inSet(w, injuryCauseIds)
  
  private val log = Logger(classOf[WorkAccidentDAO])
  
  def store( iw:InjuredWorker, accidentId:Long ):Future[InjuredWorker] = {
    cacheApi.remove(PublicCtrl.INDEX_PAGE_CACHE_KEY)
    db.run(
      (injuredWorkers returning injuredWorkers.map(_.id)).into((_, newId) => iw.copy(id=newId)).insertOrUpdate( toDto(iw, accidentId) )
    ).map( _.getOrElse(iw) )
  }
  
  def store( wa:WorkAccident ):Future[WorkAccident] = {
    cacheApi.remove(PublicCtrl.INDEX_PAGE_CACHE_KEY)
    val waRow = toDto(wa)
    val relateds = toRelationRecords(wa)
    val isNew = waRow.id == 0
    
    for {
      waIn <- db.run((workAccidents returning workAccidents.map(_.id)).into((_, newId)=>waRow.copy(id=newId)).insertOrUpdate(waRow))
      accId = waIn.getOrElse(waRow).id
      _    <- if (!isNew) db.run(injuredWorkers.filter(_.accident_id === accId).delete ) // remove old injured workers
                else Future(()) // new accident, no need to delete old records
      _    <- if (!isNew) db.run(accidentBizEntRelations.filter(_.accidentId===accId).delete ) // remove old related biz ents
                else Future(()) // new accident, no need to delete old records
      iwIn <- Future.sequence( wa.injured.map( store(_, accId)) ) // easy, as all are new now
      rlts = if ( isNew ) relateds.map( _.copy(accidentId=accId) ) else relateds
      _    <- db.run( accidentBizEntRelations ++= rlts.toSeq )
    } yield {
      wa.copy( id=accId, injured = iwIn )
    }
  }
  
  def getAccident( id:Long ):Future[Option[WorkAccident]] = {
    for {
      waRowOpt <- db.run( workAccidents.filter(_.id===id).result ).map( _.headOption )
      iwRows   <- workersInAccident(id)
      relateds <- db.run(accidentAndBizEnts.filter( r => r._1.accidentId === id ).result )
      bizEnts   = relateds.map( r => (r._1.relationTypeId, r._2) ).map( t => (relationTypes(t._1).get, t._2) )
    } yield {
      waRowOpt.map( row => fromDto(row, iwRows.toSet, bizEnts.toSet))
    }
  }
  
  def listAccidentsRequiringUpdate():Future[Seq[WorkAccidentSummary]] = {
    for {
      wa  <- db.run( workAccidentSummaries.filter(_.requiresUpdate).sortBy(_.dateTime.desc).result )
      ids = wa.map( _.id ).toSet
      biz <- db.run( accidentAndBizEntSums.filter( r => r._1.accidentId.inSet(ids)).result )
    } yield {
      val relatedRaw = biz.groupBy( _._1.accidentId )
      val related = relatedRaw.map( kv => kv._1 -> kv._2.map(r=>(relationTypes(r._1.relationTypeId).get, r._2)).toSet )
      wa.map( rec => rec.toObject(related.get(rec.id).map(_.toSet).getOrElse(Set())))
    }
  }
  
  def listAccidents(fromOpt:Option[LocalDate], toOpt:Option[LocalDate], regionIds:Set[Int],
                    industryIds:Set[Int], citizenshipIds:Set[Int], injuryCauseIds:Set[Int],
                    severities:Set[Severity.Value], includeNullSeverities:Boolean,
    start:Int, pageSize:Int, sortBy:SortKey.Value=SortKey.Datetime, isAsc:Boolean=false):Future[Seq[WorkAccidentSummary]] = {
    var qry:Query[WorkAccidentSummaryTable, WorkAccidentSummaryTable#TableElementType, Seq] = workAccidentSummaries
    fromOpt.foreach(d => qry = qry.filter(r => r.dateTime >= d.atStartOfDay()))
    toOpt.foreach(d => qry = qry.filter(r => r.dateTime < d.plusDays(1).atStartOfDay()))
  
    if ( regionIds.nonEmpty ){
      qry = (regionIds(-1), regionIds.exists(_ > -1)) match {
        case (true, false) => qry.filter(r => r.regionId.isEmpty )
        case (false, true) => qry.filter(r => r.regionId.inSet(regionIds) )
        case (true,  true) => qry.filter(r => r.regionId.inSet(regionIds) || r.regionId.isEmpty )
      }
    }
    
    if (industryIds.nonEmpty) {
      // filter by industry id
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (industryIds.size>1, industryIds(-1)) match {
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ind_allowNulls(w)
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ind_inSetOrNull(w, industryIds)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ind_inSet(w, industryIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
  
    if (citizenshipIds.nonEmpty) {
      // filter by citizenship id
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (citizenshipIds.size>1, citizenshipIds(-1)) match {
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ctz_inSetOrNull(w, citizenshipIds)
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ctz_allowNulls(w)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ctz_inSet(w, citizenshipIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
    
    if (injuryCauseIds.nonEmpty) {
      // filter by injury cause
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (injuryCauseIds.size>1, injuryCauseIds(-1)) match {
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ics_inSetOrNull(w, injuryCauseIds)
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ics_allowNulls(w)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ics_inSet(w, injuryCauseIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
    
    if ( severities.nonEmpty || includeNullSeverities ) {
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (severities.nonEmpty, includeNullSeverities) match {
        case (false,true) => (w:InjuredWorkersTable) => injWkr_sev_allowNulls(w)
        case (true,false) => (w:InjuredWorkersTable) => injWkr_sev_inSet(w, severities.map(_.id))
        case (true,true) => (w:InjuredWorkersTable) => injWkr_sev_inSetOrNull(w, severities.map(_.id))
      }
      qry = qry.filter( r => injuredWorkers.filter(w => related(w,r.id) && filter(w)).exists )
    }
    
    for {
      accs <- db.run(qry.sortBy(makeSorter(sortBy, isAsc)).drop(start).take(pageSize).result)
      acIds = accs.map( _.id ).toSet
      bzen <- db.run( accidentAndBizEntSums.filter( r => r._1.accidentId.inSet(acIds)).result )
    } yield {
      val relatedRaw = bzen.groupBy( _._1.accidentId )
      val related = relatedRaw.map( kv => kv._1 -> kv._2.map(r=>(relationTypes(r._1.relationTypeId).get, r._2)).toSet )
      accs.map( rec => rec.toObject(related.get(rec.id).map(_.toSet).getOrElse(Set())))
    }
  }
  
  def listAllAccidents():Future[Seq[WorkAccidentSummary]] = {
    for {
      sums <- db.run(workAccidentSummaries.sortBy(_.dateTime.asc).result)
      rels <- db.run(accidentAndBizEntSums.result)
      relMapRaw = rels.groupBy( s => s._1.accidentId )
      relMap = relMapRaw.map( p => p._1 -> p._2.map( t => relationTypes(t._1.relationTypeId).get -> t._2).toSet)
      unified = sums.map( s => s.toObject(relMap.getOrElse(s.id, Set())))
    } yield unified
  }
  
  def accidentsForBizEnt( bizEntId:Long ): Future[Map[WorkAccidentSummary, Set[RelationToAccident]]] = {
    db.run( accidentAndBizEntSums.filter(r => r._2.id === bizEntId)
        .join(workAccidentSummaries).on( (abe, was) => abe._1.accidentId === was.id )
        .sortBy(r=>r._2.dateTime.desc.nullsLast).result)
      .map( rows => rows.map(r => (r._2, relationTypes(r._1._1.relationTypeId).get) )
        .groupBy(_._1).map( kv => kv._1 -> kv._2.map(_._2).toSet)
        .map( p => p._1.toObject(Set()) -> p._2 )
      )
  }
  
  def deleteAccident(id:Long):Future[Try[Int]] = {
    cacheApi.remove(PublicCtrl.INDEX_PAGE_CACHE_KEY)
    db.run(
      workAccidents.filter( _.id === id ).delete.asTry
    )
  }
  
  private def makeSorter( sk:SortKey.Value, asc:Boolean ) = sk match {
    case SortKey.Datetime     => (r:WorkAccidentSummaryTable) => if (asc) r.dateTime.asc.nullsLast else r.dateTime.desc.nullsFirst
    case SortKey.Region       => (r:WorkAccidentSummaryTable) => if (asc) r.regionId.asc.nullsLast else r.regionId.desc.nullsFirst
    case SortKey.Injuries     => (r:WorkAccidentSummaryTable) => if (asc) r.injuredCount.asc.nullsLast else r.injuredCount.desc.nullsFirst
    case SortKey.Fatalities   => (r:WorkAccidentSummaryTable) => if (asc) r.killedCount.asc.nullsLast else r.killedCount.desc.nullsFirst
  }
  
  def filteredWorkAccidents(fromOpt:Option[LocalDate], toOpt:Option[LocalDate], regionIds:Set[Int],
                            industryIds:Set[Int], citizenshipIds:Set[Int], injuryCauseIds:Set[Int],
                            severities:Set[Severity.Value], includeNullSeverities:Boolean):Query[WorkAccidentsTable, WorkAccidentRecord, Seq] = {
    var qry = workAccidents
      .filterOpt(fromOpt)((r,d) => r.date_time >= d.atStartOfDay())
      .filterOpt(toOpt)((r,d)   => r.date_time < d.plusDays(1).atStartOfDay())
    
    if ( regionIds.nonEmpty ){
      qry = (regionIds(-1), regionIds.exists(_ > -1)) match {
        case (true, false) => qry.filter(r => r.regionId.isEmpty )
        case (false, true) => qry.filter(r => r.regionId.inSet(regionIds) )
        case (true,  true) => qry.filter(r => r.regionId.inSet(regionIds) || r.regionId.isEmpty )
      }
    }
    
    if (industryIds.nonEmpty) {
      // filter by industry id
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (industryIds.size>1, industryIds(-1)) match {
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ind_allowNulls(w)
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ind_inSetOrNull(w, industryIds)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ind_inSet(w, industryIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
  
    if (citizenshipIds.nonEmpty) {
      // filter by citizenship id
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (citizenshipIds.size>1, citizenshipIds(-1)) match {
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ctz_inSetOrNull(w, citizenshipIds)
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ctz_allowNulls(w)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ctz_inSet(w, citizenshipIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
  
    if (injuryCauseIds.nonEmpty) {
      // filter by injury cause
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (injuryCauseIds.size>1, injuryCauseIds(-1)) match {
        case (true, true)  => (w:InjuredWorkersTable) => injWkr_ics_inSetOrNull(w, injuryCauseIds)
        case (false, true) => (w:InjuredWorkersTable) => injWkr_ics_allowNulls(w)
        case (_   , false) => (w:InjuredWorkersTable) => injWkr_ics_inSet(w, injuryCauseIds)
      }
      qry = qry.filter( r => injuredWorkers.filter(w=>related(w,r.id) && filter(w)).exists )
    }
  
    if ( severities.nonEmpty || includeNullSeverities ) {
      val filter:InjuredWorkersTable=>Rep[Option[Boolean]] = (severities.nonEmpty, includeNullSeverities) match {
        case (false,true) => (w:InjuredWorkersTable) => injWkr_sev_allowNulls(w)
        case (true,false) => (w:InjuredWorkersTable) => injWkr_sev_inSet(w, severities.map(_.id))
        case (true,true) => (w:InjuredWorkersTable) => injWkr_sev_inSetOrNull(w, severities.map(_.id))
      }
      qry = qry.filter( r => injuredWorkers.filter(w => related(w,r.id) && filter(w)).exists )
    }
    
    qry
  }
  
  def accidentCount(fromOpt:Option[LocalDate], toOpt:Option[LocalDate], regionIds:Set[Int],
                    industryIds:Set[Int], citizenshipIds:Set[Int], injuryCauseIds:Set[Int],
                    severities:Set[Severity.Value], includeNullSeverities:Boolean):Future[Int] = {
    val qry = filteredWorkAccidents(fromOpt, toOpt, regionIds, industryIds, citizenshipIds, injuryCauseIds, severities, includeNullSeverities)
    db.run( qry.length.result )
  }
  
  def injuredWorkerCount():Future[Map[Option[Int], Int]] = db.run(
    injuredWorkers.groupBy( _.injury_severity )
        .map{ case (severity, group)=>(severity, group.length)}.result
  ).map( _.toMap )
  
  def getInjuredWorker( id:Long ):Future[Option[InjuredWorker]] = {
    for {
      res <- db.run(workersAndEmployers.filter(_._1.id===id).result.headOption)
    } yield {
      res.map( row=>fromDto(row._1, row._2) )
    }
  }
  
  def workersInAccident(accId:Long):Future[Seq[InjuredWorker]] = db.run(
    workersAndEmployers.filter( _._1.accident_id === accId ).sortBy(_._1.name).result
  ).map( _.map( p=>fromDto(p._1, p._2)) )
  
  def deleteWorker( id:Long ):Future[Try[Int]] = {
    val r = db.run(
      injuredWorkers.filter(_.id===id).delete.asTry
    )
    cacheApi.remove(PublicCtrl.INDEX_PAGE_CACHE_KEY)
    r
  }
  
  def listWorkers():Future[Seq[InjuredWorker]] = {
    for {
      iws <- db.run(injuredWorkers.sortBy(_.injury_severity.desc).result )
      ems <- db.run( businessEntities.result )
    } yield {
      val emsMap: Map[Long, BusinessEntity] = ems.map(e => e.id -> e).toMap
      iws.map(iwr => fromDto(iwr, iwr.employer.flatMap(id => emsMap.get(id))))
    }
  }
  
  def listWorkersForBizEnt( bizEnt:Long ):Future[Seq[InjuredWorkerRecord]] = db.run(
    injuredWorkers.filter( _.employer_id === bizEnt ).result
  )
  
  def batchUpdateEmployerId( from:Long, to:Long ):Future[Int] = db.run(
    injuredWorkers.filter( _.employer_id === from ).map( _.employer_id ).update(Some(to))
  )
  
  /**
   * Batch update the business-accident relation table
   * @param from id of entity being removed
   * @param into id of entity that will get the accidents
   * @return number of records updated
   */
  def batchUpdateBart( from:Long, into:Long ):Future[Int] = db.run(
    accidentBizEntRelations.filter( _.bizEntId === from ).map(_.bizEntId).update(into)
  )
  
  /**
   *
   * @param year
   * @return Seq of (InjuredWorker, WorkAccidentSummary), where ._2 is the accident id
   */
  def listKilledWorkers( year:Int ): Future[Seq[(InjuredWorker, Long, LocalDate)]] = {
    val start = LocalDateTime.of(year,1,1,0,0)
    val end = start.withYear( year+1 )
    db.run(
      workersAndEmployers.join(workAccidents).on((wae,acc)=>wae._1.accident_id === acc.id)
        .filter( _._2.date_time.between(start, end))
        .filter( _._1._1.injury_severity === Severity.fatal.id )
        .sortBy( _._2.date_time.desc ).result
    ).map( _.map( r => (fromDto(r._1._1, r._1._2), r._2.id, r._2.when.toLocalDate)) )
  }
  
  def listAllInjuredWorkers: Future[Seq[InjuredWorkerRow]] = db.run(
    workersAndEmployers.join(workAccidents).on((wae,acc)=>wae._1.accident_id === acc.id)
      .sortBy( _._2.date_time.desc ).result
  ).map( _.map( r => InjuredWorkerRow(fromDto(r._1._1, r._1._2), r._2.id, r._2.regionId, r._2.when.toLocalDate, r._2.location, r._2.officiallyRecognized)) )
  
  def listRecentInjuredWorkers(count:Int): Future[Seq[InjuredWorkerRow]] = db.run(
    workersAndEmployers.join(workAccidents).on((wae,acc)=>wae._1.accident_id === acc.id)
      .sortBy( _._2.date_time.desc )
      .take(count).result
  ).map( _.map( r => InjuredWorkerRow(fromDto(r._1._1, r._1._2), r._2.id, r._2.regionId, r._2.when.toLocalDate, r._2.location, r._2.officiallyRecognized)) )
  
  private def makeSeverityMap(k: Option[Industry], numbers: Seq[(Option[Int], Option[Int], Int)]):Map[Option[Severity.Value], Int] = {
    val relevantRows = numbers.filter( _._1 == k.map(_.id) )
    relevantRows.map( row => row._2.map( i => Severity(i))->row._3 ).toMap
  }
  
  def injuryCountsByIndustryAndSeverity(year:Int):Future[Map[Option[Industry],Map[Option[Severity.Value],Int]]] = {
    val qry = sql"""SELECT industry_id, injury_severity, count(*) as count
                   |FROM work_accidents wa INNER JOIN injured_workers iw on wa.id = iw.accident_id
                   |WHERE date_part('year', wa.date_time)=$year
                   |GROUP BY industry_id, injury_severity;
                   |""".stripMargin.as[(Option[Int],Option[Int],Int)]
    for {
      numbers <- db.run(qry)
      indList <- industries.list()
    } yield {
      val keys = indList.map( Some(_) ) ++ Seq(None)
      keys.map( k => k->makeSeverityMap(k,numbers) ).toMap
    }
  }
  
  def injuryCountsBySeverityAndYear:Future[Map[Int,Map[Option[Severity.Value],Int]]] = {
    val qry = sql"""SELECT date_part('year', wa.date_time) as year, injury_severity, count(*) as count
                   |FROM work_accidents wa INNER JOIN injured_workers iw on wa.id = iw.accident_id
                   |GROUP BY year, injury_severity
                   |ORDER BY year desc;
                   |""".stripMargin.as[(Int, Option[Int], Int)]
    for {
      numbers <- db.run(qry)
    } yield {
      val years = numbers.map(_._1).distinct
      val sevByYear = years.map( y => y ->
                      numbers.filter(_._1==y).map(row=>row._2.map(v=>Severity(v))->row._3).toMap).toMap
      sevByYear.map( year => year._1 -> completeSet(year._2) )
    }
  }
  
  private def completeSet(value: Map[Option[Severity.Value], Int]):Map[Option[Severity.Value], Int] = {
    var retVal = value
    for ( sev <- Severity.values ) {
      if ( !retVal.keySet(Some(sev)) ) {
        retVal = retVal + (Some(sev)->0)
      }
    }
    if ( !retVal.keySet(None) ) {
      retVal = retVal + (None->0)
    }
    retVal
  }
  
  // FIXME: Cache this forever, invalidate on write.
  def listYearsWithAccidents:Future[Seq[Int]] = db.run(
    sql"select distinct date_part('year', date_time) from work_accidents ORDER BY date_part".as[Int]
  )
  
  def getLastUpdateDate:Future[LocalDateTime] = db.run(
    sql"select max(date_time) from work_accidents".as[LocalDateTime]
  ).map( res => res.headOption.getOrElse(LocalDateTime.of(1970,1,1,0,0)))
  
  private def fromDto(iwRow:InjuredWorkerRecord, employer:Option[BusinessEntity]) = InjuredWorker( iwRow.id, iwRow.name, iwRow.age, iwRow.citizenship.flatMap(citizenships(_)),
    iwRow.industry.flatMap(industries(_)), employer, iwRow.from, iwRow.injuryCause.flatMap(injuryCauses(_)),
    iwRow.injurySeverity.map( Severity.apply ), iwRow.injuryDescription, iwRow.publicRemarks, iwRow.sensitiveRemarks
  )
  
  private def fromDto( waRow:WorkAccidentRecord, iws:Set[InjuredWorker], relateds:Set[(RelationToAccident, BusinessEntity)] ) = WorkAccident(
    waRow.id, waRow.when, relateds, waRow.location, waRow.regionId.flatMap( regions.apply ),
    waRow.blogPostUrl, waRow.details, waRow.investigation, waRow.initialSource,
    waRow.mediaReports.split("\n").toSet, waRow.publicRemarks, waRow.sensitiveRemarks,
    iws, waRow.requiresUpdate, waRow.officiallyRecognized
  )
  
  private def toDto( wa:WorkAccident ) = WorkAccidentRecord(
      wa.id, wa.when,
      location = wa.location,
      regionId = wa.region.map(_.id),
      blogPostUrl = wa.blogPostUrl,
      details = wa.details,
      investigation = wa.investigation,
      initialSource = wa.initialSource,
      mediaReports = wa.mediaReports.mkString("\n"),
      publicRemarks = wa.publicRemarks,
      sensitiveRemarks = wa.sensitiveRemarks,
      requiresUpdate = wa.requiresUpdate,
      wa.officiallyRecognized
  )
  
  private def toRelationRecords( wa:WorkAccident ) = {
    wa.relatedEntities.map( t => RelationToAccidentRecord(wa.id, t._1.id, t._2.id) ) ++
    wa.injured.filter( _.employer.nonEmpty )
        .map( inj => RelationToAccidentRecord(wa.id, RelationToAccidentDAO.DIRECT_EMPLOYMENT_ID, inj.employer.get.id))
  }
  
  
  private def toDto( iw:InjuredWorker, accidentId:Long ) = InjuredWorkerRecord(
      id = iw.id,
      accidentId = accidentId,
      name = iw.name,
      age = iw.age,
      citizenship = iw.citizenship.map(_.id),
      industry = iw.industry.map(_.id),
      employer = iw.employer.map(_.id),
      from= iw.from,
      injuryCause= iw.injuryCause.map(_.id),
      injurySeverity= iw.injurySeverity.map(_.id),
      injuryDescription= iw.injuryDescription,
      publicRemarks= iw.publicRemarks,
      sensitiveRemarks= iw.sensitiveRemarks
    )
  
  implicit final def helpersSlickGetResultLocalDateTime: GetResult[LocalDateTime] =
    GetResult(r => r.nextTimestamp().toLocalDateTime)
}
