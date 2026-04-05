/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable container for account lockout information.
 *
 * @param failedAttempts number of consecutive failed authentication attempts
 * @param lockedUntil    when the lockout expires (agent can retry after this)
 * @param reason         human-readable reason for lockout (e.g., "rate limit exceeded")
 *
 * @doc.type record
 * @doc.purpose Immutable account lockout information
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record LockoutInfo(
        int failedAttempts,
        Instant lockedUntil,
        String reason
) {
    /** True if the lockout is currently active. */
    public boolean isActive() {
        return Instant.now().isBefore(lockedUntil);
    }

    /** Time remaining until lockout expires. */
    public Duration remainingTime() {
        Instant now = Instant.now();
        if (!isActive()) {
            return Duration.ZERO;
        }
        return Duration.between(now, lockedUntil);
    }
}
