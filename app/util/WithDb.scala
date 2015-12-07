package util

import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

trait WithDb {
  lazy val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)
}
