package org.sackfix.server

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.sackfix.boostrap._
import org.sackfix.common.message.SfMessage
import org.sackfix.field._
import org.sackfix.fix44._
import org.sackfix.session.SfSessionId

import scala.collection.mutable

/** You must implement an actor for business messages.
  * You should inject it into the SfInitiatorActor or SfAcceptorActor depending on
  * if you are a server or a client
  *
  * Backpressure is not implemented in SackFix for IO Buffer filling up on read or write.  If you want to
  * add it please feel free.  Note that you should probably NOT send out orders if you have ACKs outstanding.
  * This will pretty much avoid all back pressure issues. ie if sendMessages.size>1 wait
  */
object OMSMessageInActor {
  def props(): Props = Props(new OMSMessageInActor)
}

class OMSMessageInActor extends Actor with ActorLogging {
  private val sentMessages = mutable.HashMap.empty[String, Long]
  private var isOpen = false

  override def receive: Receive = {
    case FixSessionOpen(sessionId: SfSessionId, sfSessionActor: ActorRef) =>
      log.info(s"Session ${sessionId.id} is OPEN for business")
      isOpen = true
    case FixSessionClosed(sessionId: SfSessionId) =>
      log.info(s"Session ${sessionId.id} is CLOSED for business")
      isOpen = false
    case BusinessFixMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      message.body match {
        case m: NewOrderSingleMessage => onNewOrderSingle(sfSessionActor, m)
        case m@_ => log.warning(s"[${sessionId.id}] Received a message it cannot handle, MsgType=${message.body.msgType}")
      }
    case BusinessFixMsgOutAck(sessionId: SfSessionId, sfSessionActor: ActorRef, correlationId:String) =>
      // You should have a HashMap of stuff you send, and when you get this remove from your set.
      // Read the Akka IO TCP guide for ACK'ed messages and you will see
      sentMessages.get(correlationId).foreach(tstamp =>
        log.debug(s"$correlationId send duration = ${(System.nanoTime()-tstamp)/1000} Micros"))
    case BusinessRejectMessage(sessionId: SfSessionId, sfSessionActor: ActorRef, message: SfMessage) =>
      log.warning(s"Session ${sessionId.id} has rejected the message ${message.toString()}")
  }

  /**
    * @param fixSessionActor This will be a SfSessionActor, but sadly Actor ref's are not typed
    *                        as yet
    */
  def onNewOrderSingle(fixSessionActor: ActorRef, o: NewOrderSingleMessage) = {
    val symbol = o.instrumentComponent.symbolField
    val side = o.sideField
    val quantity = o.orderQtyDataComponent.orderQtyField.getOrElse(OrderQtyField(0)).value

    //    println(
    //      s"""NewOrderSingle for
    //      Instrument: ${symbol}
    //      Side:       ${side}
    //      Quantity:   ${quantity}
    //      Price:      ${o.priceField.foreach(_.value)}
    //      clOrdId:    ${o.clOrdIDField.value}
    //      """)

    // validation etc..but send back the ack
    // NOTE, AKKA is Asynchronous.  You have ZERO idea if this send worked, or coincided with socket close down and so on.
    if (isOpen) {
      val correlationId = "ExecutionReport" + LocalDateTime.now.toString
      sentMessages(correlationId) = System.nanoTime()
      fixSessionActor ! BusinessFixMsgOut(ExecutionReportMessage(orderIDField = OrderIDField("1"),
        execIDField = ExecIDField("exec1"),
        execTypeField = ExecTypeField(ExecTypeField.New),
        ordStatusField = OrdStatusField(OrdStatusField.New),
        instrumentComponent = InstrumentComponent(symbolField = symbol),
        sideField = side,
        leavesQtyField = LeavesQtyField(quantity),
        cumQtyField = CumQtyField(0),
        avgPxField = AvgPxField(0)), correlationId)
    }
  }
}