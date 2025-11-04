package dev.g4s.heatwise.audit

import dev.g4s.heatwise.domain.{AuditService, ControllerState, Decision, HeatwiseKafkaConfig}
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.Future

class KafkaAuditService(cfg: HeatwiseKafkaConfig)(using actorSystem: ActorSystem) extends AuditService {
  private val kafkaLog = new KafkaLog(cfg)
  def logDecision(controllerState: ControllerState, d: Decision): Unit = kafkaLog.publishDecision(controllerState, d)

  def start(): Unit = kafkaLog.start()

  def restoreState(): Future[Option[ControllerState]] = kafkaLog.restoreState()
}
