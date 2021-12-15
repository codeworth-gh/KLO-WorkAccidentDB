package models

import java.time.{LocalDate, LocalDateTime, LocalTime}

case class WorkAccident(
                       id: Long,
                       when: LocalDateTime,
                       relatedEntities:Set[(RelationToAccident, BusinessEntity)],
                       location: String,
                       region: Option[Region],
                       blogPostUrl: String,
                       details: String,
                       investigation:String,
                       initialSource:String,
                       mediaReports:Set[String],
                       publicRemarks:String,
                       sensitiveRemarks:String,
                       injured:Set[InjuredWorker],
                       requiresUpdate:Boolean
){
  def addWorkers( iws:Set[InjuredWorker] ) = copy(injured=injured++iws)
  def hasTime = (when.getHour|when.getMinute)>0
}

case class InjuredWorker(
                        id:Long,
                        name:String,
                        age:Option[Int],
                        citizenship:Option[Citizenship],
                        industry: Option[Industry],
                        employer: Option[BusinessEntity],
                        from:String,
                        injuryCause: Option[InjuryCause],
                        injurySeverity: Option[Severity.Value],
                        injuryDescription: String,
                        publicRemarks:String,
                        sensitiveRemarks:String
                        )

case class InjuredWorkerRow(worker:InjuredWorker, accidentId:Long, accidentDate:LocalDate)

case class BusinessEntitySummary(
  id: Long,
  name: String
)

case class WorkAccidentSummary(
  id:Long, dateTime:LocalDateTime,
  relateds:Set[(RelationToAccident, BusinessEntitySummary)],
  regionId: Option[Int], location:String,
  details:String, investigation:String,
  injuredCount:Int, killedCount:Int,
  requiresUpdate:Boolean
){
  def date:LocalDate=dateTime.toLocalDate
  lazy val hasTime = (dateTime.getHour|dateTime.getMinute)>0
  
  def time:Option[LocalTime]= {
    if ( hasTime )
      Some(dateTime.toLocalTime)
    else
      None
  }
}

