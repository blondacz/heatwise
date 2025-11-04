package dev.g4s.heatwise.domain

import dev.g4s.heatwise.domain.DecisionReason.*
import org.scalatest.AppendedClues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.temporal.ChronoUnit
import java.time.{Clock, Duration, Instant, LocalDateTime, ZoneOffset}
import scala.util.Random

class DecideTest extends AnyFreeSpec with Matchers with AppendedClues {
  private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
  private val now = clock.instant()
  private val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toLocalTime
  private val defaultDelay = Delay()
  private val desiredTemp = Temperature(BigDecimal(100))
  private val policy = Policy(3, None, desiredTemp, defaultDelay )
  private val lowTemp = Temperature(BigDecimal(15))

  "should not switch on when" - {
    "temperature is high enough" in {
      val highTemp = Temperature(BigDecimal(100))
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(2), BigDecimal(2.1))
      val decision = Decide.decide(clock, price, highTemp, None, policy)

      decision.reason shouldBe DecisionReason.TemperatureOk(highTemp, desiredTemp)
      decision.heatOn shouldBe false
      decision.ts shouldBe now
    }

    "temperature is higher than desired" in {
      val highTemp = Temperature(BigDecimal(105))
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(2), BigDecimal(2.1))
      val decision = Decide.decide(clock, price, highTemp, None, policy)

      decision.reason shouldBe DecisionReason.TemperatureOk(highTemp, desiredTemp)
      decision.heatOn shouldBe false
      decision.ts shouldBe now
    }

    "price is too high" in {
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, None, policy)

      decision.reason shouldBe PriceTooHigh(4, 3)
      decision.heatOn shouldBe false
      decision.ts shouldBe now
    }

    "delay is too short - after on" in {
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(3), BigDecimal(3.15))

      val decision = Decide.decide(clock, price,lowTemp, Some(ControllerState(now,true)), policy)

      decision.reason shouldBe DelayTooShort(defaultDelay,now)
      decision.heatOn shouldBe false
    }

    "delay is too short - after off" in {
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(3), BigDecimal(3.15))
      val decision = Decide.decide(clock, price,lowTemp, Some(ControllerState(now,false)), policy)

      decision.reason shouldBe DelayTooShort(defaultDelay,now)
      decision.heatOn shouldBe false
    }

    "price too high and not in preheat period" in {
      val preheatBefore = PreheatBefore(localNow.minusMinutes(16), Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, None, policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }

    "price too high and at the end of preheat period" in {
      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, None, policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }

    "price too high, in the preheat period but delay is too short after heating on" in {
      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, Some(ControllerState(now,true)), policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }

    "price too high, in the preheat period but delay is too short after heating off" in {
      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore),desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, Some(ControllerState(now,false)), policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }
  }


  "should switch on when" - {
    "price is acceptable and no previous switch" in {
      val priceValue = BigDecimal(Random.nextInt(4)) //FIXME: Introduce scalacheck
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), priceValue, priceValue * 1.05)
      val decision = Decide.decide(clock, price, lowTemp, None, policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is negative and no previous switch" in {
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(-1), BigDecimal(-1.05))
      val decision = Decide.decide(clock, price, lowTemp, None, policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is acceptable after switching off with sufficient delay" in {
      val priceValue = BigDecimal(Random.nextInt(3))
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), priceValue, priceValue * 1.05)
      val decision = Decide.decide(clock, price, lowTemp, Some(ControllerState(now.minus(defaultDelay.minOnMinutes,ChronoUnit.MINUTES),false)), policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is acceptable after switching on with sufficient delay" in {
      val priceValue = BigDecimal(Random.nextInt(4))
      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), priceValue, priceValue * 1.05)
      val decision = Decide.decide(clock, price, lowTemp, Some(ControllerState(now.minus(defaultDelay.minOffMinutes,ChronoUnit.MINUTES) , true)), policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with no previous on" in {
      val preheatBefore = PreheatBefore(localNow.plusMinutes(5), Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, None, policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with previous on and sufficient delay" in {
      val preheatBefore = PreheatBefore(localNow.plusMinutes(5), Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price, lowTemp, Some(ControllerState(now.minus(defaultDelay.minOffMinutes,ChronoUnit.MINUTES), true)), policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with previous off and sufficient delay" in {
      val preheatBefore = PreheatBefore(localNow.plusMinutes(5), Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), desiredTemp, defaultDelay)

      val price = PricePoint(now.atZone(ZoneOffset.UTC), now.plusSeconds(1800).atZone(ZoneOffset.UTC), BigDecimal(4), BigDecimal(4.2))
      val decision = Decide.decide(clock, price,lowTemp, Some(ControllerState(now.minus(defaultDelay.minOnMinutes,ChronoUnit.MINUTES), false)), policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

  }
}
