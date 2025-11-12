package dev.g4s.heatwise.view.domain;

import java.time.Instant;

public record Decision(
        String deviceId,
        Boolean heatOn,
        String reason,
        Instant ts
) {}
