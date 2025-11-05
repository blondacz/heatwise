package dev.g4s.heatwise.domain

import org.scalatest.OneInstancePerTest
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.concurrent.duration.*

class MutableClock(var now: Instant, zone: ZoneId = ZoneOffset.UTC) extends Clock {
  def adjust(offset: FiniteDuration): Unit = now = now.plusMillis(offset.toMillis)
  override def getZone: ZoneId = zone
  override def instant(): Instant = now
  override def withZone(zone: ZoneId): Clock =  new MutableClock(now, zone)
}

class SimpleHealthRegistryTest extends AnyFreeSpec with Matchers with OneInstancePerTest {
  given clock : MutableClock = new MutableClock(Instant.EPOCH)

  "LivenessCheck with maxPeriod" - {
    "should fail when trying to register same checkl twice" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()

      val lc1 = LivenessCheck("lc1", 10.seconds)
      assertThrows[IllegalStateException](LivenessCheck("lc1", 10.seconds))
    }

    "checks should update themselves" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()

      val lc1 = LivenessCheck("lc1", 10.seconds)
      val lc2 = LivenessCheck("lc2", 10.seconds)
      val rc1 = ReadinessCheck("rc1")
      val rc2 = ReadinessCheck("rc2")

      registry.isAlive shouldBe true
      registry.isReady shouldBe false
      val notChecked = HealthResult.notChecked
      val notReady = HealthResult.notReady
      registry.readinessDetails shouldBe Map("rc1" -> notReady, "rc2" -> notReady)
      registry.livenessDetails shouldBe Map("lc1" -> notChecked, "lc2" -> notChecked)

      //no clock change
      lc1.update(HealthResult.healthy("OK1"))

      registry.readinessDetails shouldBe Map("rc1" -> notReady, "rc2" -> notReady)
      registry.livenessDetails shouldBe Map("lc1" -> HealthResult.healthy("OK1"), "lc2" -> notChecked)
       clock.adjust(10.seconds)
      lc1.update(HealthResult.healthy("OK2"))
      rc1.update(HealthResult.healthy("OK2"))
      rc2.update(HealthResult.healthy("OK2"))

      registry.readinessDetails shouldBe Map("rc1" -> HealthResult.healthy("OK2"), "rc2" -> HealthResult.healthy("OK2"))
      registry.livenessDetails shouldBe Map("lc1" -> HealthResult.healthy("OK2"), "lc2" -> notChecked)
      registry.isAlive shouldBe true
      registry.isReady shouldBe true
      clock.adjust(1.seconds)
      registry.isAlive shouldBe false
      registry.isReady shouldBe true
      lc2.update(HealthResult.healthy("OK3"))
      registry.isAlive shouldBe true
      registry.isReady shouldBe true

    }

    "should replace all liveness checks that are older than max age" in {
        given registry : HealthRegistry = new SimpleHealthRegistry()
        val check = LivenessCheck("check1", 10.seconds)
        registry.isAlive shouldBe true
        clock.adjust(10.seconds)
        registry.isAlive shouldBe true
        clock.adjust(1.seconds)
        registry.isAlive shouldBe false
    }

    "should be NOT alive when check has error but is older than maxPeriod" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val check = LivenessCheck("check1", 5.seconds)

      // Update with error
      check.update(HealthResult(clock.instant(), HealthStatus.Error("Service failed")))
      registry.isAlive shouldBe false

      // Move time forward beyond maxPeriod
      clock.adjust(6.seconds)
      registry.isAlive shouldBe false
    }



    "should handle multiple checks with different maxPeriods" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val check1 = LivenessCheck("check1", 5.seconds)
      val check2 = LivenessCheck("check2", 10.seconds)

      registry.isAlive shouldBe true //both checks default to warning
      check1.update(HealthStatus.Ok("OK"))
      check2.update(HealthStatus.Ok("OK"))
      registry.isAlive shouldBe true //both checks OK

      clock.adjust(5.seconds) //check 1 just before expiration
      registry.isAlive shouldBe true
      clock.adjust(1.seconds) //check 1 after expiration
      check1.update(HealthStatus.Ok("Ok")) //updating check 1
      registry.isAlive shouldBe true

      clock.adjust(5.seconds) //check 2 afetr expiration
      registry.isAlive shouldBe false
    }

    "should be alive when check has warning within maxPeriod" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val check = LivenessCheck("check1", 10.seconds)

      check.update(HealthStatus.Warning("Degraded"))
      registry.isAlive shouldBe true

      clock.adjust(5.seconds)
      registry.isAlive shouldBe true
    }

    "should be alive when check has ok status within maxPeriod" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val check = LivenessCheck("check1", 10.seconds)

      check.update(HealthStatus.Ok("Healthy"))
      registry.isAlive shouldBe true

      clock.adjust(5.seconds)
      registry.isAlive shouldBe true
    }
  }

  "ReadinessCheck" - {
    "should say it is ready" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val readyCheck = ReadinessCheck("ready1")

      readyCheck.update(HealthStatus.Error("Not ready"))
      registry.isReady shouldBe false

      // Move time forward significantly
      clock.adjust(100.seconds)
      // Readiness checks don't have maxPeriod, so should still fail
      registry.isReady shouldBe false
    }
  }

  "livenessDetails" - {
    "should include all liveness checks regardless of age" in {
      given registry: HealthRegistry = new SimpleHealthRegistry()
      val check1 = LivenessCheck("check1", 5.seconds)
      val check2 = LivenessCheck("check2", 10.seconds)

      check1.update(HealthResult.healthy("OK"))
      check2.update(HealthResult.healthy("OK"))

      registry.livenessDetails.keys should contain allOf("check1", "check2")

      // Even after maxPeriod expires, details should still include the check
      clock.adjust(15.seconds)
      registry.livenessDetails.keys should contain allOf("check1", "check2")
    }
  }
}
