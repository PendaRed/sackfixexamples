package org.sackfix.client

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import com.typesafe.config.ConfigException
import org.sackfix.boostrap._
import org.sackfix.boostrap.initiator.{SfInitiatorBooter, SfInitiatorSettings}
import org.sackfix.session.filebasedstore.SfFileMessageStore

object SfClientSupervisor {
  def apply(): Behavior[SystemErrorNeedsDevOpsMsg] =
    Behaviors.setup[SystemErrorNeedsDevOpsMsg](context => new SfClientSupervisor(context))

}

/**
  * All configuration is read from application.conf at the root of the class path
  */
class SfClientSupervisor(context: ActorContext[SystemErrorNeedsDevOpsMsg]) extends AbstractBehavior[SystemErrorNeedsDevOpsMsg](context) {
  var booter: Option[SfInitiatorBooter] = None
  initialiseTheClient()

  override def onMessage(msg: SystemErrorNeedsDevOpsMsg): Behavior[SystemErrorNeedsDevOpsMsg] = {
    systemCloseDown(msg.humanReadableMessageForDevOps)
    Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[SystemErrorNeedsDevOpsMsg]] = {
    case PostStop =>
      context.log.info("SfClientSupervisor Application stopped")
      this
  }

  def initialiseTheClient(): Unit = {
    try {
      // Load the config into an extension object, so can get at values as fields.
      val settings = SfInitiatorSettings(context.system)

      // ***** THE IMPORTANT BIT!  Put your logic in the MessageActor *****
      // After session valdation etc all business messages are sent to this actor
      // ******************************************************************
      val businessCommsActor = context.spawn(ClientOMSMessageActor(), name = "OMSClientMsgHandler")
      val businessComms = new BusinessCommsHandler {
        override def handleFix(msg: SfBusinessFixInfo): Unit = {
          businessCommsActor ! msg
        }
      }

      // This message store is file based, and it implements both the message store
      // and the SessionOpenTodayStore, which is why it is passed in twice below as a parameter
      val msgStore = new SfFileMessageStore(settings.pathToFileStore, 100000)

      val booter = SfInitiatorBooter(context.self, context, Some(msgStore), msgStore, businessComms)
    } catch {
      case ex: ConfigException =>
        systemCloseDown("Failed to initialise due to configuration exception: " + ex.getMessage)
    }
  }

  def systemCloseDown(msg: String): Unit = {
    context.log.error(msg)
    context.log.error("Above error requires Humans/DevOps to be involved, system is terminating.")
    // Terminate like this so that the async logging can complete
    context.system.terminate()
  }
}
