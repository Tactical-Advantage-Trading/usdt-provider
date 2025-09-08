package trading.tacticaladvantage

import com.typesafe.config.ConfigFactory
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import slick.jdbc
import slick.jdbc.PostgresProfile

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

package object usdt:
  extension[T](value: T)
    def asSome: Option[T] =
      Some(value)

  def none: PartialFunction[Any, Unit] =
    case _ =>

  def delay(secs: Int)(act: => Unit): Disposable =
    Observable.timer(secs, TimeUnit.SECONDS).forEach(_ => act)

  def interval(span: Int, period: TimeUnit)(act: => Unit): Disposable =
    Observable.interval(span, period).forEach(_ => act)

case class UsdtDataProvider(contract: String, http1: String, http2: String, http3: String, wss1: String, wss2: String, wss3: String):
  private val httpPool = List(http1, http2, http3).map(endpoint => HttpService `apply` endpoint).map(Web3j.build)
  private val wssPool = List(wss1, wss2, wss3)
  private var httpIndex: Int = 0
  private var wssIndex: Int = 0
  def nextWss: String =
    val nextIndex = wssIndex + 1
    wssIndex = nextIndex % wssPool.size
    wssPool(wssIndex)
  def nextHttp: Web3j =
    val nextIndex = httpIndex + 1
    httpIndex = nextIndex % httpPool.size
    httpPool(httpIndex)
case class USDT(usdtDataProvider: UsdtDataProvider, websocketServerPort: Int):
  lazy val address: InetSocketAddress = new InetSocketAddress(websocketServerPort)
  lazy val db: jdbc.PostgresProfile.backend.Database =
    val stream = getClass.getClassLoader.getResourceAsStream("application.conf")
    val config = ConfigFactory.parseString(scala.io.Source.fromInputStream(stream).mkString)
    PostgresProfile.backend.Database.forConfig("usdt.relationalDb", config)
case class USDTWrap(usdt: USDT)