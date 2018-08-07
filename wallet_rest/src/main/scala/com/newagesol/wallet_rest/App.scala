package com.newagesol.wallet_rest

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Wallet {
  val extractShardId: ShardRegion.ExtractShardId = {
    case s: String => s"${s.substring(0, 2).hashCode % 20}"
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case s: String => (s.substring(0, 2), s)
  }
}

object App extends App {

  implicit val system = ActorSystem.create("WalletActorSystem")
  implicit val mat = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(15.seconds)

  val log = LoggerFactory.getLogger(this.getClass)

  AkkaManagement(system).start().onComplete {
    case Success(url) =>
      log.info(s"akka mgmt started @ $url")
      ClusterBootstrap(system).start()

      val shardProxy = ClusterSharding(system)
        .startProxy("wallet", None, Wallet.extractEntityId, Wallet.extractShardId)

      val route =
        path("hello_shard" / Remaining) { w =>
          get {
            onComplete(shardProxy ? w) {
              case Success(x) => complete(s"$x")
              case Failure(ex) => complete(InternalServerError, s"${ex.getMessage}")
            }
          }
        }

      val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 9090)

    case Failure(ex) =>
      log.error("akka mgmt failed to start", ex)
  }

}