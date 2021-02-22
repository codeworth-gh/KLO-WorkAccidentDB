package dataaccess

import models.{BusinessEntity, Citizenship, Industry, InjuredWorker, InjuryCause, Region, Severity, WorkAccident}

import java.time.LocalDateTime
import scala.xml.dtd.ContentModelParser

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

//object WorkAccidentRecord {
//  def encode(wa:WorkAccident):WorkAccidentRecord=WorkAccidentRecord(
//      wa.id, wa.when, entrepreneurId = wa.entrepreneur.map(_.id),
//      regionId = wa.region.map(_.id),
//      blogPostUrl = wa.blogPostUrl,
//      details = wa.details,
//      investigation = wa.investigation,
//      mediaReports = wa.mediaReports.mkString("\n"),
//      publicRemarks = wa.publicRemarks,
//      sensitiveRemarks = wa.sensitiveRemarks
//  )
//}

case class InjuredWorkerRecord(
                          id:Long,
                          accidentId:Long,
                          name:String,
                          age:Option[Int],
                          citizenship:Option[Int],
                          industry: Option[Int],
                          from:String,
                          injuryCause: Option[Int],
                          injurySeverity: Option[String],
                          injuryDescription: String,
                          publicRemarks:String,
                          sensitiveRemarks:String
                        )

//object InjuredWorkerRecord {
//  def from(iw:InjuredWorker, accidentId:Long):InjuredWorkerRecord=InjuredWorkerRecord(
//    id = iw.id,
//    accidentId = accidentId,
//    name = iw.name,
//    age = iw.age,
//    citizenship = iw.citizenship.map(_.id),
//    industry = iw.industry.map(_.id),
//    from= iw.from,
//    injuryCause= iw.injuryCause.map(_.id),
//    injurySeverity= iw.injurySeverity.map(_.toString),
//    injuryDescription= iw.injuryDescription,
//    publicRemarks= iw.publicRemarks,
//    sensitiveRemarks= iw.sensitiveRemarks
//  )
//}