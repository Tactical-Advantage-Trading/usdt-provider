package trading.tacticaladvantage.usdt

import slick.dbio.Effect
import slick.jdbc
import slick.jdbc.PostgresProfile.api.*
import slick.lifted.TableQuery.Extract
import slick.lifted.{Index, Rep}
import slick.sql.FixedSqlAction

import scala.concurrent.Await
import scala.concurrent.duration.*

object DbOps:
  type IntRep = Rep[Int]
  type LongRep = Rep[Long]
  type StringRep = Rep[String]
  
  val span: FiniteDuration = 60.seconds
  
  val removeTables = DBIO.seq(
    RecordTxsUsdtPolygon.model.schema.dropIfExists, 
    RecordTrustedAddress.model.schema.dropIfExists
  )
  
  val createTables = DBIO.seq(
    RecordTxsUsdtPolygon.model.schema.createIfNotExists, 
    RecordTrustedAddress.model.schema.createIfNotExists
  )
  
  def tx[T, E <: Effect](act: DBIOAction[T, NoStream, E], db: Database): T = Await.result(db.run(act.transactionally), span)
  def txWrite[T](act: DBIOAction[T, NoStream, Effect.Write], db: Database): T = Await.result(db.run(act.transactionally), span)
  def txBlockingRead[T](act: DBIOAction[T, NoStream, Effect.Read], db: Database): T = Await.result(db.run(act.transactionally), span)

object RecordTxsUsdtPolygon:
  val tableName = "txs_usdt_polygon"
  val model = TableQuery[RecordTxsUsdtPolygon]
  type DbType = (Long, String, String, Long, String, String, String, String, Long, Boolean)

  def upsert(amount: String, txHash: String, blockNum: Long, fromAddress: String, toAddress: String, data: String, topics: String, stamp: Long, rm: Boolean) = sqlu"""
    INSERT INTO #$tableName (amount, hash, block, from_addr, to_addr, data, topics, stamp, is_removed)
    VALUES ($amount, $txHash, $blockNum, $fromAddress, $toAddress, $data, $topics, $stamp, $rm)
    ON CONFLICT (hash, from_addr, to_addr) DO UPDATE SET is_removed = $rm, block = $blockNum
  """

  val forAddress = Compiled: (address: DbOps.StringRep) =>
    model.filter(rec => rec.fromAddr === address || rec.toAddr === address)
      .sortBy(_.id.desc).take(25)

class RecordTxsUsdtPolygon(tag: Tag) extends Table[RecordTxsUsdtPolygon.DbType](tag, RecordTxsUsdtPolygon.tableName):
  def * = (id, amount, txHash, block, fromAddr, toAddr, data, topics, stamp, isRemoved)

  def idx1: Index = index("idx_hash_from_to", (txHash, fromAddr, toAddr), unique = true)
  def idx2: Index = index("idx_from", fromAddr, unique = false)
  def idx3: Index = index("idx_to", toAddr, unique = false)
  
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def isRemoved: Rep[Boolean] = column[Boolean]("is_removed")
  def fromAddr: Rep[String] = column[String]("from_addr")
  def toAddr: Rep[String] = column[String]("to_addr")
  def topics: Rep[String] = column[String]("topics")
  def amount: Rep[String] = column[String]("amount")
  def txHash: Rep[String] = column[String]("hash")
  def data: Rep[String] = column[String]("data")
  def block: Rep[Long] = column[Long]("block")
  def stamp: Rep[Long] = column[Long]("stamp")

//

object RecordTrustedAddress:
  val tableName = "trusted_address"
  val model = TableQuery[RecordTrustedAddress]
  type DbType = (Long, String, BigDecimal, Long)

  def upsert(address: String, amount: BigDecimal, stamp: Long = System.currentTimeMillis) = sqlu"""
    INSERT INTO #$tableName as tbl (address, amount, stamp) VALUES ($address, $amount, $stamp)
    ON CONFLICT (address) DO UPDATE SET amount = GREATEST(tbl.amount, EXCLUDED.amount)
  """

class RecordTrustedAddress(tag: Tag) extends Table[RecordTrustedAddress.DbType](tag, RecordTrustedAddress.tableName):
  def * = (id, address, amount, stamp)

  def idx1: Index = index("address", address, unique = true)

  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def amount: Rep[BigDecimal] = column[BigDecimal]("amount")
  def address: Rep[String] = column[String]("address")
  def stamp: Rep[Long] = column[Long]("stamp")