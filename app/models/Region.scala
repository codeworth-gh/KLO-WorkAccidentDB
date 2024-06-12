package models

/**
 * Region of the country in which the accident took place.
 * @param id    database id.
 * @param name  name of the region
 */
case class Region(id: Int, name:String)

object Region {
  def unapply(r:Region):Option[(Int, String)] = Some((r.id, r.name))
}