package models

case class Industry(
                   id: Int,
                   name: String
                   )

object Industry {
  def unapply(r:Industry):Option[(Int, String)] = Some((r.id, r.name))
}