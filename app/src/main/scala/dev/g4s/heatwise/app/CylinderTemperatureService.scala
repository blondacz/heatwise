package dev.g4s.heatwise.app

import dev.g4s.heatwise.adapters.cylinder.TemperatureSensor
import dev.g4s.heatwise.domain.{PricePoint, Temperature}
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4.Backend

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

trait CylinderTemperatureService  {
  def fetchCurrentTemperature(cfg: HeatwiseConfig)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Source[Temperature,Cancellable]
  
}

class LiveCylinderTemperatureService extends CylinderTemperatureService  {

  override def fetchCurrentTemperature(cfg: HeatwiseConfig)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Source[Temperature,Cancellable] = {
    TemperatureSensor.temperature
  }
}
