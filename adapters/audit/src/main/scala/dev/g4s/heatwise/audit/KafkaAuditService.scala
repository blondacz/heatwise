package dev.g4s.heatwise.audit

import dev.g4s.heatwise.domain.{AuditService, ControllerState, Decision, HealthResult, HeatwiseKafkaConfig, LivenessCheck, ReadinessCheck}
import org.apache.pekko.actor.ActorSystem

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

class KafkaAuditService(cfg: HeatwiseKafkaConfig, livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using actorSystem: ActorSystem, clock: Clock, ex: ExecutionContext) extends AuditService {
  private val kafkaLog = new KafkaLog(cfg)
  def logDecision(controllerState: ControllerState, d: Decision): Unit = {
    livenessCheck.update(HealthResult.healthy(s"Last state: ${controllerState} Decision: $d"))
    kafkaLog.publishDecision(controllerState, d)
  }

  def start(): Unit = kafkaLog.start()

  def restoreState(): Future[Option[ControllerState]] = {
    kafkaLog.restoreState().map(c => {
      readinessCheck.update(HealthResult.healthy(s"Restored last state: $c"))  
      c
    })
    
  }
}
