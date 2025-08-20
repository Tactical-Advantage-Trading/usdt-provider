package trading.tacticaladvantage.usdt

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.slf4j.{Logger, LoggerFactory}
import trading.tacticaladvantage.USDT

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import scala.annotation.targetName
import scala.util.Random

class WsServer(conf: USDT) extends WebSocketServer(conf.address) with StateMachine[Nothing]:
  val logger: Logger = LoggerFactory.getLogger("backend/WsServer")
  var links: Map[String, Link] = Map.empty
  val usdt = Usdt(conf)

  @targetName("doTell")
  def !!(change: Any): Unit =
    change match
      case wsClient: WebSocket =>
        val connId = Random.alphanumeric.take(16).mkString
        val newLink = Link(wsClient, connId, this)
        wsClient.setAttachment[String](connId)
        links += (connId, newLink)
      case connId: String =>
        links -= connId
        usdt ! connId

  override def onStart: Unit = 
    usdt.wrap = new usdt.WebConnectionWrap
    logger.info("Started successfully")

  override def onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean): Unit =
    logger.info(s"Connection closed, reason=$reason, remote=$remote, code=$code")
    this ! conn.getAttachment[String]

  override def onError(conn: WebSocket, ex: Exception): Unit =
    logger.info("Runtime failure", ex)
    conn.close(505)

  override def onOpen(conn: WebSocket, handshake: ClientHandshake): Unit =
    this ! conn

  override def onMessage(conn: WebSocket, message: ByteBuffer): Unit =
    logger.info("Unexpected binary", conn.getLocalSocketAddress)

  override def onMessage(conn: WebSocket, message: String): Unit =
    for link <- links get conn.getAttachment[String] do link ! message
