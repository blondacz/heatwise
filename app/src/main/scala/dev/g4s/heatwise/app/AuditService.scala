package dev.g4s.heatwise.app

import dev.g4s.heatwise.audit.{DecisionLog, HeatwiseKafkaConfig, KafkaLog}
import dev.g4s.heatwise.domain.{ControllerState, Decision, HealthResult, LivenessCheck, ReadinessCheck}
import org.apache.pekko.actor.ActorSystem

import java.time.Clock
import scala.concurrent.Future

trait AuditService {
  def logDecision(controllerState: ControllerState, d: Decision): Unit

  def start() : Unit
  
  def restoreState(): Future[Option[ControllerState]]
}

class KafkaAuditService(cfg: HeatwiseKafkaConfig)(using actorSystem: ActorSystem) extends AuditService {
  private val kafkaLog = new KafkaLog(cfg) 
  def logDecision(controllerState: ControllerState, d: Decision): Unit = kafkaLog.publishDecision(controllerState, d)

  def start(): Unit = kafkaLog.start()

  def restoreState(): Future[Option[ControllerState]] = kafkaLog.restoreState() 
}

class FileAuditService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends AuditService {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))

  def logDecision(controllerState: ControllerState, decision: Decision): Unit = {
    livenessCheck.update(HealthResult.healthy("Ticking"))
    DecisionLog.append(decision)
  }
  
  def start() : Unit = ()

  def restoreState(): Future[Option[ControllerState]] = Future.successful(None)
  
}
