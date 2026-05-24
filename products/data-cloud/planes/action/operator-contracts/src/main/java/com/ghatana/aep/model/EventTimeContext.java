package com.ghatana.aep.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Carries event-time, watermark, lateness, and replay time policy into EventOperators
 * @doc.layer product
 * @doc.pattern Contract
 */
public record EventTimeContext(
        TimeMode mode,
        Optional<Instant> watermark,
        Duration allowedLateness,
        LateEventBehavior lateEventBehavior,
        Optional<Instant> partialMatchExpiresAt) {

    public EventTimeContext {
        mode = Objects.requireNonNull(mode, "mode must not be null");
        watermark = watermark != null ? watermark : Optional.empty();
        allowedLateness = Objects.requireNonNull(allowedLateness, "allowedLateness must not be null");
        lateEventBehavior = Objects.requireNonNull(lateEventBehavior, "lateEventBehavior must not be null");
        partialMatchExpiresAt = partialMatchExpiresAt != null ? partialMatchExpiresAt : Optional.empty();
        if (allowedLateness.isNegative()) {
            throw new IllegalArgumentException("allowedLateness must not be negative");
        }
    }

    public enum TimeMode {
        EVENT_TIME,
        PROCESSING_TIME,
        DETECTION_TIME
    }

    public enum LateEventBehavior {
        INCORPORATE,
        COMPENSATE,
        DEGRADE_CONFIDENCE,
        EMIT_CORRECTION,
        REJECT_TO_DLQ
    }
}
