package org.sackfix.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import com.typesafe.config.ConfigException
import org.sackfix.boostrap.acceptor._
import org.sackfix.boostrap.{BusinessCommsHandler, SfBusinessFixInfo, SystemErrorNeedsDevOpsMsg}
import org.sackfix.session.filebasedstore.SfFileMessageStore

/**
  * Created by Jonathan in 2016.
  */
object SfServerSupervisor {
  def apply(): Behavior[SystemErrorNeedsDevOpsMsg] =
    Behaviors.setup[SystemErrorNeedsDevOpsMsg](context => new SfServerSupervisor(context))
}

/**
  * All configuration is read from application.conf at the root of the class path
  */
class SfServerSupervisor(context: ActorContext[SystemErrorNeedsDevOpsMsg]) extends AbstractBehavior[SystemErrorNeedsDevOpsMsg](context) {
  var booter: Option[SfAcceptorBooter] = None
  context.log.info("SfServerGuardianActor Application started")
  initialiseTheServer()

  override def onMessage(msg: SystemErrorNeedsDevOpsMsg): Behavior[SystemErrorNeedsDevOpsMsg] = {
    // No need to handle any messages
    systemCloseDown(msg.humanReadableMessageForDevOps)
    Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[SystemErrorNeedsDevOpsMsg]] = {
    case PostStop =>
      context.log.info("SfServerSupervisor Application stopped")
      this
  }

  def initialiseTheServer(): Unit = {
    try {
      // Load the config into an extension object, so can get at values as fields.
      val settings = SfAcceptorSettings(context.system)

      // ***** THE IMPORTANT BIT!  Put your logic in the MessageActor *****
      // After session valdation etc all business messages are sent to this actor
      // ******************************************************************
      //      val businessCommsActor = context.actorOf(OMSMessageInActor.props_), "OMSMsgHandler")
      val businessCommsActor = context.spawn(OMSMessageInActor(), "OMSMsgHandler")
      val businessComms = new BusinessCommsHandler {
        override def handleFix(msg: SfBusinessFixInfo): Unit = {
          businessCommsActor ! msg
        }
      }

      // This message store is file based, and it implements both the message store
      // and the SessionOpenTodayStore, which is why it is passed in twice below as a parameter
      val msgStore = new SfFileMessageStore(settings.pathToFileStore, 100000)

      booter = Some(SfAcceptorBooter(context.self, context, Some(msgStore), msgStore, businessComms))
    } catch {
      case ex: ConfigException =>
        systemCloseDown("Failed to initialise due to configuration exception: " + ex.getMessage)
    }
  }

  def systemCloseDown(msg: String): Unit = {
    booter.foreach(_.closeDown)

    context.log.error(msg)
    context.log.error("Above error requires Humans/DevOps to be involved, system is terminating.")
    // Terminate like this so that the async logging can complete
    context.system.terminate()
  }
}
