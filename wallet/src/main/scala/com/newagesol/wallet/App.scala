package com.newagesol.wallet

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.event.LoggingReceive
import akka.management.AkkaManagement
import akka.stream.ActorMaterializer
import com.newagesol.wallet.Wallet.{extractEntityId, extractShardId}

import scala.concurrent.ExecutionContextExecutor

class Wallet extends Actor with ActorLogging {
  def receive = LoggingReceive {
    case message: String =>
      sender ! s"Reply to $message from HOSTNAME=${System.getenv("HOSTNAME")}, " +
        s"CONTAINER_IP=${InetAddress.getLocalHost.getHostAddress}, " +
        s"name=${self.path}"
  }
}

object Wallet {
  val extractShardId: ShardRegion.ExtractShardId = {
    case s: String => s"${s.substring(0, 2).hashCode % 20}"
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case s: String => (s.substring(0, 2), s)
  }
}

object App extends App {
  implicit val system: ActorSystem = ActorSystem("WalletActorSystem")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val ctx: ActorMaterializer = ActorMaterializer()

  AkkaManagement(system).start()

  ClusterSharding(system).start("wallet", Props(classOf[Wallet]),
    ClusterShardingSettings(system), extractEntityId, extractShardId)
}
