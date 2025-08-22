package trading.tacticaladvantage.usdt

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.softwaremill.quicklens.*
import org.slf4j.{Logger, LoggerFactory}
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.{Address, Function}
import org.web3j.abi.{FunctionEncoder, TypeReference}
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName.{LATEST, PENDING}
import org.web3j.protocol.core.methods.request.{EthFilter, Transaction}
import org.web3j.protocol.core.methods.response.{EthCall, EthGetTransactionCount}
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api.*
import trading.tacticaladvantage.USDT

import java.math.BigInteger
import java.net.URI
import scala.annotation.targetName
import scala.compiletime.uninitialized
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.{Success, Try}

val AWAIT_BALANCE_NONCE_HTTP_ERROR = 2000
val TYPE_REF = new TypeReference[Uint256] {} :: Nil
val FALLBACK = ResponseArguments.UsdtFailure(FailureCode.INFRA_FAIL)

class Usdt(conf: USDT) extends StateMachine[Nothing]:
  val logger: Logger = LoggerFactory.getLogger("backend/Usdt")
  val http = HttpService(conf.usdtDataProvider.http)
  val httpW3 = Web3j.build(http)

  type Watches = Set[Watch]
  type UsdtTransfers = Seq[UsdtTransfer]

  val transferHistoryCache: LoadingCache[String, UsdtTransfers] =
    val loader = new CacheLoader[String, UsdtTransfers]:
      override def load(adr: String): UsdtTransfers =
        val result = DbOps.txBlockingRead(RecordTxsUsdtPolygon.forAddress(adr).result, conf.db)
        for (_, amount, txHash, block, fromAddr, toAddr, _, _, stamp, isRemoved) <- result
          yield UsdtTransfer(amount, fromAddr, toAddr, txHash, block, stamp, isRemoved)
    CacheBuilder.newBuilder.maximumSize(100_000).build(loader)

  // This one may explode, needs to be handled at caller site
  val balanceNonceCache: LoadingCache[String, ResponseArguments.UsdtBalanceNonce] =
    val loader = new CacheLoader[String, ResponseArguments.UsdtBalanceNonce]:
      override def load(adr: String): ResponseArguments.UsdtBalanceNonce =
        val data = FunctionEncoder.encode(Function("balanceOf", (Address(adr) :: Nil).asJava, TYPE_REF.asJava))
        val tokenBal = Transaction.createEthCallTransaction(adr, conf.usdtDataProvider.contract, data)
        val nonceReq = httpW3.ethGetTransactionCount(adr, PENDING)
        val balanceReq = httpW3.ethCall(tokenBal, LATEST)

        val responses = httpW3.newBatch.add(nonceReq).add(balanceReq).send.getResponses
        val countResp = responses.get(0).asInstanceOf[EthGetTransactionCount].getTransactionCount
        ResponseArguments.UsdtBalanceNonce(adr, balance = responses.get(1).asInstanceOf[EthCall].getValue,
          nonce = org.web3j.utils.Numeric.encodeQuantity(countResp), currentBlock)
    CacheBuilder.newBuilder.maximumSize(100_000).build(loader)

  var currentBlock = 0L
  var address2Watch = Map.empty[String, Watch]
  var connId2Watch = Map.empty[String, Watches].withDefaultValue(Set.empty)
  var wrap: WebConnectionWrap = uninitialized

  class WebConnectionWrap:
    val wssUri = new URI(conf.usdtDataProvider.nextWss)
    val topic = Hash.sha3String("Transfer(address,address,uint256)")
    val filter = EthFilter(LATEST, LATEST, conf.usdtDataProvider.contract)

    val wsClient = new org.web3j.protocol.websocket.WebSocketClient(wssUri):
      override def onClose(code: Int, reason: String, fromRemote: Boolean): Unit =
        logger.info(s"Websocket disconnected, code=$code, reason=$reason")
        delay(2) { wrap = new WebConnectionWrap }
        transferHistoryCache.invalidateAll
        balanceNonceCache.invalidateAll

    wsClient.setConnectionLostTimeout(5)
    val ws = WebSocketService(wsClient, true)
    val wsW3 = Web3j.build(ws)

    Try:
      ws.connect
      filter.addSingleTopic(topic)
      logger.info(s"Started successfully with $wssUri")
      wsW3.ethLogFlowable(filter).buffer(20).subscribe(logs => {
        // Batch multiple transfers to save on database writes
        val res = logs.asScala.map: log =>
          val transfer = UsdtTransfer(log.getData, log.getTopics.get(1).substring(26), log.getTopics.get(2).substring(26),
            log.getTransactionHash, log.getBlockNumber.longValue, System.currentTimeMillis, Option(log.isRemoved) getOrElse false)

          transferHistoryCache.invalidate(transfer.fromAddr)
          transferHistoryCache.invalidate(transfer.toAddr)
          balanceNonceCache.invalidate(transfer.fromAddr)
          balanceNonceCache.invalidate(transfer.toAddr)
          currentBlock = transfer.block

          if address2Watch.contains(transfer.toAddr) then sendBalanceNonce(transfer.toAddr)
          if address2Watch.contains(transfer.fromAddr) then sendBalanceNonce(transfer.fromAddr)

          for watch <- address2Watch.get(transfer.toAddr) orElse address2Watch.get(transfer.fromAddr) do
            watch.link.reply(watch.req, ResponseArguments.UsdtTransfers(transfer :: Nil, currentBlock).asSome)
            
          RecordTxsUsdtPolygon.upsert(transfer.amount, transfer.hash, transfer.block, transfer.fromAddr, transfer.toAddr,
            log.getData, log.getTopics.asScala.mkString(","), transfer.stamp, transfer.isRemoved)
        DbOps.txWrite(DBIO.sequence(res), conf.db)
      }, _ => wsClient.closeBlocking)

  @targetName("doTell")
  def !! (event: Any): Unit =
    event match
      case watch @ Watch(link, req, sub) =>
        address2Watch = address2Watch.updated(sub.address, watch)
        connId2Watch = connId2Watch.modify(_ at link.connId).using(_ + watch)
        val history = transferHistoryCache.get(sub.address).filter(_.block > sub.afterBlock)
        val response = ResponseArguments.UsdtTransfers(history.toList, currentBlock)
        if history.nonEmpty then link.reply(req, response.asSome)
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

  def sendBalanceNonce(address: String) = Future:
    val response = getBalanceNonce(address, left = 2).asSome
    for watch <- address2Watch.get(address) do watch.link.reply(watch.req, response)

  def convertBalance(hexString: String): BigDecimal =
    val bigInt = new BigInteger(hexString.substring(2), 16)
    BigDecimal(bigInt) / BigDecimal(10).pow(6)
