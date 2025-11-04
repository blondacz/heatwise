package dev.g4s.heatwise.audit

import dev.g4s.heatwise.domain.{AuditService, ControllerState, Decision, HealthResult, LivenessCheck, ReadinessCheck}

import java.time.Clock
import scala.concurrent.Future

class FileAuditService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends AuditService {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))

  def logDecision(controllerState: ControllerState, decision: Decision): Unit = {
    livenessCheck.update(HealthResult.healthy("Ticking"))
    DecisionLog.append(decision)
  }

  def start() : Unit = ()

  def restoreState(): Future[Option[ControllerState]] = Future.successful(None)

}
