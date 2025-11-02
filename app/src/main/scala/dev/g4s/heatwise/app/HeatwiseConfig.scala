package dev.g4s.heatwise.app

import scala.concurrent.duration._

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
  def load(): HeatwiseConfig = HeatwiseConfig(
    sys.env.getOrElse("OCTOPUS_PRODUCT_CODE", "AGILE-24-10-01"),
    sys.env.getOrElse("OCTOPUS_TARIFF_CODE", "E-1R-AGILE-24-10-01-J"),
    sys.env.getOrElse("RELAY_HOST", "192.168.1.50"),
    BigDecimal(sys.env.getOrElse("MAX_PRICE_PER_KWH", "10")),
    sys.env.get("MORNING_PREHEAT"),
    sys.env.get("DUMMY_RUN").forall(_.toBoolean)
  )
}
