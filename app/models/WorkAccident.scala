package models

import java.time.{LocalDate, LocalDateTime, LocalTime}

case class WorkAccident(
                       id: Long,
                       when: LocalDateTime,
                       entrepreneur:Option[BusinessEntity],
                       location: String,
                       region: Option[Region],
                       blogPostUrl: String,
                       details: String,
                       investigation:String,
                       initialSource:String,
                       mediaReports:Set[String],
                       publicRemarks:String,
                       sensitiveRemarks:String,
                       injured:Set[InjuredWorker]
){
  def addWorkers( iws:Set[InjuredWorker] ) = copy(injured=injured++iws)
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

case class WorkAccidentSummary(
  id:Long, dateTime:LocalDateTime,
  entrepreneurId:Option[Long], entrepreneurName:Option[String],
  regionId: Option[Int],
  details:String, investigation:String,
  injuredCount:Int, killedCount:Int
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

