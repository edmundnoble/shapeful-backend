package controllers

import controllers.routes
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Controller, Request}
import shapeless._
import slickless._
import slick.driver.JdbcProfile
import util.WithDb

import scala.concurrent.Future
import scala.language.existentials
import scalaz.Scalaz._
import scalaz._

import models.WelcomeCount

case class Message(value: String)

object MessageController extends Controller with WithDb {

  import dbConfig.driver.api._

  implicit val fooWrites = Json.writes[Message]

  def findQuery(hostname: String) =
      WelcomeCount.welcomeCount.filter(_.hostname === hostname)

  def getMessage = Action.async { implicit request: Request[AnyContent] ⇒
    val query = findQuery(request.host)
    dbConfig.db.run(query.result).flatMap { entries: Seq[String :: Int :: HNil] ⇒
      val entryMaybe = entries.headOption.map(e ⇒ e.head :: e.tail.head + 1 :: HNil)
      val entryOrDefault = entryMaybe.getOrElse(request.host :: 0 :: HNil)
      dbConfig.db.run(WelcomeCount.welcomeCount.insertOrUpdate(entryOrDefault)).map(_ ⇒
        Ok(Json.toJson(Message(s"Hello from Scala: ${entryOrDefault.tail.head}"))))
    }
  }

  def javascriptRoutes = Action { implicit request ⇒
    Ok(play.api.routing.JavaScriptReverseRouter("jsRoutes")(routes.javascript.MessageController.getMessage)).as(JAVASCRIPT)
  }

}