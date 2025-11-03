package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.{HealthResult, HealthStatus}

given Conversion[HealthResult, String] with
  def apply(r: HealthResult): String = r.status match
    case HealthStatus.Ok(msg) => s"OK($msg) @ ${r.timestamp}"
    case HealthStatus.Warning(msg) => s"WARN($msg) @ ${r.timestamp}"
    case HealthStatus.Error(msg) => s"ERR($msg) @ ${r.timestamp}"