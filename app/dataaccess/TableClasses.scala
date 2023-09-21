package dataaccess

import java.sql.Timestamp
import models.{BusinessEntity, BusinessEntityMapping, BusinessEntityStats, BusinessEntitySummary, Citizenship, CountByCategoryAndYear, ExecutorCountPerYearRow, ExecutorCountRow, Industry, IndustryMapping, InjuryCause, Invitation, PasswordResetRequest, Region, RelationToAccident, SafetyWarrant, Sanction, User, WorkAccidentSummary}
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._

import java.time.{LocalDate, LocalDateTime}
import scala.reflect.ClassTag

class SettingsTable(t:Tag) extends Table[Setting](t,"settings"){
  
  def key = column[String]("name", O.PrimaryKey)
  def value = column[String]("value")
  
  def * = (key, value) <> (
    rec => Setting( SettingKey.withName(rec._1), rec._2 ),
    (stg:Setting) => Some((stg.key.toString, stg.value))
  )
}

class UsersTable(tag:Tag) extends Table[User](tag,"users") {

  def id                = column[Long]("id",O.PrimaryKey, O.AutoInc)
  def username          = column[String]("username")
  def name              = column[String]("name")
  def email             = column[String]("email")
  def encryptedPassword = column[String]("encrypted_password")
  def isAdmin           = column[Boolean]("is_admin")

  def * = (id, username, name, email, encryptedPassword, isAdmin) <> (User.tupled, User.unapply)

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
  def isKnownContractor = column[Boolean]("is_known_contractor")
  def memo = column[Option[String]]("memo")
  
  def * = (
    id, name, phone, email, website, isPrivatePerson, isKnownContractor, memo
  ) <> (BusinessEntity.tupled, BusinessEntity.unapply)
  
  def nameIdx = index("business_entities_name", name)
}

class BusinessEntityStatsTable(tag:Tag) extends Table[BusinessEntityStats](tag, "bizent_accident_stats") {
  def id = column[Long]("id")
  def name = column[String]("name")
  def isKnownContractor = column[Boolean]("is_known_contractor")
  def accCnt = column[Long]("accident_count")
  def kldCnt = column[Long]("killed_count")
  def injCnt = column[Long]("injured_count")
  
  def * = (id, name, isKnownContractor, accCnt, kldCnt, injCnt) <> (BusinessEntityStats.tupled, BusinessEntityStats.unapply)
}

class BusinessEntitySummaryTable(tag:Tag) extends Table[BusinessEntitySummary](tag, "business_entities") {
  def id = column[Long]("id")
  def name = column[String]("name")
  def isKnownContractor = column[Boolean]("is_known_contractor")
  
  def * = (id, name, isKnownContractor) <> (BusinessEntitySummary.tupled, BusinessEntitySummary.unapply)
  
}

class SanctionTable(t:Tag) extends Table[Sanction](t, "sanctions"){
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def businessEntityId = column[Long]("business_entity_id")
  def authority = column[String]("authority")
  def sanctionType = column[String]("sanction_type")
  def reason = column[String]("reason")
  def applicationDate = column[LocalDate]("application_date")
  def remarks = column[String]("remarks")
  
  def * = (id, businessEntityId, authority, sanctionType, reason, applicationDate, remarks) <> (Sanction.tupled, Sanction.unapply)
  
  def fkBizEnt = foreignKey("fk_wa_ent", businessEntityId, TableRefs.businessEntities)(_.id)
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
  def blogPostUrl = column[String]("blog_post_url")
  def details = column[String]("details")
  def investigation = column[String]("investigation")
  def initialSource = column[String]("initial_source")
  def mediaReports = column[String]("media_reports")
  def publicRemarks = column[String]("public_remarks")
  def sensitiveRemarks = column[String]("sensitive_remarks")
  def requiresUpdate = column[Boolean]("requires_update")
  def officiallyRecognized = column[Option[Boolean]]("officially_recognized")
  
