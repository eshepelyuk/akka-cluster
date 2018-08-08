package com.newagesol.wallet

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery.{Resolved, ResolvedTarget}
import akka.discovery.{Lookup, SimpleServiceDiscovery}

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class SwarmServiceDiscovery(system: ActorSystem) extends SimpleServiceDiscovery {
  override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] = {
    import system._
    Future(
      Resolved(lookup.serviceName, InetAddress.getAllByName(s"tasks.${lookup.serviceName}").to[Seq].map(a => ResolvedTarget(a.getHostAddress, None)))
    )
  }
}
