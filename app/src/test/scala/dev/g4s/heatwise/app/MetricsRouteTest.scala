package dev.g4s.heatwise.app

import io.prometheus.client.CollectorRegistry
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MetricsRouteTest extends AnyFreeSpec with Matchers with ScalatestRouteTest {



  "MetricsRoute" - {
    "/metrics" - {
      "should return basic metrics" in {
        val route = new MetricsRoute(using new CollectorRegistry())
        route.init()

        Thread.sleep(10_000)
        Get("/metrics") ~> route.route ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldBe ""
        }
      }
    }
  }
}
