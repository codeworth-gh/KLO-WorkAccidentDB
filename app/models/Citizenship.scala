package models

case class Citizenship(
                      id: Int,
                      name: String
                      )

object Citizenship {
  def unapply(r:Citizenship):Option[(Int, String)] = Some((r.id, r.name))
}