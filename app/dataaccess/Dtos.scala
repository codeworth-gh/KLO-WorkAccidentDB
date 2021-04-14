package dataaccess


import models.{BusinessEntitySummary, RelationToAccident, WorkAccidentSummary}

import java.time.{LocalDate, LocalDateTime, LocalTime}

// Data Transfer objects for the DB layer

case class WorkAccidentRecord(
                         id: Long,
                         when: LocalDateTime,
                         location: String,
                         regionId: Option[Int],
                         blogPostUrl: String,
                         details: String,
                         investigation:String,
                         initialSource:String,
                         mediaReports:String,
                         publicRemarks:String,
                         sensitiveRemarks:String
                       )

case class WorkAccidentSummaryRecord(
  id:Long, dateTime:LocalDateTime,
  regionId: Option[Int], location:String,
  details:String, investigation:String,
  injuredCount:Int, killedCount:Int
) {
  def toObject( relateds:Set[(RelationToAccident, BusinessEntitySummary)] ):WorkAccidentSummary={
    WorkAccidentSummary(id,dateTime, relateds, regionId, location, details, investigation, injuredCount, killedCount)
  }
}

case class InjuredWorkerRecord(
                          id:Long,
                          accidentId:Long,
                          name:String,
                          age:Option[Int],
                          citizenship:Option[Int],
                          industry: Option[Int],
                          employer: Option[Long],
                          from:String,
                          injuryCause: Option[Int],
                          injurySeverity: Option[Int],
                          injuryDescription: String,
                          publicRemarks:String,
                          sensitiveRemarks:String
                        )

case class RelationToAccidentRecord(
                                   accidentId: Long,
                                   relationTypeId: Int,
                                   businessEntityId: Long
                                   )