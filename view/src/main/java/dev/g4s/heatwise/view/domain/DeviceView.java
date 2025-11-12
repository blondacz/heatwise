package dev.g4s.heatwise.view.domain;

import java.time.Instant;
import java.util.Optional;

public record DeviceView(
        String deviceId,
        Optional<Boolean> lastDecisionOn,
        Optional<String> lastDecisionReason,
        Optional<Instant> lastDecisionAt,
        Boolean lastStateOn,
        Instant lastStateChange
) {}
