package models

import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import shapeless._
import slickless._
import slick.driver.JdbcProfile
import slick.lifted.Tag
import util.WithDb

object WelcomeCount extends WithDb {

  import dbConfig.driver.api._

  class WelcomeCountTable(tag: Tag) extends Table[String :: Int :: HNil](tag, "welcome_ctr") {

    def hostname = column[String]("hostname", O.PrimaryKey)
    def count = column[Int]("count")

    def hostname_index = index("welcome_ctr_hostname_idx", hostname, unique = true)
    def * = hostname :: count :: HNil
  }

  def welcomeCount = TableQuery[WelcomeCountTable]
}