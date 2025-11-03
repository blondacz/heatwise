package dev.g4s.heatwise.domain

import java.time.{Clock, Instant}
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters.*

trait Liveness {
  def isAlive: Boolean
  def livenessDetails: Map[String, HealthResult]
}
trait Readiness {
  def isReady: Boolean
  def readinessDetails: Map[String, HealthResult]
}

enum HealthStatus {
  case Ok(msg : String)
  case Warning(msg: String)
  case Error(msg: String)
}

case class HealthResult (
 timestamp: Instant,
 status: HealthStatus
)

object HealthResult {
  def notChecked(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Warning("Not checked yet"))

  def healthy(msg: String)(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Ok(msg))
  def warn(msg: String)(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Warning(msg))
}

sealed trait HealthCheck {
  protected val healthRegistry :HealthRegistry
  def name: String
  def update(result: HealthResult): Unit = healthRegistry.notify(this, result)
}

case class ReadinessCheck( name : String)(using clock: Clock, protected val healthRegistry: HealthRegistry) extends HealthCheck {
  healthRegistry.notify(this,HealthResult.notChecked)
}

case class LivenessCheck( name : String)(using clock: Clock, protected val healthRegistry: HealthRegistry) extends HealthCheck {
 healthRegistry.notify(this, HealthResult.notChecked)
}

trait HealthReporting extends Liveness with Readiness

trait HealthRegistry extends HealthReporting {
  def notify(check: HealthCheck, healthResult: HealthResult): Unit
}

class SimpleHealthRegistry(using clock: Clock) extends HealthRegistry {
  private val checks = new ConcurrentHashMap[HealthCheck, HealthResult]()

  def notify(check: HealthCheck, healthResult: HealthResult): Unit = {
    checks.put(check, healthResult)
  }

  override def isAlive: Boolean = {
    checks.asScala.map((k,v) => (k,v.status) ).collect{case (a: LivenessCheck, b: HealthStatus.Error) => a}.isEmpty
  }

  override def isReady: Boolean = {
    checks.asScala.map((k,v) => (k,v.status) ).collect{case (a: ReadinessCheck, b: HealthStatus.Error) => a}.isEmpty
  }

  override def livenessDetails: Map[String, HealthResult] = checks.asScala.collect {
      case (a: LivenessCheck, b: HealthResult) => a.name -> b
    }.toMap

  override def readinessDetails: Map[String, HealthResult] = checks.asScala.collect {
    case (a: ReadinessCheck, b: HealthResult) => a.name -> b
  }.toMap
}

