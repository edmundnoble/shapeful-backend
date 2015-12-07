package models.auth

import java.util.Date

import models.auth.Users.{PasswordSalt, HashedPassword}
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import slick.driver.JdbcProfile
import shapeless._
import slickless._
import _root_.util.WithDb

object AuthTokens extends WithDb {
  case class AuthToken(tokenBase64: String) extends AnyVal

  implicit object AuthTokenWrites extends Writes[AuthToken] {
    override def writes(o: AuthToken): JsValue = JsObject.apply(Map("tokenBase64" → JsString(o.tokenBase64)))
  }

  import dbConfig.driver.api._

  implicit val authTokenColumnType = MappedColumnType.base[AuthToken, String](_.tokenBase64, AuthToken)
  implicit val dateColumnType = MappedColumnType.base[Date, Long](_.getTime, new Date(_))

  class AuthTokenTable(tag: Tag) extends Table[Int :: AuthToken :: Int :: Date :: HNil](tag, "authtokens") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def token = column[AuthToken]("token")
    def user_id = column[Int]("user_id")
    def added_time = column[Date]("added_time")

    def user_id_fk = foreignKey("authtokens_user_id_fk", user_id, Users.users)(_.id)
    def user_id_index = index("authtokens_user_id_idx", user_id, unique = false)
    def token_idx = index("authtokens_token_idx", token, unique = true)
    def * = id :: token :: user_id :: added_time :: HNil
  }

  def authTokens = TableQuery[AuthTokenTable]
}
