package dataaccess

import play.api.Configuration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject

class RegionsDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import profile.api._
  private val regionsTable = TableQuery[RegionsTable]
  
}
