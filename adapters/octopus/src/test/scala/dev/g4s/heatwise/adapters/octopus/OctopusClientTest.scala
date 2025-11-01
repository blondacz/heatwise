package dev.g4s.heatwise.adapters.octopus

import dev.g4s.heatwise.domain.{PricePoint, PriceRequest, PriceResponse}
import org.apache.pekko.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, Inside}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.testing.BackendStub
import sttp.model.{StatusCode, Uri}
import sttp.client4.testing.ResponseStub

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}

class OctopusClientTest extends AnyFreeSpec with Matchers with ScalaFutures with BeforeAndAfterAll with Inside {

  implicit val system: ActorSystem = ActorSystem("OctopusClientSpec")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  import OctopusClient.given


  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  "OctopusClient" - {

    "urlFor" - {
      "should generate correct URL for a price request" in {
        val priceRequest = PriceRequest("AGILE-FLEX-22-11-25","E-1R-AGILE-FLEX-22-11-25-A", LocalDateTime.of(2025, 11, 1, 0, 0), LocalDateTime.of(2025, 11, 1, 2, 0)
        )

        val expectedUrl = "https://api.octopus.energy/v1/products/AGILE-FLEX-22-11-25/electricity-tariffs/E-1R-AGILE-FLEX-22-11-25-A/standard-unit-rates/?period_from=2025-11-01T00:00:00&period_to=2025-11-01T02:00:00"

        val result = OctopusClient.urlForStandardUnitRates(priceRequest)

        result.toString shouldEqual expectedUrl
      }
    }

    "fetchPrices" - {
      "should successfully fetch and parse prices" in {
        val jsonResponse = """
          {
            "results": [
              {
                "valid_from": "2025-11-01T00:00:00Z",
                "valid_to": "2025-11-01T00:30:00Z",
                "value_inc_vat": 15.75,
                "value_exc_vat": 15.00
              },
              {
                "valid_from": "2025-11-01T00:30:00Z",
                "valid_to": "2025-11-01T01:00:00Z",
                "value_inc_vat": 16.50,
                "value_exc_vat": 15.71
              }
            ]
          }
        """

        given backend: Backend[Future] =
          BackendStub.asynchronousFuture
            .whenAnyRequest
            .thenRespond(ResponseStub.adjust(jsonResponse))

        val priceRequest = PriceRequest(
          productCode = "AGILE-FLEX-22-11-25",
          tariffCode = "E-1R-AGILE-FLEX-22-11-25-A",
            from = LocalDateTime.of(2025, 11, 1, 0, 0),
            to = LocalDateTime.of(2025, 11, 1, 1, 0)
        )

        val url = OctopusClient.urlForStandardUnitRates(priceRequest)
        val resultFuture = OctopusClient.fetchPrices(url)

        whenReady(resultFuture) { result =>
          inside(result)  { case Right(PriceResponse(firstPrice::secondPrice::Nil)) =>
            firstPrice.validFrom shouldEqual ZonedDateTime.of(2025, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            firstPrice.validTo shouldEqual ZonedDateTime.of(2025, 11, 1, 0, 30, 0, 0, ZoneOffset.UTC)
            firstPrice.pricePerKWh shouldEqual BigDecimal(15.00)
            firstPrice.pricePerKwhIncVat shouldEqual BigDecimal(15.75)

            secondPrice.pricePerKWh shouldEqual BigDecimal(15.71)
            secondPrice.pricePerKwhIncVat shouldEqual BigDecimal(16.50)
          }
        }
      }

      "should handle HTTP error responses" in {
        given backend: Backend[Future] =
          BackendStub.asynchronousFuture
            .whenAnyRequest
            .thenRespondServerError()

        val url = Uri.unsafeParse("https://api.octopus.energy/v1/products/TEST/electricity-tariffs/TEST/standard-unit-rates/")
        val resultFuture = OctopusClient.fetchPrices(url)

        whenReady(resultFuture) { result =>
          result should matchPattern { case Left(_: ResponseException.UnexpectedStatusCode[_]) => }
        }
      }

      "should handle invalid JSON response" in {
        given backend: Backend[Future] =
          BackendStub.asynchronousFuture
            .whenAnyRequest
            .thenRespond(ResponseStub.adjust("invalid json"))

        val url = uri"https://api.octopus.energy/v1/products/TEST/electricity-tariffs/TEST/standard-unit-rates/"
        val resultFuture = OctopusClient.fetchPrices(url)

        whenReady(resultFuture) { result =>
          result should matchPattern { case Left(_: ResponseException.DeserializationException) => }
        }
      }

      "should handle empty results array" in {
        val jsonResponse = """
          {
            "results": []
          }
        """

        given backend: Backend[Future] =
          BackendStub.asynchronousFuture
            .whenAnyRequest
            .thenRespond(ResponseStub.adjust(jsonResponse))

        val url = uri"https://api.octopus.energy/v1/products/TEST/electricity-tariffs/TEST/standard-unit-rates/"
        val resultFuture = OctopusClient.fetchPrices(url)

        whenReady(resultFuture) { result =>
          result should matchPattern { case Right(_) => }
          result.foreach { priceResponse =>
            priceResponse.results shouldBe empty
          }
        }
      }
    }

    "Decoder" - {
      "should correctly decode PricePoint from JSON" in {
        import io.circe.parser._

        val json = """
          {
            "valid_from": "2025-11-01T00:00:00Z",
            "valid_to": "2025-11-01T00:30:00Z",
            "value_inc_vat": 15.75,
            "value_exc_vat": 15.00
          }
        """
        val result = decode[PricePoint](json)

        inside(result) { case Right(PricePoint(validFrom,validTo,pricePerKWh,pricePerKwhIncVat)) =>
          validFrom shouldEqual ZonedDateTime.of(2025, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC)
          validTo shouldEqual ZonedDateTime.of(2025, 11, 1, 0, 30, 0, 0, ZoneOffset.UTC)
          pricePerKwhIncVat shouldEqual BigDecimal(15.75)
          pricePerKWh shouldEqual BigDecimal(15.00)
        }
      }

      "should correctly decode PriceResponse from JSON" in {
        import io.circe.parser._

        val json = """
          {
            "results": [
              {
                "valid_from": "2025-11-01T00:00:00Z",
                "valid_to": "2025-11-01T00:30:00Z",
                "value_inc_vat": 15.75,
                "value_exc_vat": 15.00
              },
              {
                "valid_from": "2025-11-01T00:30:00Z",
                "valid_to": "2025-11-01T01:00:00Z",
                "value_inc_vat": 16.50,
                "value_exc_vat": 15.71
              }
            ]
          }
        """

        val result = decode[PriceResponse](json)

        inside(result) { case Right(PriceResponse(a :: b :: Nil)) =>
          a.pricePerKwhIncVat shouldEqual BigDecimal(15.75)
          b.pricePerKwhIncVat shouldEqual BigDecimal(16.50)
        }
      }
    }
  }
}
