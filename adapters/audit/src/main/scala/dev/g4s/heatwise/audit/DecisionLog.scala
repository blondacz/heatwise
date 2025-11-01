package dev.g4s.heatwise.audit


import dev.g4s.heatwise.domain.Decision

import java.nio.file.*
import java.time.*
import java.time.format.DateTimeFormatter

object DecisionLog {
  private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def append(decision: Decision, base: Path = Paths.get("./decisions")): Unit = {
    val day = LocalDateTime.ofInstant(decision.ts, ZoneOffset.UTC).format(fmt)
    val file = base.resolve(s"$day.log")
    Files.createDirectories(base)
    val line = s"${decision.ts},${decision.heatOn},${decision.reason.toString.replace(',', ';')}\n"
    Files.writeString(file, line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
  }
}
