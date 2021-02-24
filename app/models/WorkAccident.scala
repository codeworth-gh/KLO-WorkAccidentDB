package models

import java.time.LocalDateTime

case class WorkAccident(
                       id: Long,
                       when: LocalDateTime,
                       entrepreneur:Option[BusinessEntity],
                       region: Option[Region],
                       blogPostUrl: String,
                       details: String,
                       investigation:String,
                       mediaReports:Set[String],
                       publicRemarks:String,
                       sensitiveRemarks:String,
                       injured:Set[InjuredWorker]
)

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

