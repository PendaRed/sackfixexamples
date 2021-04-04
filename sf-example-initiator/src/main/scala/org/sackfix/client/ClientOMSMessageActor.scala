package org.sackfix.client

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.time.LocalDateTime
import akka.actor.typed.ActorRef
import org.sackfix.boostrap._
import org.sackfix.common.message.SfMessage
import org.sackfix.field._
import org.sackfix.fix44._
import org.sackfix.session.SfSessionActor.{BusinessFixMsgOut, SfSessionActorCommand}
import org.sackfix.session.SfSessionId

import scala.collection.mutable

/**
  * You must implement an actor for business messages.
  * You should inject it into the SfInitiatorActor or SfAcceptorActor depending on
  * if you are a server or a client
  *
  * Backpressure is not implemented in SackFix for IO Buffer filling up on read or write.  If you want to
  * add it please feel free.  Note that you should probably NOT send out orders if you have ACKs outstanding.
  * This will pretty much avoid all back pressure issues. ie if sendMessages.size>1 wait
  */
object ClientOMSMessageActor {
  def apply(): Behavior[SfBusinessFixInfo] =
    Behaviors.setup(context => new ClientOMSMessageActor(context))
}

class ClientOMSMessageActor(context: ActorContext[SfBusinessFixInfo]) extends AbstractBehavior[SfBusinessFixInfo](context) {
  private val sentMessages = mutable.HashMap.empty[String, Long]
  private var orderId = 0
  private var isOpen = false

  override def onMessage(msg: SfBusinessFixInfo): Behavior[SfBusinessFixInfo] = {
    msg match {
      case FixSessionOpen(sessionId: SfSessionId, sfSessionActor: ActorRef[SfSessionActorCommand]) =>
        context.log.info(s"Session ${sessionId.id} is OPEN for business")
        isOpen = true
        sendANos(sfSessionActor)
      case FixSessionClosed(sessionId: SfSessionId) =>
        // Anything not acked did not make it our to the TCP layer - even if acked, there is a risk
        // it was stuck in part or full in the send buffer.  So you should worry when sending fix
        // using any tech that the message never arrives.
        context.log.info(s"Session ${sessionId.id} is CLOSED for business")
        isOpen = false
      case BusinessFixMessage(sessionId: SfSessionId, sfSessionActor: ActorRef[SfSessionActorCommand], message: SfMessage) =>
        // ignore duplicates...obviously you should check more than simply discarding.
        if (!message.header.possDupFlagField.getOrElse(PossDupFlagField(false)).value) {
          message.body match {
            case m: ExecutionReportMessage => onExecutionReport(sfSessionActor, m)
            case m@_ => context.log.warn(s"[${sessionId.id}] Received a message it cannot handle, MsgType=${message.body.msgType}")
          }
        }
      case BusinessFixMsgOutAck(sessionId: SfSessionId, sfSessionActor: ActorRef[SfSessionActorCommand], correlationId: String) =>
        // You should have a HashMap of stuff you send, and when you get this remove from your set.
        // Read the Akka IO TCP guide for ACK'ed messages and you will see
        sentMessages.get(correlationId).foreach(tstamp =>
          context.log.debug(s"$correlationId send duration = ${(System.nanoTime() - tstamp) / 1000} Micros"))
      case BusinessRejectMessage(sessionId: SfSessionId, sfSessionActor: ActorRef[SfSessionActorCommand], message: SfMessage) =>
        context.log.warn(s"Session ${sessionId.id} has rejected the message ${message.toString()}")
    }
    Behaviors.same
  }

  /**
    * @param fixSessionActor This will be a SfSessionActor, but sadly Actor ref's are not typed
    *                        as yet
    */
  def onExecutionReport(fixSessionActor: ActorRef[SfSessionActorCommand], o: ExecutionReportMessage) = {
    val symbol = o.instrumentComponent.symbolField
    val side = o.sideField

    //    println(
    //      s"""NewOrderSingle for
    //      Instrument: ${symbol}
    //      Side:       ${side}
    //      Price:      ${o.priceField.foreach(_.value)}
    //      clOrdId:    ${o.clOrdIDField.value}
    //      """)

    sendANos(fixSessionActor)
  }

  def sendANos(fixSessionActor: ActorRef[SfSessionActorCommand]) = {
    if (isOpen) {
      // validation etc..but send back the ack
      // NOTE, AKKA is Asynchronous.  You have ZERO idea if this send worked, or coincided with socket close down and so on.
      val correlationId = "NOS" + LocalDateTime.now.toString
      sentMessages(correlationId) = System.nanoTime()
      orderId += 1
      fixSessionActor ! BusinessFixMsgOut(NewOrderSingleMessage(clOrdIDField = ClOrdIDField(orderId.toString),
        instrumentComponent = InstrumentComponent(symbolField = SymbolField("JPG.GB")),
        sideField = SideField({
          if (orderId % 2 == 0) SideField.Buy else SideField.Sell
        }),
        transactTimeField = TransactTimeField(LocalDateTime.now),
        orderQtyDataComponent = OrderQtyDataComponent(orderQtyField = Some(OrderQtyField(100))),
        ordTypeField = OrdTypeField(OrdTypeField.Market)), correlationId)
    }
  }
}
