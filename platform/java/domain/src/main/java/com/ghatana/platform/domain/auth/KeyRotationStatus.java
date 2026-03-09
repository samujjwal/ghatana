/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Tracks the status of JWT key rotation.
 *
 * <p>Encapsulates the lifecycle state of cryptographic key pairs used for
 * JWT signing, including the current key, previous key (for validation-window
 * overlap), rotation schedule, and derived state flags.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * KeyRotationStatus status = KeyRotationStatus.builder()
 *     .currentKeyId("key-2026-01")
 *     .previousKeyId("key-2025-12")
 *     .lastRotationTime(lastRotation)
 *     .rotationInterval(Duration.ofDays(90))
 *     .validationWindow(Duration.ofDays(7))
 *     .build();
 *
 * if (status.isRotationOverdue()) {
 *     keyManager.rotateKeys();
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JWT key rotation lifecycle tracking
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class KeyRotationStatus {

    private final String currentKeyId;
    private final String previousKeyId;
    private final Instant lastRotationTime;
    private final Instant nextRotationTime;
    private final Instant validationWindowEnd;
    private final boolean withinValidationWindow;
    private final long daysUntilRotation;
    private final long daysInValidationWindow;
    private final Duration rotationInterval;
    private final Duration validationWindow;

    private KeyRotationStatus(Builder builder) {
        this.currentKeyId = Objects.requireNonNull(builder.currentKeyId, "currentKeyId is required");
        this.previousKeyId = builder.previousKeyId;
        this.lastRotationTime = Objects.requireNonNull(builder.lastRotationTime, "lastRotationTime is required");
        this.rotationInterval = Objects.requireNonNull(builder.rotationInterval, "rotationInterval is required");
        this.validationWindow = Objects.requireNonNull(builder.validationWindow, "validationWindow is required");

        // Derived fields
        this.nextRotationTime = lastRotationTime.plus(rotationInterval);
        this.validationWindowEnd = nextRotationTime.plus(validationWindow);

        Instant now = Instant.now();
        this.withinValidationWindow = now.isAfter(nextRotationTime) && now.isBefore(validationWindowEnd);
        this.daysUntilRotation = Duration.between(now, nextRotationTime).toDays();
        this.daysInValidationWindow = validationWindow.toDays();
    }

    public String getCurrentKeyId() { return currentKeyId; }
    public String getPreviousKeyId() { return previousKeyId; }
    public Instant getLastRotationTime() { return lastRotationTime; }
    public Instant getNextRotationTime() { return nextRotationTime; }
    public Instant getValidationWindowEnd() { return validationWindowEnd; }
    public boolean isWithinValidationWindow() { return withinValidationWindow; }
    public long getDaysUntilRotation() { return daysUntilRotation; }
    public long getDaysInValidationWindow() { return daysInValidationWindow; }
    public Duration getRotationInterval() { return rotationInterval; }
    public Duration getValidationWindow() { return validationWindow; }

    /** @return true if rotation is due within 7 days */
    public boolean isRotationNeededSoon() { return daysUntilRotation <= 7; }

    /** @return true if rotation is past due (negative days remaining) */
    public boolean isRotationOverdue() { return daysUntilRotation < 0; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String currentKeyId;
        private String previousKeyId;
        private Instant lastRotationTime;
        private Duration rotationInterval;
        private Duration validationWindow;

        public Builder currentKeyId(String currentKeyId) { this.currentKeyId = currentKeyId; return this; }
        public Builder previousKeyId(String previousKeyId) { this.previousKeyId = previousKeyId; return this; }
        public Builder lastRotationTime(Instant lastRotationTime) { this.lastRotationTime = lastRotationTime; return this; }
        public Builder rotationInterval(Duration rotationInterval) { this.rotationInterval = rotationInterval; return this; }
        public Builder validationWindow(Duration validationWindow) { this.validationWindow = validationWindow; return this; }

        public KeyRotationStatus build() { return new KeyRotationStatus(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyRotationStatus that)) return false;
        return Objects.equals(currentKeyId, that.currentKeyId)
                && Objects.equals(previousKeyId, that.previousKeyId)
                && Objects.equals(lastRotationTime, that.lastRotationTime)
                && Objects.equals(rotationInterval, that.rotationInterval)
                && Objects.equals(validationWindow, that.validationWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentKeyId, previousKeyId, lastRotationTime, rotationInterval, validationWindow);
    }

    @Override
    public String toString() {
        return "KeyRotationStatus{currentKeyId='" + currentKeyId + "', previousKeyId='" + previousKeyId
                + "', lastRotation=" + lastRotationTime + ", nextRotation=" + nextRotationTime
                + ", daysUntilRotation=" + daysUntilRotation + ", overdue=" + isRotationOverdue() + '}';
    }
}
