/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when a rate limit has been exceeded.
 *
 * <p>Canonical exception for rate-limiting across all platform modules.
 * Consolidates fields from security (retryAfter) and AI gateway
 * (tenantId, requestsPerSecond, currentRate).</p>
 *
 * @doc.type class
 * @doc.purpose Canonical rate limit exceeded exception
 * @doc.layer platform
 * @doc.pattern Exception
 */
public class RateLimitExceededException extends PlatformException {

    private final @Nullable String tenantId;
    private final double requestsPerSecond;
    private final double currentRate;
    private final long retryAfterMillis;

    /**
     * Creates a rate limit exception with full context.
     *
     * @param message            human-readable description
     * @param tenantId           tenant that exceeded the limit (nullable for anonymous)
     * @param requestsPerSecond  configured limit
     * @param currentRate        actual rate at time of violation
     * @param retryAfterMillis   suggested retry delay in milliseconds
     */
    public RateLimitExceededException(
            @NotNull String message,
            @Nullable String tenantId,
            double requestsPerSecond,
            double currentRate,
            long retryAfterMillis) {
        super(ErrorCode.RATE_LIMITED, message);
        this.tenantId = tenantId;
        this.requestsPerSecond = requestsPerSecond;
        this.currentRate = currentRate;
        this.retryAfterMillis = retryAfterMillis;
        withMetadata("tenantId", tenantId);
        withMetadata("requestsPerSecond", requestsPerSecond);
        withMetadata("currentRate", currentRate);
        withMetadata("retryAfterMillis", retryAfterMillis);
    }

    /**
     * Creates a simple rate limit exception.
     *
     * @param message          human-readable description
     * @param retryAfterMillis suggested retry delay in milliseconds
     */
    public RateLimitExceededException(@NotNull String message, long retryAfterMillis) {
        this(message, null, 0, 0, retryAfterMillis);
    }

    /**
     * Creates a rate limit exception with just a message.
     *
     * @param message human-readable description
     */
    public RateLimitExceededException(@NotNull String message) {
        this(message, 0);
    }

    public @Nullable String getTenantId() {
        return tenantId;
    }

    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public double getCurrentRate() {
        return currentRate;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
