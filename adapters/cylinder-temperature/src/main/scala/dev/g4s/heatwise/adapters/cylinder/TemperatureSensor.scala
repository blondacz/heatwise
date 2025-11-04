package dev.g4s.heatwise.adapters.cylinder

import org.apache.pekko.actor.Cancellable
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random
import dev.g4s.heatwise.domain.Temperature
import scala.concurrent.ExecutionContext.Implicits.global

object TemperatureSensor {
  def temperature: Source[Temperature, Cancellable] = {
    Source.tick(0.seconds, 1.minute, ()).mapAsync(1) { _ =>
      Future.successful(Random.nextInt(50) + 15).map(a => Temperature(BigDecimal(a)))
    }
  }
}
