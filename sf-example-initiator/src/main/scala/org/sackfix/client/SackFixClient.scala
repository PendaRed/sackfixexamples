package org.sackfix.client

import akka.{actor => classic}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps

/**
  * Created by Jonathan during 2016, updated 2021
  */
object SackFixClient extends App {
  // This will read reference.conf and then application.conf and configure the actor system
  // We are using Typed actors, but since the io.TCP actors are still classic we need
  // https://doc.akka.io/docs/akka/current/typed/coexisting.html
  val classicSystem = classic.ActorSystem("SackFixClient")
  val typedSystem: ActorSystem[Nothing] = classicSystem.toTyped
  classicSystem.spawn(SfClientSupervisor(), "SackFixClientSupervisor")

  // Lets any console errors appear if there are config problems etc
  Thread.sleep(10*1000)
}
