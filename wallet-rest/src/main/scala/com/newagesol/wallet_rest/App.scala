package com.newagesol.wallet_rest

import akka.actor.{ActorPath, ActorSystem}
import akka.cluster.client.{ClusterClient, ClusterClientSettings}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.lightbend.constructr.coordination.zookeeper.ZookeeperCoordination
import com.newagesol.wallet_rest.Wallet.{extractEntityId, extractShardId}
import de.heikoseeberger.constructr.ConstructrExtension

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object Wallet {
  val extractShardId: ShardRegion.ExtractShardId = {
    case s: String => s"${s.substring(0, 2).hashCode % 2}"
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case s: String => (s.substring(0, 2), s)
  }
}

//@RestController
//@Configuration
//class WalletRestController {
//
//  @Autowired
//  @Qualifier(value = "walletProxy")
//  var walletProxy: ActorRef = _
//
//  @Autowired
//  @Qualifier(value = "walletClient")
//  var walletClient: ActorRef = _
//
//  @GetMapping(Array("/health"))
//  def health(): String = "OK"
//
//  @GetMapping(Array("/hello_proxy/{wallet}"))
//  def helloProxy(@PathVariable(value = "wallet") wallet: String): String = {
//    implicit val timeout = Timeout(15.seconds)
//    val res = Await.result(walletProxy ? wallet, 15.seconds)
//    s"$res"
//  }
//
//  @GetMapping(Array("/hello_client/{wallet}"))
//  def helloClient(@PathVariable(value = "wallet") wallet: String): String = {
//    implicit val timeout = Timeout(15.seconds)
//    val res = Await.result(walletClient ? ClusterClient.Send("/system/sharding/wallet", wallet, localAffinity = false), 15.seconds)
//    s"$res"
//  }
//}


object App extends App {

  implicit val actorSystem = ActorSystem.create("WalletActorSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  ConstructrExtension(actorSystem)

  val walletProxy = ClusterSharding(actorSystem).startProxy("wallet", None, extractEntityId, extractShardId)

  val zk = new ZookeeperCoordination("WalletActorSystem", actorSystem)
  val actors: Set[ActorPath] = Await.result(zk.getNodes(), 15.seconds).map(addr => {
    ActorPath.fromString(s"$addr/system/receptionist")
  })

  print(s"@@@@ actors: $actors")

  val clusterClient = actorSystem.actorOf(ClusterClient.props(ClusterClientSettings.create(actorSystem)
    .withInitialContacts(actors)), "walletClient")

  val healthRoute =
    path("health") {
      get {
        complete("OK")
      }
    }

  val bindingFuture = Http().bindAndHandle(healthRoute, "0.0.0.0", 9090)
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => actorSystem.terminate())
}