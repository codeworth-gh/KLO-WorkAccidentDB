package dataaccess

import java.sql.Timestamp
import models.{BusinessEntity, BusinessEntitySummary, Citizenship, Industry, InjuryCause, Invitation, PasswordResetRequest, Region, RelationToAccident, User, WorkAccidentSummary}
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._

import java.time.LocalDateTime
import scala.reflect.ClassTag

class UsersTable(tag:Tag) extends Table[User](tag,"users") {

  def id                = column[Long]("id",O.PrimaryKey, O.AutoInc)
  def username          = column[String]("username")
  def name              = column[String]("name")
  def email             = column[String]("email")
  def encryptedPassword = column[String]("encrypted_password")

  def * = (id, username, name, email, encryptedPassword) <> (User.tupled, User.unapply)

}

class InvitationsTable(tag:Tag) extends Table[Invitation](tag, "invitations") {
  def email = column[String]("email", O.PrimaryKey)
  def date  = column[Timestamp]("date")
  def uuid  = column[String]("uuid")
  def sender  = column[String]("sender",O.PrimaryKey)

//  def pk = primaryKey("invitation_pkey", (email, sender))

  def * = (email, date, uuid, sender) <> (Invitation.tupled, Invitation.unapply)
}

class PasswordResetRequestsTable(tag:Tag) extends Table[PasswordResetRequest](tag, "password_reset_requests"){
  def username = column[String]("username")
  def uuid     = column[String]("uuid")
  def reset_password_date = column[Timestamp]("reset_password_date")

  def pk = primaryKey("uuid_for_forgot_password_pkey", (username, uuid))

  def * = (username, uuid, reset_password_date) <> (PasswordResetRequest.tupled, PasswordResetRequest.unapply)
}

class BusinessEntityTable(tag:Tag) extends Table[BusinessEntity](tag, "business_entities") {
  def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
  def name = column[String]("name")
  def phone = column[Option[String]]("phone")
  def email = column[Option[String]]("email")
  def website = column[Option[String]]("website")
  def isPrivatePerson = column[Boolean]("is_private_person")
  def memo = column[Option[String]]("memo")
  
  def * = (
    id, name, phone, email, website, isPrivatePerson, memo
  ) <> (BusinessEntity.tupled, BusinessEntity.unapply)
  
  def nameIdx = index("business_entities_name", name)
}

class BusinessEntitySummaryTable(tag:Tag) extends Table[BusinessEntitySummary](tag, "business_entities") {
  def id = column[Long]("id")
  def name = column[String]("name")
  
  def * = (id, name) <> (BusinessEntitySummary.tupled, BusinessEntitySummary.unapply)
  
}


class IdNameTable[T](tag: Tag, tableName: String, apply: (Int, String) => T, unapply: T => Option[(Int, String)])
                    (implicit classTag: ClassTag[T]) extends Table[T](tag, tableName) {
  def id = column[Int]("id", O.AutoInc, O.PrimaryKey)
  def name = column[String]("name", O.Unique)
  def * = (id, name) <> (apply.tupled, unapply)
}

class RegionsTable(tag: Tag) extends IdNameTable[Region](tag, "regions", Region.apply, Region.unapply)

class CitizenshipsTable(tag:Tag) extends IdNameTable[Citizenship](tag, "citizenships", Citizenship, Citizenship.unapply)

class IndustriesTable(tag:Tag) extends IdNameTable[Industry](tag, "industries", Industry, Industry.unapply)

class InjuryCausesTable(tag:Tag) extends IdNameTable[InjuryCause](tag, "injury_causes", InjuryCause, InjuryCause.unapply)

class RelationToAccidentTable(tag:Tag) extends IdNameTable[RelationToAccident](tag, "bizent_accident_relation_type", RelationToAccident, RelationToAccident.unapply)

class WorkAccidentsTable(t:Tag) extends Table[WorkAccidentRecord](t, "work_accidents") {
  
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def date_time = column[LocalDateTime]("date_time")
  def location = column[String]("location")
  def regionId = column[Option[Int]]("region_id")
  def blog_post_url = column[String]("blog_post_url")
  def details = column[String]("details")
  def investigation = column[String]("investigation")
  def initialSource = column[String]("initial_source")
  def mediaReports = column[String]("media_reports")
  def public_remarks = column[String]("public_remarks")
  def sensitive_remarks = column[String]("sensitive_remarks")
  
