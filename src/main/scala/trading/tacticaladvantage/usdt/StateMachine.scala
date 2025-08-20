package trading.tacticaladvantage.usdt

import java.util.concurrent.Executors
import scala.annotation.targetName
import scala.compiletime.uninitialized
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Processor:
  @targetName("doTell")
  def !! (event: Any): Unit
  
  @targetName("tell")
  def ! (event: Any): Unit

trait CanBeShutDown extends Processor:
  def shutDown: Unit

trait StateMachine[S] extends Processor:
  given channelContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor)

  var state: S = uninitialized

  @targetName("become")
  def >>> (freshState: S): StateMachine[S] =
    state = freshState
    this
    
  @targetName("tell")
  def ! (event: Any): Unit =
    Future(this !! event).recover: reason =>
      onMalfunction(reason, state)

  def onMalfunction(reason: Throwable, state: S): Unit =
    none