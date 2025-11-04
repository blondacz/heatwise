package dev.g4s.heatwise.adapters.octopus

import dev.g4s.heatwise.domain.{HeatwiseConfig, PriceService}
import dev.g4s.heatwise.domain.*
import org.apache.pekko.actor.ActorSystem
import sttp.client4.Backend

import java.time.{Clock, LocalDateTime, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}

class LivePriceService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends PriceService {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))
  def fetchCurrentPrice(cfg: HeatwiseConfig, now: ZonedDateTime)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[PricePoint] = {
    livenessCheck.update(HealthResult.healthy("Ticking"))
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
