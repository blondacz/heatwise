package dev.g4s.heatwise.adapters.cylinder

import dev.g4s.heatwise.domain.HealthStatus.Ok
import dev.g4s.heatwise.domain.{CylinderTemperatureService, HealthResult, HeatwiseConfig, LivenessCheck, ReadinessCheck, Temperature, TemperatureSensorConfig}
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4.Backend

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

class LiveCylinderTemperatureService(cfg: TemperatureSensorConfig, livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends CylinderTemperatureService  {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))
  private val temperatureSensor  =  new TemperatureSensor(cfg) 
  override def fetchCurrentTemperature()(using system: ActorSystem, executionContext: ExecutionContext): Source[Temperature,Cancellable] = {
    temperatureSensor.temperature.wireTap(t => livenessCheck.update(Ok(s"Last reported temperature: $t")))
  }
}