  def * = (id, date_time, location, regionId, blogPostUrl,
    details, investigation, initialSource, mediaReports, publicRemarks,
    sensitiveRemarks, requiresUpdate, officiallyRecognized
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

class WorkAccidentSummaryTable(t:Tag) extends Table[WorkAccidentSummaryRecord](t,"work_accident_summary2"){
  def id       = column[Long]("id")
  def dateTime = column[LocalDateTime]("date_time")
  def regionId = column[Option[Int]]("region_id")
  def location = column[String]("location")
  def details  = column[String]("details")
  def investigation = column[String]("investigation")
  def injuredCount  = column[Int]("injured_count")
  def killedCount   = column[Int]("killed_count")
  def requiresUpdate = column[Boolean]("requires_update")
  def officiallyRecognized = column[Option[Boolean]]("officially_recognized")
  
  def * = (id, dateTime, regionId, location,
           details, investigation, injuredCount,
            killedCount, requiresUpdate, officiallyRecognized)<>(WorkAccidentSummaryRecord.tupled, WorkAccidentSummaryRecord.unapply)
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

abstract class BaseSafetyWarrantsTable(t:Tag, tableName:String) extends Table[SafetyWarrant](t,tableName){
  
  def id             = column[Long]("id", O.PrimaryKey)
  def sentDate       = column[LocalDate]("sent_date")
  def operatorTextId = column[String]("operator_text_id")
  def operatorName   = column[String]("operator_name")
  def cityName       = column[String]("city_name")
  def executorName   = column[String]("executor_name")
  def categoryName   = column[String]("category_name")
  def felony         = column[String]("felony")
  def law            = column[String]("law")
  def clause         = column[String]("clause")
  def scrapeDate     = column[LocalDateTime]("scrape_date")
  def kloOperatorId  = column[Option[Long]]("klo_operator_id")
  def kloExecutorId  = column[Option[Long]]("klo_executor_id")
  def kloIndustryId  = column[Option[Int]]("klo_industry_id")

  def * = (id, sentDate, operatorTextId, operatorName, cityName, executorName, categoryName,
    felony, law, clause, scrapeDate, kloOperatorId, kloExecutorId, kloIndustryId
   ) <> (SafetyWarrant.tupled, SafetyWarrant.unapply)
  
  def fkComp = foreignKey("fk_operator_id", kloOperatorId, TableRefs.businessEntities)(_.id)
  def fkExec = foreignKey("fk_executor_id", kloExecutorId, TableRefs.businessEntities)(_.id)
  def fkInds = foreignKey("fk_industry_id", kloIndustryId, TableRefs.industries)(_.id)
}

class SafetyWarrantsTable(t:Tag) extends BaseSafetyWarrantsTable(t, "safety_warrants")
class RawSafetyWarrantsTable(t:Tag) extends BaseSafetyWarrantsTable(t, "safety_warrants_raw")

abstract class NameAndCountTable(t:Tag, tableName:String) extends Table[(String, Int)](t, tableName) {
  def name = column[String]("name")
  def count = column[Int]("count")
  
  def * = (name, count)
}

class ExecutorsWithOver4In24(t:Tag) extends NameAndCountTable(t, "executors_with_4_plus_24mo")
class SafetyWarrantByCategoryAll(t:Tag) extends NameAndCountTable(t, "safety_warrant_by_category_all")
class SafetyWarrantByCategory24Mo(t:Tag) extends NameAndCountTable(t, "safety_warrant_by_category_24mo")
class SafetyWarrantByLaw(t:Tag) extends NameAndCountTable(t, "safety_warrant_by_law")

class BusinessEntityMappingTable(t:Tag) extends Table[BusinessEntityMapping](t,"business_entity_mapping") {
  def id = column[Long]("id", O.PrimaryKey)
  def name = column[String]("name")
  def bizEntityId = column[Long]("biz_entity_id")
  
  def * = (id, name, bizEntityId) <> (BusinessEntityMapping.tupled, BusinessEntityMapping.unapply )
  
  def fkBizEnt = foreignKey("fk_be_id", bizEntityId, TableRefs.businessEntities)(_.id)
}

class IndustryMappingTable(t:Tag) extends Table[IndustryMapping](t,"industry_mapping") {
  def id = column[Long]("id", O.PrimaryKey)
  def name = column[String]("name")
  def industryId = column[Int]("industry_id")
  
  def * = (id, name, industryId) <> (IndustryMapping.tupled, IndustryMapping.unapply )
  
  def fkBizEnt = foreignKey("fk_be_id", industryId, TableRefs.industries)(_.id)
}

class SWWorst20Table(t:Tag) extends Table[ExecutorCountRow](t, "safety_warrants_top_20_executors") {
  def execName = column[String]("executor_name")
  def count    = column[Int]("count")
  
  def * = (execName, count)<> (ExecutorCountRow.tupled, ExecutorCountRow.unapply)
}

class SWOver10After201820Table(t:Tag) extends Table[ExecutorCountRow](t, "safety_warrant_over_10_after_2018") {
  def execName = column[String]("executor_name")
  def count    = column[Int]("count")
  
  def * = (execName, count)<> (ExecutorCountRow.tupled, ExecutorCountRow.unapply)
}

class SWPerExecutorPerYear(t:Tag) extends Table[ExecutorCountPerYearRow](t, "safety_warrants_per_executor_per_year") {
  def execName = column[String]("executor_name")
  def year     = column[Int]("year")
  def count    = column[Int]("count")
  
  def * = (execName, year, count)<> (ExecutorCountPerYearRow.tupled, ExecutorCountPerYearRow.unapply)
}

class SWPerCategoryPerYear(t:Tag) extends Table[CountByCategoryAndYear](t, "safety_warrant_by_category_and_year") {
  def name  = column[String]("name")
  def year  = column[Int]("year")
  def count = column[Int]("count")
  
  def * = (name, year, count)<> (CountByCategoryAndYear.tupled, CountByCategoryAndYear.unapply)
}

class SWPerExecutor(t:Tag) extends Table[ExecutorCountRow](t, "safety_warrants_per_executor") {
  def execName = column[String]("executor_name")
  def count    = column[Int]("count")
  
  def * = (execName, count)<> (ExecutorCountRow.tupled, ExecutorCountRow.unapply)
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