package dev.g4s.heatwise.domain


import scala.concurrent.duration.*
import pureconfig.*
import pureconfig.ConvertHelpers.*
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.*
import pureconfig.configurable.*

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.nio.file.Path

case class Topics(decisions: String, state: String)

//TODO: SOLID - create abstraction
case class HeatwiseKafkaConfig(bootstrap: String,
                               deviceId: String,
                               topics :Topics,
                               acks : String)

case class TemperatureSensorConfig(path: Path, checkInterval: FiniteDuration)


//TODO: split to sub-config abstractions
final case class HeatwiseConfig(
                                 productCode: String,
                                 tariffCode: String,
                                 relayHost: String,
                                 maxPricePerKWh: BigDecimal,
                                 morningPreheat: Option[LocalTime],
                                 desiredTemperature: BigDecimal,
                                 dummyRun: Boolean,
                                 checkInterval: FiniteDuration,
                                 kafka: HeatwiseKafkaConfig,
                                 temperatureSensorConfig: TemperatureSensorConfig
)

object HeatwiseConfig {

  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  given localTimeReader: ConfigReader[LocalTime] = localTimeConfigConvert(DateTimeFormatter.ofPattern("HH:mm"))
  given reader: ConfigReader[HeatwiseConfig] = deriveReader

  def loadOrThrow(): HeatwiseConfig = {
    ConfigSource.default.at("heatwise").load[HeatwiseConfig] match {
      case Right(cfg) => cfg
      case Left(errs: ConfigReaderFailures) =>
        sys.error(s"Invalid Heatwise config: ${errs.toList.map(_.description).mkString("; ")}")
    }
  }
}
