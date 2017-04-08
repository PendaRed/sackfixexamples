package org.sackfix.client

import akka.actor.ActorSystem

/**
  * Created by Jonathan during 2016
  */
object SackFixClient extends App {
  // This will read reference.conf and then application.conf and configure the actor system
  val system = ActorSystem("SackFixClient")
  val fixGuardian = system.actorOf(SfClientGuardianActor.props(), name="SfClientGuardianActor")

  fixGuardian ! SfClientGuardianActor.GuardianInitialiseInMsg

  // Lets any console errors appear if there are config problems etc
  Thread.sleep(10*1000)
}
