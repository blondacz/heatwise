package dev.g4s.heatwise.domain

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.FiniteDuration

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
  def notReady(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Error("Not ready yet"))
  def tooOld(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Error("Original check too old"))

  def healthy(msg: String)(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Ok(msg))
  def warn(msg: String)(using clock: Clock): HealthResult = HealthResult(clock.instant(), HealthStatus.Warning(msg))
}

sealed trait HealthCheck {
  protected val healthRegistry :HealthRegistry
  def name: String
  def update(result: HealthResult): Unit = healthRegistry.notify(this, result)
  def update(status: HealthStatus)(using clock: Clock) : Unit = update(HealthResult(clock.instant(), status))
}


case class ReadinessCheck( name : String)(using clock: Clock, protected val healthRegistry: HealthRegistry) extends HealthCheck {
  healthRegistry.register(this,HealthResult.notReady).fold(e => throw e, _ => ())//TODO: not that happy about throwing exception here
}

case class LivenessCheck(name : String, maxAge: FiniteDuration)(using clock: Clock, protected val healthRegistry: HealthRegistry) extends HealthCheck {
 healthRegistry.register(this, HealthResult.notChecked).fold(e => throw e, _ => ())
}

trait HealthReporting extends Liveness with Readiness

trait HealthRegistry extends HealthReporting {
  def register(check: HealthCheck, healthResult: HealthResult):  Either[Throwable, Unit]
  def notify(check: HealthCheck, healthResult: HealthResult): Unit
}

class SimpleHealthRegistry(using clock: Clock) extends HealthRegistry {
  private val checks = new ConcurrentHashMap[HealthCheck, HealthResult]()


  def notify(check: HealthCheck, healthResult: HealthResult): Unit = {
    checks.put(check, healthResult)
  }

  override def isAlive: Boolean = {
    val now = clock.instant()
    checks.asScala
      .collect { case (check: LivenessCheck, HealthResult(timestamp, status)) =>
        val age = Duration.between(timestamp, now)
        val maxPeriodMillis = check.maxAge.toMillis
        if (age.toMillis <= maxPeriodMillis) Some(check -> status) else Some(check -> HealthStatus.Error(s"Check ${check.name} is too old: $age"))
      }
      .flatten
      .collect{ case (a: LivenessCheck, HealthStatus.Error(_)) => false}
      .isEmpty
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

  override def register(check: HealthCheck, healthResult: HealthResult): Either[Throwable, Unit] = {
    checks.asScala.keySet.find{c =>
      check.name == c.name && check.getClass == c.getClass
    }.map(c => new IllegalStateException(s"HealthCheck already exists => $c")).toLeft(notify(check, healthResult))
  }
}

