package dataaccess


import java.time.LocalDateTime

// Data Transfer objects for the DB layer

case class WorkAccidentRecord(
                         id: Long,
                         when: LocalDateTime,
                         entrepreneurId:Option[Long],
                         regionId: Option[Int],
                         blogPostUrl: String,
                         details: String,
                         investigation:String,
                         mediaReports:String,
                         publicRemarks:String,
                         sensitiveRemarks:String
                       )

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

