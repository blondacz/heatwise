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
  private val defaultDelay = Delay()
  private val policy = Policy(3, None, defaultDelay)


  "should not switch on when" - {
    "price is too high" in {
      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, None, policy)

      decision.reason shouldBe PriceTooHigh(4, 3)
      decision.heatOn shouldBe false
      decision.ts shouldBe now
    }

    "delay is too short - after on" in {
      val price = Price(now, 3)
      
      val decision = Decide.decide(clock, price, Some(now -> true), policy)

      decision.reason shouldBe DelayTooShort(defaultDelay,now)
      decision.heatOn shouldBe false
    }

    "delay is too short - after off" in {
      val price = Price(now, 3)
      val decision = Decide.decide(clock, price, Some(now -> false), policy)

      decision.reason shouldBe DelayTooShort(defaultDelay,now)
      decision.heatOn shouldBe false
    }

    "price too high and not in preheat period" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusMinutes(16).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, None, policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }

    "price too high and at the end of preheat period" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, None, policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }

    "price too high, in the preheat period but delay is too short after heating on" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, Some(now -> true), policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }  
    
    "price too high, in the preheat period but delay is too short after heating off" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, Some(now -> false), policy)
      decision.reason shouldBe NotInPreheatPeriod(preheatBefore)
      decision.heatOn shouldBe false
    }
  }


  "should switch on when" - {
    "price is acceptable and no previous switch" in {
      val price = Price(now, Random.nextInt(4)) //FIXME: Introduce scalacheck
      val decision = Decide.decide(clock, price, None, policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is negative and no previous switch" in {
      val price = Price(now, -1)
      val decision = Decide.decide(clock, price, None, policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is acceptable after switching off with sufficient delay" in {
      val price = Price(now, Random.nextInt(3))
      val decision = Decide.decide(clock, price, Some(now.minus(defaultDelay.minOnMinutes,ChronoUnit.MINUTES) -> false), policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price is acceptable after switching on with sufficient delay" in {
      val price = Price(now, Random.nextInt(4))
      val decision = Decide.decide(clock, price, Some(now.minus(defaultDelay.minOffMinutes,ChronoUnit.MINUTES) -> true), policy)
      decision.reason shouldBe PriceOk(price.pricePerKWh, policy.maxPricePerKWh)
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with no previous on" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMinutes(5).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, None, policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with previous on and sufficient delay" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMinutes(5).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, Some(now.minus(defaultDelay.minOffMinutes,ChronoUnit.MINUTES) -> true), policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

    "price too high but in preheat period with previous off and sufficient delay" in {
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMinutes(5).toLocalTime

      val preheatBefore = PreheatBefore(localNow, Duration.ofMinutes(15))
      val policy = Policy(3, Some(preheatBefore), defaultDelay)

      val price = Price(now, 4)
      val decision = Decide.decide(clock, price, Some(now.minus(defaultDelay.minOnMinutes,ChronoUnit.MINUTES) -> false), policy)
      decision.reason shouldBe InPreheatPeriod(preheatBefore) withClue s"should be in preheat period ${localNow.toString}"
      decision.heatOn shouldBe true
    }

  }
}
