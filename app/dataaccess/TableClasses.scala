package dataaccess

import java.sql.Timestamp
import models.{Citizenship, Industry, InjuryCause, Invitation, PasswordResetRequest, Region, User}
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._

import scala.reflect.ClassTag

class UsersTable(tag:Tag) extends Table[User](tag,"users") {

  def id                = column[Long]("id",O.PrimaryKey, O.AutoInc)
  def username          = column[String]("username")
  def name              = column[String]("name")
  def email             = column[String]("email")
  def encryptedPassword = column[String]("encrypted_password")

  def * = (id, username, name, email, encryptedPassword) <> (User.tupled, User.unapply)

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