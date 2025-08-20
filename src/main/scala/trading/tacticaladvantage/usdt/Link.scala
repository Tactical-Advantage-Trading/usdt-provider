package trading.tacticaladvantage.usdt

import io.circe.parser.decode
import io.circe.syntax.*
import org.java_websocket.WebSocket
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api.*

import scala.annotation.targetName
import scala.util.{Failure, Random, Success}

case class Watch(link: Link, req: Request, sub: RequestArguments.UsdtSubscribe)
class Link(conn: WebSocket, val connId: String, val wsSrv: WsServer) extends StateMachine[Nothing]:
  val logger = LoggerFactory.getLogger("backend/client/Link")
  val GENERAL_ERROR: String = "general-error"
  val VERSION: Char = '1'
  
  @targetName("doTell")
  def !! (change: Any): Unit = (change, state) match
    case (verJson: String, _) if VERSION > verJson.head =>
      val failure = ResponseArguments.UsdtFailure(FailureCode.UPDATE_CLIENT_APP)
      val response = Response(failure.asSome, id = GENERAL_ERROR)
      conn.send(response.asJson.noSpaces)
    case (verJson: String, _) =>
      decode[Request](verJson.tail) match
        case Right(userEvent) =>
          process(userEvent)
        case _ =>
          val failure = ResponseArguments.UsdtFailure(FailureCode.INVALID_JSON)
          val response = Response(failure.asSome, id = GENERAL_ERROR)
          conn.send(response.asJson.noSpaces)
    case _ =>

  def reply(req: Request, arguments: Option[ResponseArguments] = None): Unit =
    conn.send(Response(arguments, req.id).asJson.noSpaces)

  def replyFailure(req: Request, fail: FailureCode, note: String = new String): Unit =
    if note.nonEmpty then logger.info(s"REPLY FAILURE NOTE $note")
    reply(req, ResponseArguments.UsdtFailure(fail).asSome)

  def process(req: Request): Unit = req.arguments match
    case sub: RequestArguments.UsdtSubscribe => wsSrv.usdt ! Watch(this, req, sub)
    case _ => replyFailure(req, FailureCode.INVALID_REQUEST)
