package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.testkit.TestKit

import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Instant, ZoneOffset}

class HealthRoutesTest extends AnyFreeSpec with Matchers with ScalatestRouteTest {

  val fixedTime: Instant = Instant.parse("2024-01-01T10:00:00Z")
  given clock: Clock = Clock.fixed(fixedTime, ZoneOffset.UTC)
  given registry: HealthRegistry = SimpleHealthRegistry()

  "HealthRoutes" - {
    "/live" - {
      "should return OK when all liveness checks pass" in {
        val livenessCheck = LivenessCheck("test-liveness")
        livenessCheck.update(HealthResult.healthy("All good"))

        val routes = HealthRoutes.routes(registry, registry)

        Get("/live") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe "OK"
        }
      }

      "should return 500 when liveness checks fail" in {
        val registry = SimpleHealthRegistry()
        val livenessCheck = LivenessCheck("test-liveness")(using clock, registry)
        livenessCheck.update(HealthResult(clock.instant(), HealthStatus.Error("Service down")))

        val routes = HealthRoutes.routes(registry, registry)

        Get("/live") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          responseAs[String] should include("NOT OK")
          responseAs[String] should include("Service down")
        }
      }
    }

    "/ready" - {
      "should return READY when all readiness checks pass" in {
        val registry = SimpleHealthRegistry()
        val readinessCheck = ReadinessCheck("test-readiness")(using clock, registry)
        readinessCheck.update(HealthResult.healthy("Ready to serve"))

        val routes = HealthRoutes.routes(registry, registry)

        Get("/ready") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe "READY"
        }
      }

      "should return 503 when readiness checks fail" in {
        val registry = SimpleHealthRegistry()
        val readinessCheck = ReadinessCheck("test-readiness")(using clock, registry)
        readinessCheck.update(HealthResult(clock.instant(), HealthStatus.Error("Database not ready")))

        val routes = HealthRoutes.routes(registry, registry)

        Get("/ready") ~> routes ~> check {
          status shouldBe StatusCodes.ServiceUnavailable
          responseAs[String] should include("STALE")
          responseAs[String] should include("Database not ready")
        }
      }
    }

    "HealthResult conversion" - {
      "should convert Ok status to string" in {
        val result = HealthResult(fixedTime, HealthStatus.Ok("healthy"))
        val converted: String = result
        converted shouldBe s"OK(healthy) @ $fixedTime"
      }

      "should convert Warning status to string" in {
        val result = HealthResult(fixedTime, HealthStatus.Warning("degraded"))
        val converted: String = result
        converted shouldBe s"WARN(degraded) @ $fixedTime"
      }

      "should convert Error status to string" in {
        val result = HealthResult(fixedTime, HealthStatus.Error("failed"))
        val converted: String = result
        converted shouldBe s"ERR(failed) @ $fixedTime"
      }
    }
  }
}
