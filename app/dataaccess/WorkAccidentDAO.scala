package dataaccess

import models.{BusinessEntity, InjuredWorker, Severity, WorkAccident}
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class WorkAccidentDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider,
                                 citizenships: CitizenshipsDAO, regions: RegionsDAO,
                                 industries: IndustriesDAO, injuryCauses: InjuryCausesDAO,
                                 businesses: BusinessEntityDAO
                                )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val workAccidents = TableQuery[WorkAccidentsTable]
  private val injuredWorkers = TableQuery[InjuredWorkersTable]
  private val businessEntities = TableQuery[BusinessEntityTable]
  private val accidentsAndEntrepreneurs = workAccidents.joinLeft(businessEntities).on( (a,b)=> a.entrepreneur_id === b.id )
  
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
      iwIn <- Future.sequence( wa.injured.map( store(_, accId)) )
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
  
//  def listAccidents(start:Int, pageSize:Int):Future[Seq[WorkAccident]] = {
//    db.run(
//      accidentsAndEntrepreneurs.drop(start).take(pageSize).result
//    ).map( )
//  }
  
  def getInjuredWorker( id:Long ):Future[Option[InjuredWorker]] = {
    for {
      res <- db.run(injuredWorkers.filter(_.id===id).result.headOption)
    } yield {
      res.map( fromDto )
    }
  }
  
  def workersInAccident(accId:Long):Future[Seq[InjuredWorker]] = db.run(
    injuredWorkers.filter( _.accident_id === accId ).sortBy(_.name).result
  ).map( _.map(fromDto) )
  
  def deleteWorker( id:Long ):Future[Try[Int]] = db.run(
    injuredWorkers.filter(_.id===id).delete.asTry
  )
  
  def listWorkers():Future[Seq[InjuredWorker]] = db.run(
    injuredWorkers.sortBy(_.injury_severity.desc).result
  ).map( _.map(fromDto) )
  
  private def fromDto(iwRow:InjuredWorkerRecord) = InjuredWorker( iwRow.id, iwRow.name, iwRow.age, iwRow.citizenship.flatMap(citizenships(_)),
    iwRow.industry.flatMap(industries(_)), iwRow.from, iwRow.injuryCause.flatMap(injuryCauses(_)),
    iwRow.injurySeverity.map( Severity.apply ), iwRow.injuryDescription, iwRow.publicRemarks, iwRow.sensitiveRemarks
  )
  
  private def fromDto( waRow:WorkAccidentRecord, iws:Set[InjuredWorker], entrepreneur:Option[BusinessEntity] ) = WorkAccident(
    waRow.id, waRow.when, entrepreneur, waRow.regionId.flatMap( regions.apply ), waRow.blogPostUrl, waRow.details,
    waRow.investigation, waRow.mediaReports.split("\n").toSet, waRow.publicRemarks, waRow.sensitiveRemarks, iws
  )
  
  private def toDto( wa:WorkAccident ) = WorkAccidentRecord(
      wa.id, wa.when, entrepreneurId = wa.entrepreneur.map(_.id),
      regionId = wa.region.map(_.id),
      blogPostUrl = wa.blogPostUrl,
      details = wa.details,
      investigation = wa.investigation,
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
      from= iw.from,
      injuryCause= iw.injuryCause.map(_.id),
      injurySeverity= iw.injurySeverity.map(_.id),
      injuryDescription= iw.injuryDescription,
      publicRemarks= iw.publicRemarks,
      sensitiveRemarks= iw.sensitiveRemarks
    )
  
}
