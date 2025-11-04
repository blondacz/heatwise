package dev.g4s.heatwise.app

import dev.g4s.heatwise.audit.KafkaLog
import dev.g4s.heatwise.domain.*
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import org.apache.pekko.stream.SourceShape
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}
import sttp.client4.*

import java.time.{Clock, Instant, ZonedDateTime, Duration as JDuration}
import scala.concurrent.Future
import scala.concurrent.duration.*

class HeatwiseApp(
    priceService: PriceService,
    relayService: RelayService,
    auditService: AuditService,
    temperatureService: CylinderTemperatureService
)(using system: ActorSystem, backend: Backend[Future]) {

  import system.dispatcher

  def priceSource(cfg: HeatwiseConfig): Source[PricePoint, Cancellable] =
    Source.tick(0.seconds, cfg.checkInterval, ()).mapAsync(1) { _ =>
      priceService.fetchCurrentPrice(cfg, ZonedDateTime.now())
    }


  def combinedSource(cfg: HeatwiseConfig): Source[(PricePoint, Temperature), NotUsed]  =
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits.*
      val zip = b.add(ZipLatest[PricePoint, Temperature]())
      priceSource(cfg) ~> zip.in0
      temperatureService.fetchCurrentTemperature(cfg) ~> zip.in1
      SourceShape(zip.out)
    }).dropRepeated() //FIXME: dropRepeated maye interfere with hysteresis, may need to change tests and drop
    
    
  def decisionStream(cs: Option[ControllerState], combinedSource: Source[(PricePoint, Temperature), NotUsed] , policy: Policy)(using clock: Clock): Source[(ControllerState,Decision), NotUsed] =
    combinedSource
      .statefulMap(() => cs) (
        { case (lastChange, (price, temp)) => 
          val d = Decide.decide(clock, price, temp, lastChange, policy)
          val lc = if (lastChange.forall(_.lastOn != d.heatOn)) Some(ControllerState(d.ts, d.heatOn)) else lastChange
          (lc, (lc.get,d))//get here is safe but not that nice
        },
        _ => None
  )
      

  def executionStream(cfg: HeatwiseConfig, decisions: Source[(ControllerState,Decision), NotUsed]): Source[Unit, NotUsed] =
    decisions.mapAsync(1) { case (s,d) =>
      val f = relayService.switchRelay(cfg.relayHost, d.heatOn, cfg.dummyRun)
      f.collect {
        case Left(value) =>
          system.log.error(value, s"Failed to switch relay for decision: $d")
          auditService.logDecision(s,d)//FIXME: log failures to change and maybe do not log the desired state?
        case Right(value) =>
          system.log.info(s"Switch relay for decision: $d switch: $value")
          auditService.logDecision(s, d)
      }
    }

  def run(cfg: HeatwiseConfig, policy: Policy)(using clock: Clock): Future[Done] = {
    auditService.start()
    auditService.restoreState().flatMap(cs =>
      executionStream(cfg, decisionStream(cs, combinedSource(cfg),policy)).toMat(Sink.ignore)(Keep.right).run()
    )
  }
}
