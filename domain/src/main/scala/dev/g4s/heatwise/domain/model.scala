package dev.g4s.heatwise.domain

import java.time.*
import java.time.Duration.*

final case class Price(slotStart: Instant, pricePerKWh: BigDecimal)
final case class Decision(ts: Instant, heatOn: Boolean, reason: DecisionReason)

sealed trait DecisionReason

object DecisionReason {
  final case class PriceOk(currentPrice: BigDecimal, maxPricePerKWh: BigDecimal) extends DecisionReason
  final case class PriceTooHigh(currentPrice: BigDecimal, maxPricePerKWh: BigDecimal) extends DecisionReason
  final case class DelayTooShort(delay: Delay, lastChange: Instant) extends DecisionReason
  final case class InPreheatPeriod(preheatBefore: PreheatBefore) extends DecisionReason
  final case class NotInPreheatPeriod(preheatBefore: PreheatBefore) extends DecisionReason
}

final case class Money()
final case class Delay(minOnMinutes: Int = 15, minOffMinutes: Int = 10)
final case class PreheatBefore(readyBy: LocalTime, duration: Duration)

final case class Policy(maxPricePerKWh: BigDecimal,
                        morningPreheat: Option[PreheatBefore],
                        delay: Delay)




object Decide {
  import DecisionReason.*


  def decide(clock: Clock, price: Price, lastOnChange: Option[(Instant, Boolean)], policy: Policy): Decision = {
    val slotOk = price.pricePerKWh <= policy.maxPricePerKWh
    val now = clock.instant()
    val delayOk = lastOnChange match {
      case Some((changedAt, wasOn)) =>
        val mins = between(changedAt, now).toMinutes
        if (wasOn) mins >= policy.delay.minOffMinutes else mins >= policy.delay.minOnMinutes
      case None => true
    }

    policy.morningPreheat.fold {
      if (slotOk && delayOk)
        Decision(now, true, PriceOk(price.pricePerKWh, policy.maxPricePerKWh))
      else if (!slotOk)
        Decision(now, false, PriceTooHigh(price.pricePerKWh, policy.maxPricePerKWh))
      else
        Decision(now, false, DelayTooShort(policy.delay, lastOnChange.get._1))
    }{case p @ PreheatBefore(readyBy,duration) =>
      val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
      val localReadyBy = LocalDateTime.ofInstant(now, ZoneOffset.UTC).`with`(readyBy)
      if (localNow.isBefore(localReadyBy) && localNow.isAfter(localReadyBy.minus(duration)) && delayOk)
        Decision(now, true, InPreheatPeriod(p))
      else
        Decision(now, false, NotInPreheatPeriod(p))
    }
  }
}