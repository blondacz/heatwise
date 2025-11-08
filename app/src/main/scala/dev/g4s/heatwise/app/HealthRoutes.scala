package dev.g4s.heatwise.app

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import dev.g4s.heatwise.domain.{HealthResult, HeatwiseConfig, Liveness, Readiness, TemperatureSensorConfig}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.duration.FiniteDuration
import scala.runtime.stdLibPatches.Predef.summon
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.given
import io.circe.Encoder
import io.circe.generic.auto.*
import io.circe.generic.semiauto.deriveEncoder

case class HealthReport(live: Map[String, HealthResult], ready: Map[String, HealthResult], info: Map[String, String], heatwiseConfig: HeatwiseConfig)

object HealthRoutes extends FailFastCirceSupport  {
  given fdEncoder : Encoder[FiniteDuration] = Encoder.encodeString.contramap[FiniteDuration](_.toString) 
  given temperatureSensorConfigEncoder : Encoder[TemperatureSensorConfig] = Encoder.encodeString.contramap[TemperatureSensorConfig](_.path.toString)   
  given heatwiseConfigEncoder : Encoder[HeatwiseConfig] = deriveEncoder   
  given healthResultEncoder : Encoder[HealthResult] = deriveEncoder 
  given healthReportEncoder : Encoder[HealthReport] = deriveEncoder 
  
  def routes(live: Liveness, ready: Readiness, info: Map[String,String], heatwiseConfig: HeatwiseConfig): Route =
    concat(
      path("health")(complete(HealthReport(live.livenessDetails, ready.readinessDetails, info, heatwiseConfig))),
      path("live")(if (live.isAlive) complete("OK") else complete(500, s"NOT OK: ${live.livenessDetails.values.mkString(",")}")),
      path("ready") {
        if (ready.isReady) complete("READY") else complete(503, s"STALE: ${ready.readinessDetails.values.mkString(", ")}")
      }
    )
}



