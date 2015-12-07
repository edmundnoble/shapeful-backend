package models.auth

import java.util.Base64
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import shapeless._
import slickless._
import util.WithDb


object Users extends WithDb {

  case class PasswordSalt(bytes: Array[Byte], base64: String)
  object PasswordSalt {
    def fromBase64(base: String): PasswordSalt = {
      PasswordSalt(Base64.getDecoder.decode(base), base)
    }
    def fromBytes(hash: Array[Byte]): PasswordSalt = {
      PasswordSalt(hash, Base64.getEncoder.encodeToString(hash))
    }
  }
  case class HashedPassword(bytes: Array[Byte], base64: String)
  object HashedPassword {
    def fromBase64(base: String): HashedPassword = {
      HashedPassword(Base64.getDecoder.decode(base), base)
    }
    def fromBytes(hash: Array[Byte]): HashedPassword = {
      HashedPassword(hash, Base64.getEncoder.encodeToString(hash))
    }
  }

  import dbConfig.driver.api._

  // And a ColumnType that maps it to Int values 1 and 0
  implicit val hashedPasswordColumnType = MappedColumnType.base[HashedPassword, String](_.base64, HashedPassword.fromBase64)
  implicit val passwordSaltColumnType = MappedColumnType.base[PasswordSalt, String](_.base64, PasswordSalt.fromBase64)

  /* Table mapping
 */
  class UserTable(tag: Tag) extends Table[Int :: String :: HashedPassword :: PasswordSalt :: HNil](tag, "users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def password = column[HashedPassword]("password")
    def salt = column[PasswordSalt]("salt")

    def index_email = index("users_email_idx", email, unique = true)
    def * = id :: email :: password :: salt :: HNil
  }

  def users = TableQuery[UserTable]
}
