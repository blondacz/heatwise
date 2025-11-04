package dev.g4s.heatwise.domain

import org.apache.pekko.actor.ActorSystem
import sttp.client4.Backend

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

trait PriceService {
  def fetchCurrentPrice(cfg: HeatwiseConfig, now: ZonedDateTime)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[PricePoint]
}
