/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks Service Level Objective (SLO) compliance over a rolling time window (STORY-K06-013).
 *
 * <p>For each registered SLO, {@link SloTracker} maintains success and failure event counts
 * within a configurable rolling window and exposes compliance metrics so Prometheus /
 * Grafana dashboards can alert on SLO burns.
 *
 * <p>Complements {@link FinanceSloRegistry} which wires domain-specific SLOs. This class
 * provides the generic infrastructure that the registry delegates to.
 *
 * @doc.type  class
 * @doc.purpose Generic rolling-window SLO compliance tracker with Micrometer gauges (K06-013)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class SloTracker {

    private static final Logger log = LoggerFactory.getLogger(SloTracker.class);

    /** Rolling window size — events older than this are excluded from compliance calculation. */
    private final long windowMs;

    private final MeterRegistry registry;
    private final Map<String, SloWindow> sloWindows = new ConcurrentHashMap<>();

    /**
     * @param registry  Micrometer registry for gauges
     * @param window    rolling window for SLO evaluation (e.g. 5 minutes)
     */
    public SloTracker(MeterRegistry registry, Duration window) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.windowMs = Objects.requireNonNull(window, "window").toMillis();
    }

    /**
     * Records a successful SLO-relevant event for the given SLO name.
     *
     * @param sloName  identifier for this SLO (e.g. {@code "ledger.posting"})
     * @param tenantId tenant context
     */
    public void recordSuccess(String sloName, String tenantId) {
        window(sloName, tenantId).success();
    }

    /**
     * Records a failing SLO-relevant event.
     *
     * @param sloName  identifier for this SLO
     * @param tenantId tenant context
     */
    public void recordFailure(String sloName, String tenantId) {
        SloWindow w = window(sloName, tenantId);
        w.failure();

        double rate = w.successRate();
        log.debug("SLO event failure: slo={} tenant={} successRate={:.4f}", sloName, tenantId, rate);
    }

    /**
     * Returns the current success rate (0.0–1.0) for the given SLO within the rolling window.
     */
    public double successRate(String sloName, String tenantId) {
        return window(sloName, tenantId).successRate();
    }

    /**
     * Returns {@code true} if the SLO success rate is below the given target threshold.
     *
     * @param sloName   SLO identifier
     * @param tenantId  tenant context
     * @param threshold minimum required success rate (e.g. 0.9999 for four-nines)
     */
    public boolean isBreached(String sloName, String tenantId, double threshold) {
        return successRate(sloName, tenantId) < threshold;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private SloWindow window(String sloName, String tenantId) {
        String key = sloName + "#" + tenantId;
        return sloWindows.computeIfAbsent(key, k -> {
            SloWindow w = new SloWindow(windowMs);
            // Register gauge for Prometheus
            Gauge.builder("slo.compliance.rate", w, SloWindow::successRate)
                    .tag("slo",    sloName)
                    .tag("tenant", tenantId)
                    .description("Rolling SLO success rate")
                    .register(registry);
            return w;
        });
    }

    /** Lightweight sliding-window success/failure tracker. */
    private static final class SloWindow {
        private final long windowMs;
        private final AtomicLong successes = new AtomicLong();
        private final AtomicLong failures  = new AtomicLong();
        private volatile long windowStart  = System.currentTimeMillis();

        SloWindow(long windowMs) {
            this.windowMs = windowMs;
        }

        void success() {
            maybeReset();
            successes.incrementAndGet();
        }

        void failure() {
            maybeReset();
            failures.incrementAndGet();
        }

        double successRate() {
            maybeReset();
            long s = successes.get();
            long f = failures.get();
            long total = s + f;
            return total == 0 ? 1.0 : (double) s / total;
        }

        private synchronized void maybeReset() {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                successes.set(0);
                failures.set(0);
                windowStart = now;
            }
        }
    }
}
