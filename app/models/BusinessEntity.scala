package models

import java.util.UUID

/**
 * An employer or a private individual related to accidents, e.g. as the employer of
 * the injured worker, or as the entrepreneur of the project in which the accident happened.
 */
case class BusinessEntity(
  id:   Long,
  name: String,
  /** Private Company number (מספר ח.פ.) */
  pcNumber: Option[Long],
  phone:   Option[String],
  email:   Option[String],
  website: Option[String],
  isPrivatePerson: Boolean,
  isKnownContractor: Boolean,
  memo: Option[String]
)
object BusinessEntity {
  def unapply(b:BusinessEntity):Option[(Long, String, Option[Long],Option[String],Option[String],Option[String],Boolean,Boolean,Option[String])] = Some(
    b.id, b.name, b.pcNumber, b.phone, b.email, b.website, b.isPrivatePerson, b.isKnownContractor, b.memo
  )
}

case class BusinessEntityStats(
  id:   Long,
  name: String,
  isKnownContractor: Boolean,
  accidentCount: Long,
  killedCount:   Long,
  injuredCount:  Long,
  safetyViolationSanctionCount: Long
)

object BusinessEntityStats {
  def unapply(b:BusinessEntityStats):Option[(Long, String, Boolean, Long, Long, Long, Long)] = Some(
    b.id, b.name, b.isKnownContractor, b.accidentCount, b.killedCount, b.injuredCount, b.safetyViolationSanctionCount
  )
}

case class ExecutorCountRow(name:String, count:Int)
case class ExecutorCountPerYearRow(name:String, year:Int, count:Int)
case class CountByCategoryAndYear( category:Option[String], year:Option[Int], count:Int )

case class EntityMergeLogEntry(
                              mergeId: UUID,
                              table: String,
                              message: String
                              )