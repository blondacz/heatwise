package dev.g4s.heatwise.app

import dev.g4s.heatwise.adapters.octopus.OctopusClient
import dev.g4s.heatwise.domain._
import org.apache.pekko.actor.ActorSystem
import sttp.client4.Backend

import java.time.{LocalDateTime, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}

trait PriceService {
  def fetchCurrentPrice(cfg: HeatwiseConfig, now: ZonedDateTime)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[PricePoint]
}

object LivePriceService extends PriceService {
  def fetchCurrentPrice(cfg: HeatwiseConfig, now: ZonedDateTime)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[PricePoint] = {
    val priceRequest = PriceRequest.fromDateTimForDuration(cfg.productCode, cfg.tariffCode, LocalDateTime.now(), java.time.Duration.ofHours(2))
    OctopusClient
      .fetchPrices(OctopusClient.urlForStandardUnitRates(priceRequest))
      .collect {
        case Left(ex) =>
          system.log.error(ex, "Failed to fetch prices")
          PricePoint.MaxPricePerKWh(now)
        case Right(PriceResponse(res)) =>
          res.sortBy(_.validFrom).find(p => !p.validFrom.isAfter(now)).getOrElse(res.headOption.getOrElse(PricePoint.MaxPricePerKWh(now)))
      }
  }
}
