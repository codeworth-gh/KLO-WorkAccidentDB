package models

/**
 * The way a business entity relates to an accident.
 */
case class RelationToAccident (
  id: Int,
  name: String
)

object RelationToAccident {
  def unapply(r:RelationToAccident):Option[(Int, String)] = Some((r.id, r.name))
}