package models

case class InjuryCause(
                      id: Int,
                      name: String
                      )

object InjuryCause {
  def unapply(r:InjuryCause):Option[(Int, String)] = Some((r.id, r.name))
}