package dev.g4s.heatwise.domain

import scala.concurrent.Future

trait AuditService {
  def logDecision(controllerState: ControllerState, d: Decision): Unit

  def start() : Unit

  def restoreState(): Future[Option[ControllerState]]
}
