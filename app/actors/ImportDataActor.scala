package actors

import akka.actor.{Actor, Props}
import dataaccess.{BusinessEntityDAO, CitizenshipsDAO, IndustriesDAO, InjuryCausesDAO, RegionsDAO, WorkAccidentDAO}
import models.{BusinessEntity, Citizenship, Industry, InjuredWorker, InjuryCause, Region, Severity, WorkAccident}
import play.api.Logger

import java.nio.file.Path
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.util.{Failure, Success, Using}

object ImportDataActor {
  def props = Props[ImportDataActor]()
  case class ImportFile(path:Path)
  
  object FieldNums {
    val DATE=0
    val TIME=2
    val NAME=3
    val AGE=4
    val CITIZENSHIP=5
    val FROM=6
    val LOCATION=7
    val INDUSTRY=8
    val EMPLOYER=9
    val INJURY_CAUSE=10
    val ACCIDENT_DETAILS=11
    val SEVERITY=12
    val INJURY_DETAILS=13
    val REGION=14
    val INVESTIGATION=15
    val SOURCE=16
    val REMARKS=17
  }
}

class ImportDataActor @Inject() (businessEnts:BusinessEntityDAO, regions:RegionsDAO,
  accidents:WorkAccidentDAO, citizenships:CitizenshipsDAO, injuryCauses: InjuryCausesDAO, industries:IndustriesDAO
                                )(implicit anEc:ExecutionContext) extends Actor {
  private val log = Logger(classOf[ImportDataActor])
  private val D = Duration(5, duration.MINUTES)
  private val ldtFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  
  private var citizenshipByName:Map[String, Citizenship]=null
  private var injuryCauseByName:Map[String, InjuryCause]=null
  private var industryByName:Map[String, Industry]=null
  private var regionByName:Map[String, Region]=null
  private var currentFile:String=null;
  
  override def receive: Receive = {
    case ImportDataActor.ImportFile(path) => {
      log.info(s"Importing file at $path")
      
      citizenshipByName = Await.result(citizenships.list(), D).map( c => c.name.replaceAll(" ","")->c ).toMap
      injuryCauseByName = Await.result(injuryCauses.list(), D).map( c => c.name.replaceAll(" ","")->c ).toMap
      industryByName = Await.result(industries.list(), D).map( c => c.name.replaceAll(" ","")->c ).toMap
      regionByName = Await.result(regions.list(), D).map( c => c.name.replaceAll(" ","")->c ).toMap
      currentFile = path.getFileName.toString
      
      val importCount = importFile(path)
      
      currentFile = null
      citizenshipByName = null
      injuryCauseByName = null
      industryByName = null
      regionByName = null
      
      log.info(s"Imported $importCount new accidents")
    }
  }
  
  private def importFile(inFile:Path):Int = {
    Using(scala.io.Source.fromFile(inFile.toFile)){ rdr =>
      rdr.getLines()
        .zipWithIndex
        .drop(1) // skip header
        .filter( _._1.trim.nonEmpty )
        .flatMap( parseLine ).toSeq
        .groupBy( wa=>(wa.when.toLocalDate, wa.entrepreneur, wa.location, wa.region) )
        .map( mergeAccidentRows )
        .map( wa => {
          wa.injured.size match {
            case 0 => log.warn( s"Accident with no workers: ${wa.when}/${wa.region}")
            case 1 => None // skip
            case _ => log.info( wa.toString )
          }
          wa
        })
        .map( wa => Await.result(accidents.store(wa), D) )
        .size
    } match {
      case Success(i) => i
      case Failure(e) => {
        log.warn("Import failure: " + e.getMessage, e)
        -1
      }
    }
  }
  
  private def parseLine( line:(String, Int) ):Option[WorkAccident] = {
    import ImportDataActor.FieldNums
    val comps = line._1.split("\t",-1).map(cleanField)
    try {
      val ctzn = canonize(comps(FieldNums.CITIZENSHIP), citizenshipByName)
      val inds = canonize(comps(FieldNums.INDUSTRY), industryByName)
      val incz = canonize(comps(FieldNums.INJURY_CAUSE), injuryCauseByName)
      val emp = getCreateBusinessEntity(comps(FieldNums.EMPLOYER) )
      val iw = InjuredWorker(0,
        comps(FieldNums.NAME), toInt(comps(FieldNums.AGE)),
        ctzn, inds, emp, comps(FieldNums.FROM), incz, severity(comps(FieldNums.SEVERITY)),
          comps(FieldNums.INJURY_DETAILS), "", s"Imported from $currentFile, line: ${line._2}")
      val wa = WorkAccident(0, dateTime(comps(FieldNums.DATE), comps(FieldNums.TIME)),
        getCreateBusinessEntity(comps(FieldNums.EMPLOYER)),
        comps(FieldNums.LOCATION), canonize(comps(FieldNums.REGION), regionByName),
        "", comps(FieldNums.ACCIDENT_DETAILS),comps(FieldNums.INVESTIGATION),
        comps(FieldNums.SOURCE), Set(), comps(FieldNums.REMARKS), "", Set(iw)
      )
      return Some(wa)
      
    } catch {
      case e:Exception => log.warn(s"Line ${line._2}: ${e.getMessage}")
      
    }
    None
  }
  
  private def mergeAccidentRows( group:(_, Seq[WorkAccident]) ):WorkAccident = {
    val template = group._2.head
    if ( group._2.size==1 ) {
      template
    } else {
      template.addWorkers( group._2.tail.flatMap(_.injured).toSet )
    }
  }
  
  private def getCreateBusinessEntity( name:String ):Option[BusinessEntity] = {
    name match {
      case "" => None
      case _  => Some(Await.result( businessEnts.findOrCreateNames(Set(name)), D)(name))
    }
  }
  
  private def toInt(v:String):Option[Int] = v match {
    case "" => None
    case s:String => Some(s.toInt)
  }
  
  private def severity(data:String):Option[Severity.Value] = {
    data match {
      case "" => None
      case "קל" => Some(Severity.light)
      case "בינוני" => Some(Severity.medium)
      case "קשה" => Some(Severity.severe)
      case "אנוש" => Some(Severity.nearFatal)
      case "הרוג" => Some(Severity.fatal)
      case _ => throw new Exception(s"Unknown severity '${data}'")
    }
  }
  
  private def canonize[C]( rawVal:String, index:Map[String,C] ):Option[C] = {
    if ( rawVal.isEmpty ) {
      None
    } else {
      index.get(rawVal.replaceAll(" ","")) match {
        case Some(c)=>Some(c)
        case None => throw new Exception(s"Unknown value $rawVal")
      }
    }
  }
  
  private def dateTime( date:String, time:String ):LocalDateTime = {
    val effTime = if (time.isBlank) "00:00" else time
    LocalDateTime.parse( date + " " + effTime, ldtFmt )
  }
  
  private def cleanField( f:String ): String = {
    if ( f.isBlank ) return ""
    var ff = f.trim
    if ( ff.head=='"' && ff.last=='"' ) {
      ff = ff.tail.dropRight(1)
      ff = ff.replaceAll("\"\"","\"")
      ff = ff.trim
    }
    ff
  }
  
}

