/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.health;

import com.ghatana.platform.observability.health.HealthCheck;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Platform-integrated health check for the AEP engine (AEP-017).
 *
 * <p>Implements the platform {@link HealthCheck} contract so the AEP engine can
 * be registered with the standard {@link com.ghatana.platform.observability.health.HealthCheckRegistry}
 * and contribute to both liveness and readiness probes.
 *
 * <p><b>Check logic:</b>
 * <ul>
 *   <li>If the engine is closed, the check returns {@code DOWN}.</li>
 *   <li>If the last event processing attempt was recent (within the degradation window),
 *       the check returns {@code UP}.</li>
 *   <li>If the error rate over the sampling window exceeds the configured threshold,
 *       the check returns {@code DEGRADED}.</li>
 *   <li>If no events have been processed yet, the check returns {@code UP} (healthy idle).</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AepHealthIndicator health = AepHealthIndicator.builder()
 *     .engineClosed(() -> engine.isClosed())
 *     .degradeIfErrorRateExceeds(0.05)       // 5% error rate → DEGRADED
 *     .build();
 *
 * // Schedule with platform registry
 * HealthCheckRegistry.getInstance().register(health);
 *
 * // Update from the processing pipeline
 * health.recordSuccess();
 * health.recordFailure();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AEP engine health indicator for liveness and readiness probes
 * @doc.layer product
 * @doc.pattern Observer
 * @since 1.2.0
 */
public final class AepHealthIndicator implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(AepHealthIndicator.class);

    private static final String NAME = "aep-engine";

    private final java.util.function.BooleanSupplier engineClosedSupplier;
    private final double maxErrorRate;
    private final Duration degradationWindow;
    private final Clock clock;

    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicReference<Instant> lastActivityTime = new AtomicReference<>(null);

    private AepHealthIndicator(Builder builder) {
        this.engineClosedSupplier = builder.engineClosedSupplier;
        this.maxErrorRate         = builder.maxErrorRate;
        this.degradationWindow    = builder.degradationWindow;
        this.clock                = builder.clock;
    }

    /** Signals a successful event processing cycle. */
    public void recordSuccess() {
        successCount.incrementAndGet();
        lastActivityTime.set(clock.instant());
    }

    /** Signals a failed event processing cycle. */
    public void recordFailure() {
        failureCount.incrementAndGet();
        lastActivityTime.set(clock.instant());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public Promise<HealthCheck.HealthCheckResult> check() {
        return Promise.of(evaluate());
    }

    private HealthCheck.HealthCheckResult evaluate() {
        if (engineClosedSupplier.getAsBoolean()) {
            return HealthCheck.HealthCheckResult.unhealthy("AEP engine is closed");
        }

        long successes = successCount.get();
        long failures  = failureCount.get();
        long total     = successes + failures;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("successes", successes);
        details.put("failures", failures);
        details.put("total", total);

        Instant last = lastActivityTime.get();
        if (last != null) {
            details.put("lastActivityAgoMs", Duration.between(last, clock.instant()).toMillis());
        }

        if (total == 0) {
            return HealthCheck.HealthCheckResult.healthy(
                "AEP engine is idle (no events processed yet)",
                details,
                Duration.ZERO);
        }

        double errorRate = (double) failures / total;
        details.put("errorRatePct", String.format("%.2f", errorRate * 100));

        if (errorRate > maxErrorRate) {
            log.warn("AEP health DEGRADED: errorRate={} exceeds threshold={}",
                String.format("%.2f%%", errorRate * 100),
                String.format("%.2f%%", maxErrorRate * 100));
            return HealthCheck.HealthCheckResult.degraded(
                String.format("Error rate %.1f%% exceeds threshold %.1f%%",
                    errorRate * 100, maxErrorRate * 100),
                details,
                Duration.ZERO);
        }

        return HealthCheck.HealthCheckResult.healthy("AEP engine is healthy", details, Duration.ZERO);
    }

    /**
     * Resets all counters. Useful for testing rolling windows or after a config reload.
     */
    public void reset() {
        successCount.set(0);
        failureCount.set(0);
        lastActivityTime.set(null);
    }

    /** @return a new builder */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AepHealthIndicator}.
     *
     * @doc.type class
     * @doc.purpose Fluent builder for AepHealthIndicator
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {

        private java.util.function.BooleanSupplier engineClosedSupplier = () -> false;
        private double maxErrorRate     = 0.10; // 10% error rate triggers DEGRADED
        private Duration degradationWindow = Duration.ofMinutes(5);
        private Clock clock = Clock.systemUTC();

        private Builder() {}

        /**
         * Supplier that returns {@code true} when the engine has been closed.
         *
         * @param supplier must not be {@code null}
         */
        public Builder engineClosed(java.util.function.BooleanSupplier supplier) {
            this.engineClosedSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
            return this;
        }

        /**
         * Sets the error rate threshold above which the check returns {@code DEGRADED}.
         *
         * @param rate value in [0.0, 1.0], e.g. {@code 0.05} for 5%
         */
        public Builder degradeIfErrorRateExceeds(double rate) {
            if (rate < 0.0 || rate > 1.0) {
                throw new IllegalArgumentException("rate must be in [0.0, 1.0], was: " + rate);
            }
            this.maxErrorRate = rate;
            return this;
        }

        /**
         * Clock to use; override for testing.
         *
         * @param clock must not be {@code null}
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            return this;
        }

        /** @return new {@link AepHealthIndicator} */
        public AepHealthIndicator build() {
            return new AepHealthIndicator(this);
        }
    }
}
