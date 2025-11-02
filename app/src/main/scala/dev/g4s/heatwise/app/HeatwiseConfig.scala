package dev.g4s.heatwise.app

import scala.concurrent.duration.*
import pureconfig.*
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint
import pureconfig.generic.semiauto.*

final case class HeatwiseConfig(
    productCode: String,
    tariffCode: String,
    relayHost: String,
    maxPricePerKWh: BigDecimal,
    morningPreheat: Option[String],
    dummyRun: Boolean = true,
    checkInterval: FiniteDuration = 1.minute
)

object HeatwiseConfig {

  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))


  given reader: ConfigReader[HeatwiseConfig] = deriveReader
  
  def loadOrThrow(): HeatwiseConfig = {
    ConfigSource.default.at("heatwise").load[HeatwiseConfig] match {
      case Right(cfg) => cfg
      case Left(errs: ConfigReaderFailures) =>
        sys.error(s"Invalid Heatwise config: ${errs.toList.map(_.description).mkString("; ")}")
    }
  }
}
