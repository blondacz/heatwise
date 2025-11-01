package dev.g4s.heatwise.audit

import dev.g4s.heatwise.domain.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.*

class DecisionLogTest extends AnyFreeSpec with Matchers {

  private def tempBaseDir(): Path = {
    val parent = Files.createTempDirectory("decision-log-test-")
    // create a nested non-existing base to verify createDirectories
    parent.resolve("nested/base")
  }

  "DecisionLog.append" - {

    "should create directories and write a line with escaped commas" in {
      val base = tempBaseDir()
      val ts = Instant.parse("2025-11-01T10:15:30Z")
      val decision = Decision(ts, heatOn = true, DecisionReason.PriceOk(BigDecimal(10.25), BigDecimal(15)))

      DecisionLog.append(decision, base)

      val expectedDay = LocalDateTime.ofInstant(ts, ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      val file = base.resolve(s"$expectedDay.log")
      Files.exists(file) shouldBe true

      val content = Files.readString(file, StandardCharsets.UTF_8)
      content should include (s"${decision.ts},${decision.heatOn},")
      content should include ("PriceOk(10.25;15)")
      content.endsWith("\n") shouldBe true
    }

    "should name the file by UTC day of decision timestamp" in {
      val base = tempBaseDir()
      val ts = Instant.parse("2025-01-02T23:59:59Z")
      val decision = Decision(ts, heatOn = false, DecisionReason.PriceTooHigh(BigDecimal(30), BigDecimal(20)))

      DecisionLog.append(decision, base)

      val expectedName = "2025-01-02.log"
      val file = base.resolve(expectedName)
      Files.exists(file) shouldBe true
    }

    "should append multiple lines for same day" in {
      val base = tempBaseDir()
      val ts1 = Instant.parse("2025-03-15T00:00:01Z")
      val ts2 = Instant.parse("2025-03-15T12:34:56Z")
      val d1 = Decision(ts1, heatOn = true, DecisionReason.NotInPreheatPeriod(PreheatBefore(LocalTime.NOON, Duration.ofMinutes(30))))
      val d2 = Decision(ts2, heatOn = false, DecisionReason.DelayTooShort(Delay(5, 5), ts1))

      DecisionLog.append(d1, base)
      DecisionLog.append(d2, base)

      val day = LocalDateTime.ofInstant(ts1, ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      val file = base.resolve(s"$day.log")
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
      lines.size() shouldBe 2
    }
  }
}
