package dev.g4s.heatwise.domain

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.Source
import sttp.client4.Backend

import scala.concurrent.{ExecutionContext, Future}

trait CylinderTemperatureService  {
  def fetchCurrentTemperature()(using system: ActorSystem, executionContext: ExecutionContext): Source[Temperature,Cancellable]

}
