package dev.g4s.heatwise.adapters.relay

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.client4.*
import sttp.client4.testing.BackendStub
import sttp.client4.testing.ResponseStub

import scala.concurrent.{ExecutionContext, Future}

class ShellySwitchTest extends AnyFreeSpec with Matchers with ScalaFutures {

  given ec: ExecutionContext = ExecutionContext.global
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "ShellySwitch" - {

    "url should generate correct endpoint - on" in {
      val url = ShellySwitch.url("192.168.1.5", on = true)
      url.toString shouldEqual "http://192.168.1.5/rpc/Switch.Set?id=0&on=true"
    }

    "url should generate correct endpoint - off" in {
      val url = ShellySwitch.url("192.168.1.5", on = false)
      url.toString shouldEqual "http://192.168.1.5/rpc/Switch.Set?id=0&on=false"
    }

    "switch should parse successful response" in {
      val expectedUrl = "http://192.168.1.5/rpc/Switch.Set?id=0&on=true"

      given backend: Backend[Future] =
        BackendStub.asynchronousFuture
          .whenRequestMatches(_.uri.toString == expectedUrl)
          .thenRespond(ResponseStub.adjust("""{"output": true}"""))
          .whenAnyRequest
          .thenRespondNotFound()

      val resultFut = ShellySwitch.switch("192.168.1.5", on = true)

      whenReady(resultFut) { result =>
        result shouldBe Right(ShellySwitch.SwitchResponse(output = true))
      }
    }

    "switch should handle HTTP error responses" in {
      given backend: Backend[Future] =
        BackendStub.asynchronousFuture
          .whenAnyRequest
          .thenRespondServerError()

      val resultFut = ShellySwitch.switch("192.168.1.5", on = false)

      whenReady(resultFut) { result =>
        result.isLeft shouldBe true
      }
    }

    "switch should handle invalid JSON" in {
      given backend: Backend[Future] =
        BackendStub.asynchronousFuture
          .whenAnyRequest
          .thenRespond(ResponseStub.adjust("not json"))

      val resultFut = ShellySwitch.switch("192.168.1.5", on = true)

      whenReady(resultFut) { result =>
        result.isLeft shouldBe true
      }
    }
  }
}