  def * = (id, date_time, location, regionId, blog_post_url, details, investigation, initialSource, mediaReports, public_remarks, sensitive_remarks
  )<>(WorkAccidentRecord.tupled, WorkAccidentRecord.unapply)
  
  def fkRgn = foreignKey("fk_wa_rgn", regionId, TableRefs.regions)(_.id.?)
}

class InjuredWorkersTable(t:Tag) extends Table[InjuredWorkerRecord](t, "injured_workers") {
  
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accident_id = column[Long]("accident_id")
  def name = column[String]("name")
  def age = column[Option[Int]]("age")
  def citizenship_id = column[Option[Int]]("citizenship_id")
  def industry_id = column[Option[Int]]("industry_id")
  def employer_id = column[Option[Long]]("employer_id")
  def from_place = column[String]("from_place")
  def injury_cause_id = column[Option[Int]]("injury_cause_id")
  def injury_severity = column[Option[Int]]("injury_severity")
  def injury_description = column[String]("injury_description")
  def public_remarks = column[String]("public_remarks")
  def sensitive_remarks = column[String]("sensitive_remarks")
  
  def * = (id, accident_id, name, age, citizenship_id, industry_id, employer_id, from_place, injury_cause_id, injury_severity,
    injury_description, public_remarks, sensitive_remarks)<>(InjuredWorkerRecord.tupled, InjuredWorkerRecord.unapply)
  
  def fkAcc = foreignKey("fk_iw_wa", accident_id, TableRefs.accidents)(_.id)
  def fkCtz = foreignKey("fk_iw_cz", citizenship_id, TableRefs.citizenships)(_.id.?)
  def fkBnt = foreignKey("fk_iw_be", employer_id, TableRefs.businessEntities)(_.id.?)
  def fkInd = foreignKey("fk_iw_in", industry_id, TableRefs.industries)(_.id.?)
  def fkICs = foreignKey("fk_iw_ic", injury_cause_id, TableRefs.injuryCauses)(_.id.?)
}

class WorkAccidentSummaryTable(t:Tag) extends Table[WorkAccidentSummaryRecord](t,"work_accident_summary"){
  def id       = column[Long]("id")
  def dateTime = column[LocalDateTime]("date_time")
  def regionId = column[Option[Int]]("region_id")
  def location = column[String]("location")
  def details  = column[String]("details")
  def investigation = column[String]("investigation")
  def injuredCount  = column[Int]("injured_count")
  def killedCount   = column[Int]("killed_count")
  
  def * = (id, dateTime, regionId, location,
           details, investigation,
           injuredCount, killedCount)<>(WorkAccidentSummaryRecord.tupled, WorkAccidentSummaryRecord.unapply)
}

class AccidentToBusinessEntityTable(t:Tag) extends Table[RelationToAccidentRecord](t, "bart_accident"){
  def accidentId = column[Long]("accident_id")
  def relationId = column[Int]("bart_id")
  def bizEntId   = column[Long]("business_entity_id")
  
  def * = (accidentId, relationId, bizEntId) <> (RelationToAccidentRecord.tupled, RelationToAccidentRecord.unapply )
  
  def fkAcc = foreignKey("bart_accident_accident_id_fkey", accidentId, TableRefs.accidents)(_.id)
  def fkRel = foreignKey("bart_accident_bart_id_fkey", relationId, TableRefs.relationsToAccidents)(_.id)
  def fkBiz = foreignKey("bart_accident_business_entity_id_fkey", bizEntId, TableRefs.businessEntities)(_.id)
}

object TableRefs {
  val businessEntities = TableQuery[BusinessEntityTable]
  val regions = TableQuery[RegionsTable]
  val accidents = TableQuery[WorkAccidentsTable]
  val citizenships = TableQuery[CitizenshipsTable]
  val industries = TableQuery[IndustriesTable]
  val injuryCauses = TableQuery[InjuryCausesTable]
  val relationsToAccidents = TableQuery[RelationToAccidentTable]
}