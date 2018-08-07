package com.newagesol.wallet

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.event.LoggingReceive
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import com.newagesol.wallet.Wallet.{extractEntityId, extractShardId}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

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
  implicit val mat: ActorMaterializer = ActorMaterializer()

  val log = LoggerFactory.getLogger(this.getClass)

  AkkaManagement(system).start().onComplete {
    case Success(url) =>
      log.info(s"akka mgmt started @ $url")
      ClusterBootstrap(system).start()
      ClusterSharding(system).start("wallet", Props(classOf[Wallet]),
        ClusterShardingSettings(system), extractEntityId, extractShardId)
    case Failure(ex) =>
      log.error("akka mgmt failed to start", ex)
  }

}
