package trading.tacticaladvantage.usdt

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.{DefaultSSLWebSocketServerFactory, WebSocketServer}
import org.slf4j.{Logger, LoggerFactory}
import trading.tacticaladvantage.USDT

import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.*
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

  def init(keystoreResource: String, storePass: Array[Char] = "123456".toCharArray): Unit =
    val stream = getClass.getClassLoader.getResourceAsStream(keystoreResource)
    require(stream != null, "Keystore file is not found")
    val ks = KeyStore.getInstance("JKS")
    ks.load(stream, storePass)

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, storePass)
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    val factory = new DefaultSSLWebSocketServerFactory(sslContext)

    setWebSocketFactory(factory)
    setConnectionLostTimeout(30)
    setReuseAddr(true)
    start

  override def onStart: Unit =
    logger.info("Started successfully")
    usdt.wrap = new usdt.WebConnectionWrap

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
