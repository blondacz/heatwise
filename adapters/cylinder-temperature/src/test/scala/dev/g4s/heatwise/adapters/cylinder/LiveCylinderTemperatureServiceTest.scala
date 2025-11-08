package dev.g4s.heatwise.adapters.cylinder

import dev.g4s.heatwise.domain.{HealthRegistry, HeatwiseConfig, LivenessCheck, ReadinessCheck, SimpleHealthRegistry, Temperature, TemperatureSensorConfig}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Futures, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.shouldEqual
import sttp.client4.Backend
import sttp.client4.pekkohttp.PekkoHttpBackend

import java.nio.file.Paths
import java.time.Clock
import scala.concurrent.Future
import scala.concurrent.duration.*

class LiveCylinderTemperatureServiceTest extends AnyFreeSpec with ScalaFutures with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
  
  given system: ActorSystem = ActorSystem("heatwise")

  given clock: Clock = Clock.systemUTC()
  import system.dispatcher

  given healthRegistry: HealthRegistry = new SimpleHealthRegistry()

  given backend: Backend[Future] = PekkoHttpBackend.usingActorSystem(system)

  "should run the service and not blow up" in {
    val service = new LiveCylinderTemperatureService(TemperatureSensorConfig(Paths.get("pathssss"), 1.second), LivenessCheck("cylinder-temperature", 20.minutes), ReadinessCheck("cylinder-temperature"))
    val x = service.fetchCurrentTemperature().runWith(Sink.head)
    whenReady(x.failed) {  ex =>
       ex shouldBe a[IllegalStateException]
    }
  }
}
