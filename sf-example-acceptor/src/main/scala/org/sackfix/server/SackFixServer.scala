package org.sackfix.server

import akka.actor.ActorSystem

/**
  * Created by Jonathan in 2016.
  */
object SackFixServer extends App {
  // This will read reference.conf and then application.conf and configure the actor system
  val system = ActorSystem("SackFixFixServer")
  val fixGuardian = system.actorOf(SfServerGuardianActor.props(), name="SfServerGuardianActor")

  fixGuardian ! SfServerGuardianActor.GuardianInitialiseInMsg

  // Lets any console errors appear if there are config problems etc
  Thread.sleep(10*1000)
}
