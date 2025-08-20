package trading.tacticaladvantage.usdt

import io.circe.generic.auto.*
import io.circe.config.parser
import trading.tacticaladvantage.USDTWrap

@main
def main: Unit =
  val conf = parser.decode[USDTWrap](/**/)
  println(conf.toOption.get)

