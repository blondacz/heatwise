package dev.g4s.heatwise.app

import dev.g4s.heatwise.audit.DecisionLog
import dev.g4s.heatwise.domain.Decision

trait AuditService {
  def logDecision(decision: Decision): Unit
}

object LiveAuditService extends AuditService {
  def logDecision(decision: Decision): Unit = DecisionLog.append(decision)
}
