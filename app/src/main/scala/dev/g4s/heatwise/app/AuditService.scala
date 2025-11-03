package dev.g4s.heatwise.app

import dev.g4s.heatwise.audit.DecisionLog
import dev.g4s.heatwise.domain.{Decision, HealthResult, LivenessCheck, ReadinessCheck}

import java.time.Clock

trait AuditService {
  def logDecision(decision: Decision): Unit
}

class LiveAuditService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends AuditService {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))
  def logDecision(decision: Decision): Unit = {
    livenessCheck.update(HealthResult.healthy("Ticking"))
    DecisionLog.append(decision)
  }
}
