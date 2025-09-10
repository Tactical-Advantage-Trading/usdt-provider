package trading.tacticaladvantage.usdt

import io.circe.config.parser
import io.circe.generic.auto.*
import trading.tacticaladvantage.USDTWrap

@main
def main: Unit =
  val conf = parser.decode[USDTWrap](/**/)
  val server = WsServer(conf.toOption.get.usdt)
  server.init("keystore.p12")
  