package trading.tacticaladvantage.usdt

import io.circe.parser.decode
import io.circe.syntax.*
import org.java_websocket.WebSocket
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api.*

import scala.annotation.targetName
import scala.util.{Failure, Random, Success}

final class RateLimiter(maxCalls: Int, perMillis: Long):
  private var history = Vector.empty[Long]
  def exceedsLimit(now: Long = System.currentTimeMillis): Boolean =
    history = (history :+ now).dropWhile(now - _ > perMillis)
    history.size > maxCalls

case class Watch(link: Link, req: Request, sub: RequestArguments.UsdtSubscribe)
class Link(val conn: WebSocket, val connId: String, val wsSrv: WsServer) extends StateMachine[Nothing]:
  val rateLimiter = RateLimiter(maxCalls = 6, perMillis = 2000L)
  val logger = LoggerFactory.getLogger("backend/client/Link")
  val GENERAL_ERROR: String = "general-error"
  val VERSION: Char = '1'
  
  @targetName("doTell")
  def !! (change: Any): Unit =
    change match
      case verJson: String if VERSION > verJson.head =>
        val failure = ResponseArguments.UsdtFailure(FailureCode.UPDATE_CLIENT_APP)
        val response = Response(failure.asSome, id = GENERAL_ERROR)
        conn.send(response.asJson.noSpaces)
      case verJson: String =>
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
    case _ if rateLimiter.exceedsLimit(System.currentTimeMillis) => replyFailure(req, FailureCode.INVALID_REQUEST)
    case subscribe: RequestArguments.UsdtSubscribe => wsSrv.usdt ! Watch(this, req, subscribe)
    case null => replyFailure(req, FailureCode.INVALID_REQUEST)
