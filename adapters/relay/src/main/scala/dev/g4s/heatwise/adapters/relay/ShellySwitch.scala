package dev.g4s.heatwise.adapters.relay



import io.circe.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.model.Uri

import java.time.*
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import scala.concurrent.{ExecutionContext, Future}
/** Switch response example: { "id": 0, "output": true, // 'true' for on, 'false' for off "apower": 5.2, "voltage":
  * 230.1, "current": 0.05 }
  */
object ShellySwitch {
  given Decoder[SwitchResponse] = Decoder.forProduct1("output")(SwitchResponse.apply)
  def url(deviceHost: String, on: Boolean): Uri = uri"http://$deviceHost/rpc/Switch.Set?id=0&on=$on"

  case class SwitchResponse(output: Boolean)

  def switch(deviceHost: String, on: Boolean)(using backend: Backend[Future], ex: ExecutionContext): Future[Either[Exception, Boolean]] = {
    val request = basicRequest.get(url(deviceHost, on)).response(asJson[SwitchResponse]).mapResponse(_.map(_.output))
    request.send(backend).map(_.body)
  }

}
