package dev.g4s.heatwise.app

import dev.g4s.heatwise.audit.HeatwiseKafkaConfig

import scala.concurrent.duration.*
import pureconfig.*
import pureconfig.ConvertHelpers.*
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.*
import pureconfig.configurable.*

import java.time.LocalTime
import java.time.format.DateTimeFormatter

final case class HeatwiseConfig(
                                 productCode: String,
                                 tariffCode: String,
                                 relayHost: String,
                                 maxPricePerKWh: BigDecimal,
                                 morningPreheat: Option[LocalTime],
                                 desiredTemperature: BigDecimal,
                                 dummyRun: Boolean = true,
                                 checkInterval: FiniteDuration = 1.minute,
                                 kafka: HeatwiseKafkaConfig
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
