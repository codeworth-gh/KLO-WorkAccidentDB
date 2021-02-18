package models

/**
 * An employer or a private individual related to accidents, e.g. as the employer of
 * the injured worker, or as the entrepreneur of the project in which the accident happened.
 */
case class BusinessEntity(
  id:   Long,
  name: String,
  phone:  Option[String],
  email: Option[String],
  website: Option[String],
  isPrivatePerson: Boolean,
  memo: Option[String]
)