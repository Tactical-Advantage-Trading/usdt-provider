package trading.tacticaladvantage

import slick.jdbc
import slick.jdbc.PostgresProfile
import com.typesafe.config.ConfigFactory
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

package object usdt:
  extension[T](value: T)
    def asSome: Option[T] =
      Some(value)

  extension[A](seq: Seq[A] = Nil)
    def toMapBy[K, V](key: A => K, value: A => V): Map[K, V] =
      val res = for elem <- seq yield key(elem) -> value(elem)
      res.toMap
    def sumBy[B](fun: A => B)(
      using num: Numeric[B]
    ): B =
      seq.map(fun).sum

  def none: PartialFunction[Any, Unit] =
    case _ =>

  def delay(secs: Int)(act: => Unit): Disposable =
    Observable.timer(secs, TimeUnit.SECONDS).forEach(_ => act)

  def interval(span: Int, period: TimeUnit)(act: => Unit): Disposable =
    Observable.interval(span, period).forEach(_ => act)

case class UsdtDataProvider(contract: String, http: String, wss: String)
case class WebsocketServer(host: String, port: Int):
  def address = InetSocketAddress.createUnresolved(host, port)
case class USDT(usdtDataProvider: UsdtDataProvider, websocketServer: WebsocketServer):
  lazy val db: jdbc.PostgresProfile.backend.Database =
    val stream = getClass.getClassLoader.getResourceAsStream("application.conf")
    val config = ConfigFactory.parseString(scala.io.Source.fromInputStream(stream).mkString)
    PostgresProfile.backend.Database.forConfig("usdt.relationalDb", config)
case class USDTWrap(usdt: USDT)