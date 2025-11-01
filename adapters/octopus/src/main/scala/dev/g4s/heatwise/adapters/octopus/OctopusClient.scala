package dev.g4s.heatwise.adapters.octopus

import dev.g4s.heatwise.domain.*
import io.circe.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.model.Uri

import java.time.*
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import scala.concurrent.{ExecutionContext, Future}

object OctopusClient {

  given Decoder[PricePoint] = Decoder.forProduct4("valid_from", "valid_to", "value_exc_vat", "value_inc_vat")(PricePoint.apply)
  given Decoder[PriceResponse] = Decoder.derived[PriceResponse]


  def urlForStandardUnitRates(priceRequest: PriceRequest): Uri = {
    val periodFrom = priceRequest.from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    val periodTo = priceRequest.to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    uri"https://api.octopus.energy/v1/products/${priceRequest.productCode}/electricity-tariffs/${priceRequest.tariffCode}/standard-unit-rates/?period_from=$periodFrom&period_to=$periodTo"
  }

  def fetchPrices(tariffUrl: Uri)(using backend: Backend[Future], ex: ExecutionContext): Future[Either[Exception, PriceResponse]] = {
    val request = basicRequest.get(tariffUrl).response(asJson[PriceResponse])
    request.send(backend).map(_.body)
  }
}
