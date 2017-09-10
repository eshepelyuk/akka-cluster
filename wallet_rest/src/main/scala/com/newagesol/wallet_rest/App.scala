package com.newagesol.wallet_rest

import akka.actor.{ActorPath, ActorSystem, RootActorPath}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.heikoseeberger.constructr.ConstructrExtension
import de.heikoseeberger.constructr.coordination.Coordination

import scala.concurrent.Await
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

  implicit val actorSystem = ActorSystem.create("WalletActorSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit val timeout = Timeout(15.seconds)

  val zk = Coordination("WalletActorSystem", actorSystem)

  val rootActorPathes: Set[ActorPath] = Await.result(zk.getNodes(), 15.seconds).map(addr => {
    RootActorPath(addr)
  })

  val clusterClient = actorSystem.actorOf(
    ClusterClient
      .props(ClusterClientSettings(actorSystem)
        .withInitialContacts(rootActorPathes.map(_ / "system" / "receptionist"))),
    "walletClusterClient")

  ConstructrExtension(actorSystem)

  val shardProxy = ClusterSharding(actorSystem)
    .startProxy("wallet", None, Wallet.extractEntityId, Wallet.extractShardId)

  val route =
    path("hello_shard" / Remaining) { w =>
      get {
        onComplete(shardProxy ? w) {
          case Success(x) => complete(s"$x")
          case Failure(ex) => complete(InternalServerError, s"${ex.getMessage}")
        }
      }
    } ~ path("hello_client" / Remaining) { w =>
      get {
        onComplete(clusterClient ? ClusterClient.Send("/system/sharding/wallet", w, localAffinity = false)) {
          case Success(x) => complete(s"$x")
          case Failure(ex) => complete(InternalServerError, s"${ex.getMessage}")
        }
      }
    } ~ path("hello_cluster" / Remaining) { w =>
      get {
        onComplete(actorSystem.actorSelection(rootActorPathes.head / "user" / "someActor") ? w) {
          case Success(x) => complete(s"$x")
          case Failure(ex) => complete(InternalServerError, s"${ex.getMessage}")
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 9090)

}