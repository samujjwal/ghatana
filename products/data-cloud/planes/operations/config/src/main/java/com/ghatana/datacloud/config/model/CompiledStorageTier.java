package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Compiled storage tier configuration for multi-tier event storage.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled storage tier for multi-tier event storage
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledStorageTier(
        String profileName,
        Duration duration,
        boolean appendOnly,
        boolean isForever
        ) {

    /**
     * Creates a CompiledStorageTier with validation.
     */
    public CompiledStorageTier    {
        Objects.requireNonNull(profileName, "Storage tier profile name cannot be null");

        // Duration can be null if isForever is true
        if (duration == null && !isForever) {
            throw new IllegalArgumentException("Storage tier must have duration or be FOREVER");
        }
    }

    /**
     * Create a storage tier with a specific duration.
     *
     * @param profileName storage profile name
     * @param duration retention duration
     * @param appendOnly whether append-only
     * @return compiled storage tier
     */
    public static CompiledStorageTier withDuration(String profileName, Duration duration, boolean appendOnly) {
        return new CompiledStorageTier(profileName, duration, appendOnly, false);
    }

    /**
     * Create a forever storage tier (archive).
     *
     * @param profileName storage profile name
     * @param appendOnly whether append-only
     * @return compiled storage tier
     */
    public static CompiledStorageTier forever(String profileName, boolean appendOnly) {
        return new CompiledStorageTier(profileName, null, appendOnly, true);
    }

    /**
     * Check if this is a hot tier (short duration).
     *
     * @return true if hot tier (< 1 day)
     */
    public boolean isHotTier() {
        return duration != null && duration.compareTo(Duration.ofDays(1)) < 0;
    }

    /**
     * Check if this is a warm tier (medium duration).
     *
     * @return true if warm tier (1 day - 90 days)
     */
    public boolean isWarmTier() {
        if (duration == null) {
            return false;
        }
        return duration.compareTo(Duration.ofDays(1)) >= 0
                && duration.compareTo(Duration.ofDays(90)) < 0;
    }

    /**
     * Check if this is a cold tier (long duration or forever).
     *
     * @return true if cold tier (>= 90 days or forever)
     */
    public boolean isColdTier() {
        return isForever || (duration != null && duration.compareTo(Duration.ofDays(90)) >= 0);
    }
}
