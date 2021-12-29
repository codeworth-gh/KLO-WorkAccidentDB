package dataaccess

import play.api.cache.SyncCacheApi
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.lifted

import javax.inject.Inject
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.concurrent.duration.Duration

object SettingKey extends Enumeration {
  val LastSafetyWarrantScrapeTime  = Value
  val LastSafetyWarrantScrapeCount = Value
  val SafetyWarrantProductsNeedUpdate = Value
}

case class Setting( key:SettingKey.Value, value:String ) {
  def isTrueish:Boolean = Set("1","yes","true")(value.toLowerCase)
}

/**
 * A DAO for accessing and editing settings.
 */
class SettingDAO @Inject() (protected val dbConfigProvider:DatabaseConfigProvider, cache:SyncCacheApi
                           )(implicit ec:ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
  
  import slick.jdbc.PostgresProfile.api._
  private val READ_DURATION = Duration(5, duration.SECONDS)
  private val STORE_DURATION = Duration(5, duration.MINUTES)
  
  private val settingsTable = TableQuery[SettingsTable]
  
  
  
  def get(key:SettingKey.Value):Option[String] = {
    cache.get[String]("settings-" + key.toString) match {
      case s:Some[String] => s
      case None    => loadFromDb(key)
    }
  }
  
  def set( k:SettingKey.Value, v:String):Setting = set(Setting(k,v))
  
  def set(s:Setting):Setting = {
    cache.set("settings-"+s.key.toString, s.value, STORE_DURATION)
    db.run( settingsTable.insertOrUpdate(s) )
    s
  }
  
  private def loadFromDb(key: SettingKey.Value):Option[String] = {
    val res = db.run( settingsTable.filter( _.key === key.toString ).result.headOption ).map(
      {
        case Some(v) => {
          cache.set("settings-" + key.toString, v.value)
          Some(v.value)
        }
        case None => None
      }
    )
    Await.result( res, READ_DURATION )
  }
  

}
