package org.sackfix.server

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorInitializationException, ActorKilledException, ActorLogging, DeathPactException, OneForOneStrategy, Props}
import org.sackfix.boostrap.{BusinessCommsHandler, BusinessFixMessage, SfBusinessFixInfo, SystemErrorNeedsDevOpsMsg}
import org.sackfix.boostrap.acceptor._
import org.sackfix.server.SfServerGuardianActor._
import org.sackfix.session.filebasedstore.SfFileMessageStore
import com.typesafe.config.ConfigException

import scala.concurrent.duration._

/**
  * Created by Jonathan in 2016.
  */
object SfServerGuardianActor {
  def props(): Props = Props(new SfServerGuardianActor)

  case object GuardianInitialiseInMsg

}

/**
  * All configuration is read from application.conf at the root of the class path
  */
class SfServerGuardianActor extends Actor with ActorLogging {
  var booter: Option[SfAcceptorBooter] = None

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
    case GuardianInitialiseInMsg => initialiseTheServer
    case SystemErrorNeedsDevOpsMsg(msg: String) => systemCloseDown(msg)
  }

  def initialiseTheServer: Unit = {
    try {
      // Load the config into an extension object, so can get at values as fields.
      val settings = SfAcceptorSettings(context.system)

      // ***** THE IMPORTANT BIT!  Put your logic in the MessageActor *****
      // After session valdation etc all business messages are sent to this actor
      // ******************************************************************
      val businessCommsActor = context.actorOf(OMSMessageInActor.props, name="OMSMsgHandler")
      val businessComms = new BusinessCommsHandler{
        override def handleFix(msg:SfBusinessFixInfo): Unit = {
          businessCommsActor ! msg
        }
      }

      // This message store is file based, and it implements both the message store
      // and the SessionOpenTodayStore, which is why it is passed in twice below as a parameter
      val msgStore = new SfFileMessageStore(settings.pathToFileStore)

      booter = Some(new SfAcceptorBooter(self, context, Some(msgStore), msgStore, businessComms))
    } catch {
      case ex: ConfigException =>
        systemCloseDown("Failed to initialise due to configuration exception: " + ex.getMessage)
    }
  }

  def systemCloseDown(msg: String): Unit = {
    booter.foreach(_.closeDown)

    log.error(msg)
    log.error("Above error requires Humans/DevOps to be involved, system is terminating.")
    // Terminate like this so that the async logging can complete
    context.system.terminate()
  }
}
