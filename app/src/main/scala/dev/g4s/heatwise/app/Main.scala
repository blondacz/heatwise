package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.*
import dev.g4s.heatwise.adapters.octopus.{LivePriceService, OctopusClient}
import dev.g4s.heatwise.adapters.relay.*
import dev.g4s.heatwise.adapters.cylinder.LiveCylinderTemperatureService
import dev.g4s.heatwise.audit.{DecisionLog, FileAuditService, KafkaAuditService}
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}

import java.time.{Duration as JDuration, *}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import sttp.client4.*
import sttp.client4.pekkohttp.*


object Main {
  given system: ActorSystem = ActorSystem("heatwise")
  given backend: Backend[Future] = PekkoHttpBackend.usingActorSystem(system)

  import system.dispatcher


  def main(args: Array[String]): Unit = {
    val cfg = HeatwiseConfig.loadOrThrow()
    given clock: Clock = Clock.systemUTC()
    system.log.info(s"Starting with config: $cfg")

    val policy = Policy(
      maxPricePerKWh = cfg.maxPricePerKWh,
      morningPreheat = cfg.morningPreheat.map(s => PreheatBefore(s, JDuration.ofMinutes(30))),
      desiredTemperature = Temperature(cfg.desiredTemperature),
      delay = Delay()
    )

    given healthRegistry : HealthRegistry = new SimpleHealthRegistry()

    Http().newServerAt("0.0.0.0", 8080).bind(HealthRoutes.routes(healthRegistry, healthRegistry, Map("config" -> cfg.toString), cfg))

    val app = new HeatwiseApp(
      new LivePriceService(LivenessCheck("price", 20.minutes), ReadinessCheck("price")),
      new LiveRelayService(LivenessCheck("relay", 20.minutes), ReadinessCheck("relay")),
      new KafkaAuditService(cfg.kafka, LivenessCheck("kafka-audit", 20.minutes), ReadinessCheck("kafka-audit")),
      new LiveCylinderTemperatureService(LivenessCheck("cylinder-temperature", 20.minutes), ReadinessCheck("cylinder-temperature")))
    val run = app.run(cfg, policy)

    run.onComplete(_ => system.terminate())
  }
}