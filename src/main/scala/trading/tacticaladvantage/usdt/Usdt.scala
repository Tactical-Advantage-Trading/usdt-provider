package trading.tacticaladvantage.usdt

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.softwaremill.quicklens.*
import io.reactivex.disposables.Disposable
import org.slf4j.LoggerFactory
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.{Address, Function}
import org.web3j.abi.{FunctionEncoder, TypeReference}
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName.{LATEST, PENDING}
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.{EthCall, EthGetTransactionCount, EthSubscribe}
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService
import org.web3j.protocol.websocket.events.LogExtNotification
import org.web3j.utils.Numeric
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api.*
import trading.tacticaladvantage.USDT

import java.math.BigInteger
import java.net.URI
import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.TimeUnit
import scala.annotation.targetName
import scala.compiletime.uninitialized
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.{Success, Try}

val AWAIT_BALANCE_NONCE_HTTP_ERROR = 2000
val TYPE_REF = new TypeReference[Uint256] {} :: Nil
val FALLBACK = ResponseArguments.UsdtFailure(FailureCode.INFRA_FAIL)

class Usdt(conf: USDT) extends StateMachine[Nothing]:
  val logger = LoggerFactory.getLogger("backend/Usdt")
  val logExtClass = classOf[LogExtNotification]
  val subClass = classOf[EthSubscribe]

  val addresses = java.util.Collections.singletonList(conf.usdtDataProvider.contract)
  val topic = org.web3j.crypto.Hash.sha3String("Transfer(address,address,uint256)")
  val topics = java.util.Collections.singletonList(topic)
  
  val params = Map("address" -> addresses, "topics" -> topics)
  val paramsList = java.util.Arrays.asList("logs", params.asJava)

  type Watches = Set[Watch]
  type UsdtTransfers = Seq[UsdtTransfer]

  val transferHistoryCache: LoadingCache[String, UsdtTransfers] =
    val loader = new CacheLoader[String, UsdtTransfers]:
      override def load(adr: String): UsdtTransfers =
        val result = DbOps.txBlockingRead(RecordTxsUsdtPolygon.forAddress(adr).result, conf.db)
        for (_, amount, txHash, block, fromAddr, toAddr, _, _, stamp, isRemoved) <- result yield
          UsdtTransfer(amount, fromAddr, toAddr, txHash, block, stamp, isRemoved)
    CacheBuilder.newBuilder
      .expireAfterAccess(28, TimeUnit.DAYS)
      .maximumSize(100_000)
      .build(loader)

  // This one may explode, needs to be handled at caller site
  val balanceNonceCache: LoadingCache[String, ResponseArguments.UsdtBalanceNonce] =
    val loader = new CacheLoader[String, ResponseArguments.UsdtBalanceNonce]:
      override def load(adr: String): ResponseArguments.UsdtBalanceNonce =
        val data = FunctionEncoder `encode` Function("balanceOf", (Address(adr) :: Nil).asJava, TYPE_REF.asJava)
        val tokenBal = Transaction.createEthCallTransaction(null, conf.usdtDataProvider.contract, data)
        val ethCall = conf.usdtDataProvider.nextHttp.ethCall(tokenBal, LATEST)
        val balance = convertBalance(ethCall.send.getValue).toString
        ResponseArguments.UsdtBalanceNonce(adr, balance, "0x1")
    CacheBuilder.newBuilder
      .expireAfterAccess(28, TimeUnit.DAYS)
      .maximumSize(100_000)
      .build(loader)

  var currentBlock = 0L
  var address2Watch = Map.empty[String, Watch]
  var connId2Watch = Map.empty[String, Watches].withDefaultValue(Set.empty)
  var wrap: WebConnectionWrap = uninitialized

  class WebConnectionWrap:
    var sub: Try[Disposable] = uninitialized
    val wssUri = new URI(conf.usdtDataProvider.nextWss)
    val wsClient = new org.web3j.protocol.websocket.WebSocketClient(wssUri):
      override def onClose(code: Int, reason: String, fromRemote: Boolean): Unit =
        logger.info(s"Disconnected, code=$code, reason=$reason")
        delay(2) { wrap = new WebConnectionWrap }
        transferHistoryCache.invalidateAll
        balanceNonceCache.invalidateAll
        sub.foreach(_.dispose)
      override def onError(e: Exception): Unit =
        onClose(-1, "onError", fromRemote = false)
        super.onError(e)

    val currentActiveWebSocket = WebSocketService(wsClient, true)
    val req = new Request("eth_subscribe", paramsList, currentActiveWebSocket, subClass)

    sub = Try:
      currentActiveWebSocket.connect
      logger.info(s"Started successfully with $wssUri")
      currentActiveWebSocket.subscribe(req, "eth_unsubscribe", logExtClass).buffer(100).subscribe(logs => {
        val res = logs.asScala.map(_.getParams.getResult).filter(l => convertBalance(l.getData) >= 0.01D).map: log =>
          currentBlock = Option(log.getBlockNumber).map(Numeric.decodeQuantity).map(_.longValue).getOrElse(currentBlock)

          val transfer =
            UsdtTransfer(amount = convertBalance(log.getData).toString,
              fromAddr = "0x" + log.getTopics.get(1).substring(26).toLowerCase,
              toAddr = "0x" + log.getTopics.get(2).substring(26).toLowerCase,
              log.getTransactionHash, currentBlock, System.currentTimeMillis,
              log.isRemoved)

          transferHistoryCache.invalidate(transfer.fromAddr)
          transferHistoryCache.invalidate(transfer.toAddr)
          balanceNonceCache.invalidate(transfer.fromAddr)
          balanceNonceCache.invalidate(transfer.toAddr)

          if address2Watch.contains(transfer.toAddr) then sendBalanceNonce(transfer.toAddr)
          if address2Watch.contains(transfer.fromAddr) then sendBalanceNonce(transfer.fromAddr)
          for watch <- address2Watch.get(transfer.toAddr) orElse address2Watch.get(transfer.fromAddr) do
            watch.link.reply(watch.req, ResponseArguments.UsdtTransfers(transfer :: Nil).asSome)
            logger.info(s"${transfer.fromAddr} -> ${transfer.toAddr}, ${transfer.amount}")

          RecordTxsUsdtPolygon.upsert(transfer.amount, transfer.hash, transfer.block, transfer.fromAddr,
            transfer.toAddr, log.getData, log.getTopics.asScala.mkString(","), transfer.stamp, transfer.isRemoved)
        logger.info(s"${res.size} transfers, currentBlock=$currentBlock")
        DbOps.txWrite(DBIO.sequence(res), conf.db)
        broadcastCurrentBlock(currentBlock)
      }, _ => wsClient.closeBlocking)

  @targetName("doTell")
  def !! (event: Any): Unit =
    event match
      case watch @ Watch(link, req, sub) =>
        address2Watch = address2Watch.updated(sub.address, watch)
        connId2Watch = connId2Watch.modify(_ at link.connId).using(_ + watch)
        val history = transferHistoryCache.get(sub.address).filter(_.block > sub.afterBlock)
        if history.nonEmpty then link.reply(req, ResponseArguments.UsdtTransfers(history.toList).asSome)
        sendBalanceNonce(sub.address)
      case connId: String =>
        connId2Watch(connId).foreach: watch =>
          address2Watch -= watch.sub.address
        connId2Watch -= connId
      case _ =>

  def getBalanceNonce(address: String, left: Int): ResponseArguments =
    Try apply balanceNonceCache.get(address) match
      case Success(result) =>
        result
      case _ if left > 0 =>
        Thread.sleep(AWAIT_BALANCE_NONCE_HTTP_ERROR)
        getBalanceNonce(address, left - 1)
      case _ =>
        FALLBACK

  def sendBalanceNonce(adr: String) = Future:
    val response = getBalanceNonce(address = adr, left = 2).asSome
    for watch <- address2Watch.get(adr) do watch.link.reply(watch.req, response)

  def broadcastCurrentBlock(num: Long) =
    val bytesToSend = ByteBuffer.allocate(java.lang.Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(num).array
    for watch <- connId2Watch.values.flatMap(_.headOption) do watch.link.conn.send(bytesToSend)

  def convertBalance(hexString: String): BigDecimal =
    val bigInt = new BigInteger(hexString.substring(2), 16)
    BigDecimal(bigInt) / BigDecimal(10).pow(6)
