package dev.g4s.heatwise.app

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

class HeatwiseConfigTest extends AnyFreeSpec with Matchers {


  "HeatwiseConfig.loadOrThrow" - {

    "should load config  defaults" in {
        val cfg = HeatwiseConfig.loadOrThrow()

        cfg.productCode shouldBe "AGILE-24-10-01"
        cfg.tariffCode shouldBe "E-1R-AGILE-24-10-01-J"
        cfg.relayHost shouldBe "192.168.1.50"
        cfg.maxPricePerKWh shouldBe BigDecimal(6)
        cfg.morningPreheat shouldBe None
        cfg.dummyRun shouldBe true // default
        cfg.checkInterval shouldBe 1.minute // default
    }
  }
}
