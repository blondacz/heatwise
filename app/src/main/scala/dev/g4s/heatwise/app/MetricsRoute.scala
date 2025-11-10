package dev.g4s.heatwise.app

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity}
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import org.apache.pekko.http.scaladsl.server.Route

import java.io.StringWriter

class MetricsRoute(using registry: CollectorRegistry) {
  def init(): Unit = DefaultExports.initialize()

  val route: Route =
    path("metrics") {
      get {
        val writer = new StringWriter()
        TextFormat.write004(writer, registry.metricFamilySamples())
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, writer.toString))
      }
    }
}
