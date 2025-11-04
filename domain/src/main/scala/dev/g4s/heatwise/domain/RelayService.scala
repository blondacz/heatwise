package dev.g4s.heatwise.domain

import org.apache.pekko.actor.ActorSystem
import sttp.client4.Backend

import scala.concurrent.{ExecutionContext, Future}

trait RelayService {
  def switchRelay(host: String, on: Boolean, dummyRun: Boolean)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[Either[Throwable, Boolean]]
}
