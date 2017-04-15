package org.sackfix.client

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorContext, ActorInitializationException, ActorKilledException, ActorLogging, ActorRef, DeathPactException, OneForOneStrategy, Props}
import org.sackfix.boostrap._
import org.sackfix.boostrap.initiator.{SfInitiatorBooter, SfInitiatorSettings}
import org.sackfix.client.SfClientGuardianActor.GuardianInitialiseInMsg
import org.sackfix.fix44.NewOrderSingleMessage
import org.sackfix.session.{SfMessageStore, SfSessionId}
import org.sackfix.session.filebasedstore.SfFileMessageStore
import com.typesafe.config.ConfigException

import scala.concurrent.duration._

object SfClientGuardianActor {
  def props(): Props = Props(new SfClientGuardianActor)

  case object GuardianInitialiseInMsg

}

/**
  * All configuration is read from application.conf at the root of the class path
  */
class SfClientGuardianActor extends Actor with ActorLogging {
  var booter: Option[SfInitiatorBooter] = None

  // Included just in case you want to know about lifecycle
  override def preStart = {
    super.preStart
    log.info("Guardian prestart")
  }

  override def postStop = {
    super.postStop
    log.info("Guardian postStop")
  }

  // Included just in case you want to know about supervisor strategy
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case _: ActorInitializationException => Stop
      case _: ActorKilledException => Stop
      case _: DeathPactException => Stop
      case _: Exception => Restart
    }

  def receive = {
    case GuardianInitialiseInMsg => initialiseTheClient
    case SystemErrorNeedsDevOpsMsg(msg: String) => systemCloseDown(msg)
  }

  def initialiseTheClient: Unit = {
    try {
      // Load the config into an extension object, so can get at values as fields.
      val settings = SfInitiatorSettings(context.system)

      // ***** THE IMPORTANT BIT!  Put your logic in the MessageActor *****
      // After session valdation etc all business messages are sent to this actor
      // ******************************************************************
      val businessCommsActor = context.actorOf(ClientOMSMessageActor.props, name="OMSClientMsgHandler")
      val businessComms = new BusinessCommsHandler{
        override def handleFix(msg: SfBusinessFixInfo): Unit = {
          businessCommsActor ! msg
        }
      }

      // This message store is file based, and it implements both the message store
      // and the SessionOpenTodayStore, which is why it is passed in twice below as a parameter
      val msgStore = new SfFileMessageStore(settings.pathToFileStore, 100000)

      val booter = SfInitiatorBooter(self, context, Some(msgStore), msgStore, businessComms)
    } catch {
      case ex: ConfigException =>
        systemCloseDown("Failed to initialise due to configuration exception: " + ex.getMessage)
    }
  }

  def systemCloseDown(msg: String): Unit = {
    log.error(msg)
    log.error("Above error requires Humans/DevOps to be involved, system is terminating.")
    // Terminate like this so that the async logging can complete
    context.system.terminate()
  }
}
