package models

import java.time.LocalDate

/**
 * A sanction imposed on a business entity due to a safety violation.
 * Normally scraped from data.gov.il.
 */
case class SafetyViolationSanction(
  /** ID in KLO's system  */
  id: Int,
  /** Sanction id  */
  sanctionNumber: Int,
  date: LocalDate,
  /** Name of the company that got the sanction */
  companyName:String,
  /**  H.P. number, the Israeli company id. */
  pcNumber: Option[Long],
  /** Where the violation happened */
  violationSite: String,
  /** What went wrong */
  violationClause:String,
  /** Sanction size */
  sum: Int,
  commissionersDecision:Option[String],
  kloBizEntId:Option[Long]
)
