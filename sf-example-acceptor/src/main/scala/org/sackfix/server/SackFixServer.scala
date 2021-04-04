package org.sackfix.server

import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.{actor => classic}

/**
  * Created by Jonathan in 2016.
  */
object SackFixServer extends App {
  // Create ActorSystem and top level supervisor
  // We are using Typed actors, but since the io.TCP actors are still classic we need
  // https://doc.akka.io/docs/akka/current/typed/coexisting.html
  val classicSystem = classic.ActorSystem("SackFixFixServer")
  val typedSystem: ActorSystem[Nothing] = classicSystem.toTyped
  classicSystem.spawn(SfServerSupervisor(), "SfServerSupervisor")

  // Lets any console errors appear if there are config problems etc
  Thread.sleep(10*1000)
}
