package controllers.auth

import java.security.{SecureRandom, MessageDigest}
import java.util.{Base64, Date}
import javax.crypto.{SecretKeyFactory, Mac}
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec}

import models.auth.AuthTokens.AuthToken
import models.auth.{AuthTokens, Users}
import models.auth.Users.{PasswordSalt, HashedPassword}
import play.api.libs.json.Json
import play.api.{Logger, Play}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.Files
import play.api.mvc.{Controller, _}
import slick.dbio.DBIOAction
import slick.driver.JdbcProfile
import shapeless._
import slickless._
import util.WithDb

import scala.concurrent.Future
import scala.language.{postfixOps, existentials}

object Authentication extends Controller with WithDb {

  import AuthTokens._

  val logger = Logger(getClass)

  import dbConfig.driver.api._

  sealed trait AuthResult
  case class AuthFailure() extends AuthResult
  case class AuthSuccess() extends AuthResult

  def check(email: String, password: String) = {
    logger.info(s"Checking $email!")
    findUsersCompiled(email).result map { users ⇒
      if (users.isEmpty) {
        false
      } else {
        val hashedPassword = signWithAppSecret(hashPasswordWithSalt(password, users.head.tail.tail.tail.head))
        println(f"Hashed password: ${hashedPassword.map(b => f"${0xff & b}%x").mkString}\n\n" +
          f"salt: ${users.head.tail.tail.tail.head.bytes.map(b => f"${0xff & b}%x").mkString}")
        val passwordValid = users.head.tail.tail.head.bytes sameElements hashedPassword
        passwordValid
      }
    }
  }

  val HMAC_SHA1_ALGORITHM = "HmacSHA1"

  def generateAndAddLoginToken(email: String) = {
    logger.info(s"Generating login token for $email!")
    val sr = SecureRandom.getInstance("SHA1PRNG")
    val token = new Array[Byte](32)
    sr.nextBytes(token)
    val authToken = AuthToken(Base64.getEncoder.encodeToString(token))
    findUsersCompiled(email).result flatMap { users ⇒
      if (users.nonEmpty) {
        logger.info(s"Generated token ${authToken.tokenBase64} for $email!")
        (AuthTokens.authTokens +=
          (-1 :: authToken :: users.head.head :: new Date() :: HNil)).map(_ ⇒ Some(authToken))
      } else {
        DBIOAction.successful(None)
      }
    }
  }

  def hashPasswordWithRandomSaltAndSign(password: String): (HashedPassword, PasswordSalt) = {
    val salt = makeSalt()
    val hashed = hashPasswordWithSalt(password, salt)
    val signed = signWithAppSecret(hashed)
    (HashedPassword.fromBytes(signed), salt)
  }

  def hashPasswordWithSalt(password: String, salt: PasswordSalt): Array[Byte] = {
    val spec = new PBEKeySpec(password.toCharArray, salt.bytes, 1000, 64 * 8)
    val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val hash = skf.generateSecret(spec).getEncoded
    hash
  }

  def signWithAppSecret(input: Array[Byte]): Array[Byte] = {
    // get an hmac_sha1 key from the raw key bytes
    val key = "E4BT6RNwjx85jok7zwuhK6GX6AOcV208HAEkrU1SZQRV9VlrwZWeVaecGq9Ug0yV3UzjPYcozTqQ0SFqPzetbUrpQ3FGEViWcr38"
    val signingKey = new SecretKeySpec(key.getBytes, HMAC_SHA1_ALGORITHM)

    // get an hmac_sha1 Mac instance and initialize with the signing key
    val mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
    mac.init(signingKey)

    // compute the hmac on input data bytes
    mac.doFinal(input)
  }

  def makeSalt(): PasswordSalt = {
    val sr = SecureRandom.getInstance("SHA1PRNG")
    val salt = new Array[Byte](32)
    sr.nextBytes(salt)
    PasswordSalt.fromBytes(salt)
  }

  def findUsersCompiled(email: String) = {
    Users.users.filter(_.email === email).take(1)
  }

  def emailValid(email: String): Boolean =
    (!email.isEmpty) && email.contains("@")

  def passwordValid(password: String): Boolean =
    (!password.isEmpty) && (password.length >= 8) && (password.length <= 256)

  def createUser() = Action.async(parse.multipartFormData) { implicit request ⇒
    val params = request.body.dataParts
    val authParams = for {
      email ← params.get("email").flatMap(_.headOption) if emailValid(email)
      password ← params.get("password").flatMap(_.headOption) if passwordValid(password)
    } yield (email, password)
    dbConfig.db.run(authParams.map {
      case (email, password) ⇒
        findUsersCompiled(email).result.flatMap { users ⇒
          if (users.isEmpty) {
            logger.info(s"$email: user exists!")
            DBIOAction.successful(Unauthorized("User exists!"))
          } else {
            logger.info(s"$email:$password user created!")
            val (hashedPassword, passwordSalt) = hashPasswordWithRandomSaltAndSign(password)
            (Users.users += (-1 :: email :: hashedPassword :: passwordSalt :: HNil))
              .flatMap(_ => generateAndAddLoginToken(email))
              .map(token ⇒ token.fold(InternalServerError("User deleted!"))(t => Ok(Json.toJson(t))))
          }
        } transactionally
    }.getOrElse(DBIOAction.successful(Unauthorized("Missing parameters!"))))
  }

  def login() = Action.async(parse.multipartFormData) { implicit request ⇒
    val params = request.body.dataParts
    val authParams = for {
      email ← params.get("email").flatMap(_.headOption) if emailValid(email)
      password ← params.get("password").flatMap(_.headOption) if passwordValid(password)
    } yield (email, password)
    val authResult = authParams.fold(Future.successful(Unauthorized("Missing parameters!"))) {
      case (email, password) ⇒
        dbConfig.db.run(check(email, password) flatMap { loginSuccess ⇒
          if (loginSuccess) {
            generateAndAddLoginToken(email) map { loginToken ⇒
              loginToken.fold(InternalServerError("User deleted!")) { authToken =>
                Ok(Json.toJson(authToken))
              }
            }
          } else {
            DBIOAction.successful(Unauthorized("Incorrect email or password!"))
          }
        } transactionally)
    }
    authResult
  }

}