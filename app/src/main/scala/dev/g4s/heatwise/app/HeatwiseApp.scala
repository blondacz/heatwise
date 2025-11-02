package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.{Decide, Decision, Policy, PricePoint}
import org.apache.pekko.Done
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import sttp.client4.Backend

import java.time.{Clock, Instant, ZonedDateTime}
import scala.concurrent.Future
import dev.g4s.heatwise.domain.*
import dev.g4s.heatwise.adapters.octopus.OctopusClient
import dev.g4s.heatwise.adapters.relay.*
import dev.g4s.heatwise.audit.DecisionLog
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}

import java.time.{Duration as JDuration, *}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import sttp.client4.*
import sttp.client4.pekkohttp.*

class HeatwiseApp(
    priceService: PriceService,
    relayService: RelayService,
    auditService: AuditService
)(using system: ActorSystem, backend: Backend[Future]) {

  import system.dispatcher

  def priceSource(cfg: HeatwiseConfig): Source[PricePoint, Cancellable] =
    Source.tick(0.seconds, cfg.checkInterval, ()).mapAsync(1) { _ =>
      priceService.fetchCurrentPrice(cfg, ZonedDateTime.now())
    }

  def decisionStream(priceSource: Source[PricePoint, Cancellable] , policy: Policy)(using clock: Clock): Source[Decision, Cancellable] =
    priceSource
      .statefulMap(() => Option.empty[(Instant, Boolean)])(
        (lastChange, p) => {
          val d = Decide.decide(clock, p, lastChange, policy)
          val lc = if (lastChange.forall(_._2 != d.heatOn)) Some((d.ts, d.heatOn)) else lastChange
          (lc, d)
        },
        _ => None
      )

  def executionStream(cfg: HeatwiseConfig, decisions: Source[Decision, Cancellable]): Source[Unit, Cancellable] =
    decisions.mapAsync(1) { d =>
      val f = relayService.switchRelay(cfg.relayHost, d.heatOn, cfg.dummyRun)
      f.collect {
        case Left(value) =>
          system.log.error(value, s"Failed to switch relay for decision: $d")
          auditService.logDecision(d)
        case Right(value) =>
          system.log.info(s"Switch relay for decision: $d switch: $value")
          auditService.logDecision(d)
      }
    }

  def run(cfg: HeatwiseConfig, policy: Policy)(using clock: Clock): Future[Done] =
    executionStream(cfg, decisionStream(priceSource(cfg),policy)).toMat(Sink.ignore)(Keep.right).run()
}
