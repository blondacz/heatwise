package dev.g4s.heatwise.adapters.cylinder

import dev.g4s.heatwise.domain.{Temperature, TemperatureSensorConfig}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Inside}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TemperatureSensorTest extends AnyFreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Inside {

  implicit val system: ActorSystem = ActorSystem("OctopusClientSpec")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))
    
  private val checkInterval = 100.millis  

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  "should fail if temperature file is not found" in {
    val sensor = new TemperatureSensor(TemperatureSensorConfig(Paths.get("not-exists"), checkInterval))
    val f = sensor.temperature.runWith(Sink.head)

    whenReady(f.failed) { ex =>
      ex shouldBe a[IllegalStateException]
      ex.getMessage should fullyMatch regex "File not found: (.*)not-exists"
    }
  }

  "should fail if temperature file is not syncing" in {
    val sensor = new TemperatureSensor(TemperatureSensorConfig(ResourceUtils.getPathFromResource("not_syncing"), checkInterval))
    val f = sensor.temperature.runWith(Sink.head)

    whenReady(f.failed) { ex =>
      ex shouldBe a[IllegalStateException]
      ex.getMessage shouldBe s"Temperature not syncing. Line 1 is: [72 01 ff ff ff ff ff ff ff : crc=72 NO]"
    }
  }

  "should fail if temperature file is not correct format" in {
    val sensor = new TemperatureSensor(TemperatureSensorConfig(ResourceUtils.getPathFromResource("not_temperature"), checkInterval))
    val f = sensor.temperature.runWith(Sink.head)

    whenReady(f.failed) { ex =>
      ex shouldBe a[IllegalStateException]
      ex.getMessage shouldBe s"Temperature has wrong format. Line 2 is: [72 01 ff ff ff ff ff ff ff t=bla]"
    }
  }

  "should fail if temperature file does not hav 2 lines" in {
    val sensor = new TemperatureSensor(TemperatureSensorConfig(ResourceUtils.getPathFromResource("temperature_empty"), checkInterval))
    val f = sensor.temperature.runWith(Sink.head)

    whenReady(f.failed) { ex =>
      ex shouldBe a[IllegalStateException]
      ex.getMessage shouldBe s"Temperature has wrong file format. Lines are: "
    }
  }

  "should read temperature OK" in {
    val sensor = new TemperatureSensor(TemperatureSensorConfig(ResourceUtils.getPathFromResource("temperature_ok"), checkInterval))
    val f = sensor.temperature.runWith(Sink.head)

    whenReady(f)(_ shouldBe Temperature(23.125))
  }
}
