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

case class InjuredWorkerRecord(
                          id:Long,
                          accidentId:Long,
                          name:String,
                          age:Option[Int],
                          citizenship:Option[Int],
                          industry: Option[Int],
                          from:String,
                          injuryCause: Option[Int],
                          injurySeverity: Option[Int],
                          injuryDescription: String,
                          publicRemarks:String,
                          sensitiveRemarks:String
                        )

