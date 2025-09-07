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

class WsServer(conf: USDT) extends WebSocketServer(conf.address):
  val logger: Logger = LoggerFactory.getLogger("backend/WsServer")
  var links: Map[String, Link] = Map.empty
  val usdt = Usdt(conf)

  def init(keystoreResource: String, storePass: Array[Char] = "123456".toCharArray): Unit =
    val stream = getClass.getClassLoader.getResourceAsStream(keystoreResource)
    require(stream != null, "Keystore file is not found")
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(stream, storePass)

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, storePass)
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    val factory = new DefaultSSLWebSocketServerFactory(sslContext)

    setWebSocketFactory(factory)
    setReuseAddr(true)
    start

  override def onStart: Unit =
    logger.info("WS server started successfully")
    usdt.wrap = new usdt.WebConnectionWrap

  override def onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean): Unit =
    logger.info(s"Connection closed, reason=$reason, remote=$remote, code=$code")
    links -= conn.getAttachment[String]
    usdt ! conn.getAttachment[String]

  override def onError(conn: WebSocket, ex: Exception): Unit =
    logger.info("Runtime failure", ex)
    Option(conn).foreach(_.close)

  override def onOpen(conn: WebSocket, handshake: ClientHandshake): Unit =
    val connId = Random.alphanumeric.take(16).mkString
    val newLink = Link(conn, connId, this)
    conn.setAttachment[String](connId)
    links += (connId, newLink)

  override def onMessage(conn: WebSocket, message: ByteBuffer): Unit =
    logger.info("Unexpected binary", conn.getLocalSocketAddress)

  override def onMessage(conn: WebSocket, message: String): Unit =
    for link <- links get conn.getAttachment[String] do link ! message