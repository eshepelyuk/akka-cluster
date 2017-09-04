package com.newagesol.wallet

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.event.{Logging, LoggingReceive}
import akka.stream.ActorMaterializer
import com.newagesol.wallet.Wallet.{extractEntityId, extractShardId}
import de.heikoseeberger.constructr.ConstructrExtension

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
  implicit val actorSystem: ActorSystem = ActorSystem("WalletActorSystem")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  ConstructrExtension(actorSystem)

  val walletShard = ClusterSharding(actorSystem).start("wallet", Props(classOf[Wallet]),
    ClusterShardingSettings(actorSystem), extractEntityId, extractShardId)

  Logging(actorSystem, this.getClass).info(s"@@@@ ${walletShard.path}")
  ClusterClientReceptionist(actorSystem).registerService(walletShard)
}
