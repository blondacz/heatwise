package dev.g4s.heatwise.adapters.relay

import dev.g4s.heatwise.domain.{HealthResult, LivenessCheck, ReadinessCheck, RelayService}
import org.apache.pekko.actor.ActorSystem
import sttp.client4.Backend

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

class LiveRelayService(livenessCheck: LivenessCheck, readinessCheck: ReadinessCheck)(using clock: Clock) extends RelayService {
  readinessCheck.update(HealthResult.healthy("Ready to serve"))
  def switchRelay(host: String, on: Boolean, dummyRun: Boolean)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[Either[Throwable, Boolean]] =
    ShellySwitch.switch(host, on, dummyRun).map(r => {
      livenessCheck.update(HealthResult.healthy(s"Last relay state: $r"))
      r
    })
}
