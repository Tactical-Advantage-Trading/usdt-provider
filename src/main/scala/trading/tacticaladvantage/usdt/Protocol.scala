package trading.tacticaladvantage.usdt

import io.circe.*
import io.circe.derivation.*
import io.circe.generic.semiauto.*

given circeConfig: Configuration =
  Configuration.default.withDiscriminator("tag")

enum FailureCode(val code: Int):
  case INVALID_JSON extends FailureCode(10)
  case INVALID_REQUEST extends FailureCode(20)
  case UPDATE_CLIENT_APP extends FailureCode(30)
  case INFRA_FAIL extends FailureCode(40)

given Encoder[FailureCode] = Encoder.encodeInt.contramap(_.code)
given Decoder[FailureCode] = Decoder.decodeInt.emap: enumCode =>
  FailureCode.values
    .find(_.code == enumCode)
    .toRight(s"!$enumCode")

case class UsdtTransfer(amount: String, fromAddr: String, toAddr: String, hash: String, block: Long, stamp: Long, isRemoved: Boolean)

given Encoder[UsdtTransfer] = deriveEncoder
given Decoder[UsdtTransfer] = deriveDecoder

enum RequestArguments(val tag: String):
  // Note: address should be lower-cased since we normalize addresses to be lower case on our side
  case UsdtSubscribe(address: String, afterBlock: Long) extends RequestArguments("UsdtSubscribe")

enum ResponseArguments(val tag: String):
  case UsdtFailure(failureCode: FailureCode) extends ResponseArguments("UsdtFailure")
  case UsdtTransfers(transfers: List[UsdtTransfer] = Nil) extends ResponseArguments("UsdtTransfers")
  case UsdtBalanceNonce(address: String, balance: String, nonce: String) extends ResponseArguments("UsdtBalanceNonce")

given Encoder[RequestArguments] = ConfiguredEncoder.derived
given Decoder[RequestArguments] = ConfiguredDecoder.derived
given Encoder[ResponseArguments] = ConfiguredEncoder.derived
given Decoder[ResponseArguments] = ConfiguredDecoder.derived

case class Request(arguments: RequestArguments, id: String)
case class Response(arguments: Option[ResponseArguments], id: String)

given Encoder[Request] = deriveEncoder
given Decoder[Request] = deriveDecoder
given Encoder[Response] = deriveEncoder
given Decoder[Response] = deriveDecoder
