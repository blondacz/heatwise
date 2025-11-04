package dev.g4s.heatwise.adapters.cylinder

import dev.g4s.heatwise.domain.{CylinderTemperatureService, HeatwiseConfig, Temperature}
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4.Backend

import scala.concurrent.{ExecutionContext, Future}

class LiveCylinderTemperatureService extends CylinderTemperatureService  {

  override def fetchCurrentTemperature(cfg: HeatwiseConfig)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Source[Temperature,Cancellable] = {
    TemperatureSensor.temperature
  }
}
