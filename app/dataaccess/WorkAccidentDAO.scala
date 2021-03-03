package dataaccess

import models.{BusinessEntity, InjuredWorker, Severity, WorkAccident, WorkAccidentSummary}
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class WorkAccidentDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                 citizenships: CitizenshipsDAO, regions: RegionsDAO,
                                 industries: IndustriesDAO, injuryCauses: InjuryCausesDAO
                                )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val workAccidents = TableQuery[WorkAccidentsTable]
  private val injuredWorkers = TableQuery[InjuredWorkersTable]
  private val businessEntities = TableQuery[BusinessEntityTable]
  private val workAccidentSummaries = TableQuery[WorkAccidentSummaryTable]
  private val accidentsAndEntrepreneurs = workAccidents.joinLeft(businessEntities).on( (a,b)=> a.entrepreneur_id === b.id )
  private val workersAndEmployers = injuredWorkers.joinLeft(businessEntities).on( (w,e)=>w.employer_id === e.id )
  
  private val log = Logger(classOf[WorkAccidentDAO])
  
  def store( iw:InjuredWorker, accidentId:Long ):Future[InjuredWorker] = {
    db.run(
      (injuredWorkers returning injuredWorkers.map(_.id)).into((_, newId) => iw.copy(id=newId)).insertOrUpdate( toDto(iw, accidentId) )
    ).map( _.getOrElse(iw) )
  }
  
  def store( wa:WorkAccident ):Future[WorkAccident] = {
    val waRow = toDto(wa)
    
    for {
      waIn <- db.run((workAccidents returning workAccidents.map(_.id)).into((_, newId)=>waRow.copy(id=newId)).insertOrUpdate(waRow))
      accId = waIn.getOrElse(waRow).id
      _    <- db.run(injuredWorkers.filter(_.id inSet wa.injured.map(_.id)).delete ) // remove old records
      iwIn <- Future.sequence( wa.injured.map( store(_, accId)) ) // easy, as all are new now
    } yield {
      wa.copy( id=accId, injured = iwIn )
    }
  }
  
  def getAccident( id:Long ):Future[Option[WorkAccident]] = {
    for {
      waRowOpt <- db.run( accidentsAndEntrepreneurs.filter(_._1.id===id).result ).map( _.headOption )
      iwRows <- workersInAccident(id)
    } yield {
      waRowOpt.map( row => fromDto(row._1, iwRows.toSet, row._2))
    }
  }
  
  def listAccidents(start:Int, pageSize:Int):Future[Seq[WorkAccidentSummary]] = {
    db.run(
      workAccidentSummaries.drop(start).take(pageSize).sortBy(_.dateTime.desc).result
    )
  }
  
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
  
  def deleteWorker( id:Long ):Future[Try[Int]] = db.run(
    injuredWorkers.filter(_.id===id).delete.asTry
  )
  
  def listWorkers():Future[Seq[InjuredWorker]] = {
    for {
      iws <- db.run(injuredWorkers.sortBy(_.injury_severity.desc).result )
      ems <- db.run( businessEntities.result )
    } yield {
      val emsMap:Map[Long, BusinessEntity] = ems.map(e=>e.id->e).toMap
      iws.map( iwr=>fromDto(iwr, iwr.employer.flatMap(id=>emsMap.get(id))) )
    }
    
  }
  
  private def fromDto(iwRow:InjuredWorkerRecord, employer:Option[BusinessEntity]) = InjuredWorker( iwRow.id, iwRow.name, iwRow.age, iwRow.citizenship.flatMap(citizenships(_)),
    iwRow.industry.flatMap(industries(_)), employer, iwRow.from, iwRow.injuryCause.flatMap(injuryCauses(_)),
    iwRow.injurySeverity.map( Severity.apply ), iwRow.injuryDescription, iwRow.publicRemarks, iwRow.sensitiveRemarks
  )
  
  private def fromDto( waRow:WorkAccidentRecord, iws:Set[InjuredWorker], entrepreneur:Option[BusinessEntity] ) = WorkAccident(
    waRow.id, waRow.when, entrepreneur, waRow.location, waRow.regionId.flatMap( regions.apply ), waRow.blogPostUrl, waRow.details,
    waRow.investigation, waRow.initialSource, waRow.mediaReports.split("\n").toSet, waRow.publicRemarks, waRow.sensitiveRemarks, iws
  )
  
  private def toDto( wa:WorkAccident ) = WorkAccidentRecord(
      wa.id, wa.when, entrepreneurId = wa.entrepreneur.map(_.id),
      location = wa.location,
      regionId = wa.region.map(_.id),
      blogPostUrl = wa.blogPostUrl,
      details = wa.details,
      investigation = wa.investigation,
      initialSource = wa.initialSource,
      mediaReports = wa.mediaReports.mkString("\n"),
      publicRemarks = wa.publicRemarks,
      sensitiveRemarks = wa.sensitiveRemarks
  )
  
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
  
}
