package trading.tacticaladvantage

import com.typesafe.config.ConfigFactory
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
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

case class UsdtDataProvider(contract: String, http: String, wss: String)
case class USDT(usdtDataProvider: UsdtDataProvider, websocketServerPort: Int):
  lazy val address: InetSocketAddress = new InetSocketAddress(websocketServerPort)
  lazy val db: jdbc.PostgresProfile.backend.Database =
    val stream = getClass.getClassLoader.getResourceAsStream("application.conf")
    val config = ConfigFactory.parseString(scala.io.Source.fromInputStream(stream).mkString)
    PostgresProfile.backend.Database.forConfig("usdt.relationalDb", config)
case class USDTWrap(usdt: USDT)