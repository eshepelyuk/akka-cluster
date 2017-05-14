package com.newagesol.wallet_rest

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.pattern._
import akka.util.Timeout
import com.newagesol.wallet_rest.Wallet.{extractEntityId, extractShardId}
import de.heikoseeberger.constructr.ConstructrExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.{DataSourceAutoConfiguration, DataSourceTransactionManagerAutoConfiguration}
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.{SpringApplication, SpringBootConfiguration}
import org.springframework.cloud.config.client.DiscoveryClientConfigServiceBootstrapConfiguration
import org.springframework.context.annotation.{Bean, Configuration, Import}
import org.springframework.web.bind.annotation.{GetMapping, PathVariable, RestController}

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

@RestController
@Configuration
class WalletRestController {

  @Autowired
  var walletClient: ActorRef = _

  @GetMapping(Array("/health"))
  def health(): String = "OK"

  @GetMapping(Array("/hello/{wallet}"))
  def hello(@PathVariable(value = "wallet") wallet: String): String = {
    implicit val timeout = Timeout(15.seconds)
    val res = Await.result(walletClient ? wallet, 15.seconds)
    s"$res"
  }
}

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = Array(classOf[DataSourceAutoConfiguration],
  classOf[HibernateJpaAutoConfiguration],
  classOf[DataSourceTransactionManagerAutoConfiguration],
  classOf[DiscoveryClientConfigServiceBootstrapConfiguration]
))
@Import(Array(classOf[WalletRestController]))
class WalletRestSpringConfig {

  @Bean def actorSystem(): ActorSystem = {
    val retval = ActorSystem.create("WalletActorSystem")
    ConstructrExtension(retval)
    retval
  }

  @Bean def clusterClient(actorSystem: ActorSystem): ActorRef = {
    ClusterSharding(actorSystem).startProxy("wallet", None, extractEntityId, extractShardId)
  }

  //  @Bean def clusterClient(actorSystem: ActorSystem): ActorRef = {
  //    val zk = new ZookeeperCoordination("WalletActorSystem", actorSystem)
  //
  //    val actors: Set[ActorPath] = Await.result(zk.getNodes(), 15.seconds).map(addr => {
  //      ActorPath.fromString(s"$addr/system/receptionist")
  //    })
  //
  //    actorSystem.actorOf(ClusterClient.props(
  //      ClusterClientSettings.create(actorSystem).withInitialContacts(actors)), "walletClient")
  //  }
}

object App extends App {
  SpringApplication.run(classOf[WalletRestSpringConfig], args: _*)
}