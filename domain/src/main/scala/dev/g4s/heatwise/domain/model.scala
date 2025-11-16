package dev.g4s.heatwise.domain

import java.time.*
import java.time.Duration.*


case class PriceRequest(productCode: String, tariffCode: String, from: LocalDateTime, to: LocalDateTime)
object PriceRequest {
  def forDay(productCode: String, tariffCode: String, day: LocalDate): PriceRequest = {
    val from = day.atStartOfDay()
    val to = day.plusDays(1).atStartOfDay()
    PriceRequest(productCode, tariffCode, from, to)
  }
  
  def fromDateTimForDuration(productCode: String, tariffCode: String, from: LocalDateTime, duration: Duration): PriceRequest = {
    val to = from.plus(duration)
    PriceRequest(productCode, tariffCode, from, to)
  }
}
case class PriceResponse(results: List[PricePoint])
case class PricePoint(validFrom: ZonedDateTime, validTo: ZonedDateTime, pricePerKWh: BigDecimal, pricePerKwhIncVat: BigDecimal)
object PricePoint {
  def MaxPricePerKWh(when: ZonedDateTime) = PricePoint(when, when, BigDecimal(1_000_000), BigDecimal(1_000_000)) 
}

case class Temperature(value: BigDecimal)

final case class Decision(ts: Instant, heatOn: Boolean, reason: DecisionReason)
final case class ControllerState(lastChangeTs: Instant, lastOn: Boolean)

sealed trait DecisionReason

object DecisionReason {
  final case class PriceOk(currentPrice: BigDecimal, maxPricePerKWh: BigDecimal) extends DecisionReason
  final case class PriceTooHigh(currentPrice: BigDecimal, maxPricePerKWh: BigDecimal) extends DecisionReason
  final case class DelayTooShort(delay: Delay, lastChange: Instant) extends DecisionReason
  final case class InPreheatPeriod(preheatBefore: PreheatBefore) extends DecisionReason
  case object NotInPreheatPeriod extends DecisionReason
  final case class TemperatureOk(currentTemperature: Temperature, desiredTemperature: Temperature) extends DecisionReason
}

final case class Delay(minOnMinutes: Int = 2, minOffMinutes: Int = 1)
final case class PreheatBefore(readyBy: LocalTime, duration: Duration)

final case class Policy(maxPricePerKWh: BigDecimal,
                        morningPreheat: Option[PreheatBefore],
                        desiredTemperature: Temperature,
                        delay: Delay)




object Decide {
  import DecisionReason.*


  def decide(clock: Clock, price: PricePoint, currentTemperature: Temperature, lastOnChange: Option[ControllerState], policy: Policy): Decision = {
    if (currentTemperature.value < policy.desiredTemperature.value) {
      val slotOk = price.pricePerKWh <= policy.maxPricePerKWh
      val now = clock.instant()
      val delayOk = lastOnChange match {
        case Some(ControllerState(changedAt, wasOn)) =>
          val mins = between(changedAt, now).toMinutes
          if (wasOn) mins >= policy.delay.minOffMinutes else mins >= policy.delay.minOnMinutes
        case None => true
      }

      val inPreheatPeriod = policy.morningPreheat.fold {
        NotInPreheatPeriod
      } { case p@PreheatBefore(readyBy, duration) =>
        val localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC)
        val localReadyBy = LocalDateTime.ofInstant(now, ZoneOffset.UTC).`with`(readyBy)
        if (localNow.isBefore(localReadyBy) && localNow.isAfter(localReadyBy.minus(duration)) && delayOk)
          InPreheatPeriod(p)
        else
          NotInPreheatPeriod
      }

      if (slotOk && delayOk)
        Decision(now, true, PriceOk(price.pricePerKWh, policy.maxPricePerKWh))
      else if (!slotOk && delayOk && inPreheatPeriod == NotInPreheatPeriod) {
        Decision(now, false, PriceTooHigh(price.pricePerKWh, policy.maxPricePerKWh))
      } else if (!delayOk) {
        Decision(now, lastOnChange.map(_.lastOn).getOrElse(false), DelayTooShort(policy.delay, lastOnChange.get.lastChangeTs))
      }  else if (inPreheatPeriod == NotInPreheatPeriod) {
        Decision(now, false, NotInPreheatPeriod)
      } else {
        Decision(now, true, InPreheatPeriod(policy.morningPreheat.get))
      }

    } else Decision(clock.instant(), false, TemperatureOk(currentTemperature, policy.desiredTemperature))
  }
}