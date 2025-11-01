package dev.g4s.heatwise.domain

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDate

class PriceRequestTest extends AnyFreeSpec with Matchers {
  "should create request for day" in {
    val request = PriceRequest.forDay("G4S-E-1R-2022-01-01", "E-1R-2022-01-01", LocalDate.of(2022, 1, 1))
    request shouldBe PriceRequest("G4S-E-1R-2022-01-01", "E-1R-2022-01-01", LocalDate.of(2022, 1, 1).atStartOfDay(), LocalDate.of(2022, 1, 2).atStartOfDay())  
  }
}
