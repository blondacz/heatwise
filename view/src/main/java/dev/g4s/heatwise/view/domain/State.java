package dev.g4s.heatwise.view.domain;

import java.time.Instant;

public record State(
        String deviceId,
        Instant lastChangeTs, Boolean lastOn
) {}

