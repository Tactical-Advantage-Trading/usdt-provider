package trading.tacticaladvantage.usdt

import io.circe.generic.auto.*
import io.circe.config.parser
import trading.tacticaladvantage.USDTWrap

@main
def main: Unit =
  val conf = parser.decode[USDTWrap](/**/)
  val usdt1 = conf.toOption.get.usdt

//  DbOps.tx(DbOps.removeTables, usdt1.db)
//  DbOps.tx(DbOps.createTables, usdt1.db)

  val server = WsServer(conf.toOption.get.usdt)
  server.init("keystore.p12")