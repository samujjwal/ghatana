package com.ghatana.agent.memory.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Validity tracks confidence, temporal decay, and lifecycle status
 * for a memory item. Confidence decays over time unless the item
 * is re-verified.
 *
 * <p><b>Decay formula:</b> {@code currentConfidence = initial * e^(-decayRate * ageInDays)}
 *
 * @doc.type value-object
 * @doc.purpose Memory item validity and confidence tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Validity {

    /** Confidence score in [0.0, 1.0]. */
    @Builder.Default
    double confidence = 0.0;

    /** When this item was last verified as still accurate. */
    @Nullable
    Instant lastVerified;

    /** Per-day confidence decay rate. 0.0 means no decay. */
    @Builder.Default
    double decayRate = 0.0;

    /** Current lifecycle status. */
    @NotNull
    @Builder.Default
    ValidityStatus status = ValidityStatus.ACTIVE;

    /**
     * Computes the effective confidence given the current time,
     * applying exponential decay since last verification.
     *
     * @param now Current time
     * @return Decayed confidence in [0.0, 1.0]
     */
    public double effectiveConfidence(@NotNull Instant now) {
        if (decayRate <= 0.0 || lastVerified == null) {
            return confidence;
        }
        long ageMs = now.toEpochMilli() - lastVerified.toEpochMilli();
        double ageDays = ageMs / (1000.0 * 60 * 60 * 24);
        return confidence * Math.exp(-decayRate * ageDays);
    }
}
