package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.{HealthResult, HealthStatus, Liveness, Readiness}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

object HealthRoutes {

 

  def routes(live: Liveness, ready: Readiness): Route =
    concat(
      path("live")(if (live.isAlive) complete("OK") else complete(500, s"NOT OK: ${live.livenessDetails.values.mkString(",")}")),
      path("ready") {
        if (ready.isReady) complete("READY") else complete(503, s"STALE: ${ready.readinessDetails.values.mkString(", ")}")
      }
    )
}



