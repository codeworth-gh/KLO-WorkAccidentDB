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

case class BusinessEntityStats(
  id:   Long,
  name: String,
  isKnownContractor: Boolean,
  accidentCount: Long,
  killedCount:   Long,
  injuredCount:  Long,
  safetyViolationSanctionCount: Long
)

case class ExecutorCountRow(name:String, count:Int)
case class ExecutorCountPerYearRow(name:String, year:Int, count:Int)
case class CountByCategoryAndYear( category:String, year:Int, count:Int )

case class EntityMergeLogEntry(
                              mergeId: UUID,
                              table: String,
                              message: String
                              )