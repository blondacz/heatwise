package dev.g4s.heatwise.adapters.cylinder

import dev.g4s.heatwise.domain.HealthStatus.Ok
import dev.g4s.heatwise.domain.{CylinderTemperatureService, HealthResult, HeatwiseConfig, LivenessCheck, ReadinessCheck, Temperature}
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4.Backend

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

class LiveCylinderTemperatureService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends CylinderTemperatureService  {
  readinessCheck.update(HealthResult.healthy("Ready to serve")) 
  override def fetchCurrentTemperature(cfg: HeatwiseConfig)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Source[Temperature,Cancellable] = {
    TemperatureSensor.temperature.wireTap(t => livenessCheck.update(Ok("Last reported temperature: $t")))
  }
}
