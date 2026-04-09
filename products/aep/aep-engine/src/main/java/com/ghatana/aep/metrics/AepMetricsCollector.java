/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsProvider;
import com.ghatana.platform.observability.NoopMetricsCollector;

import java.util.Objects;

/**
 * AEP-standard metrics facade (AEP-007).
 *
 * <p>Previously, metrics were collected inconsistently across AEP modules:
 * some used the platform {@link MetricsCollector} directly, others accessed
 * Prometheus / Micrometer registries, and naming conventions diverged.
 * This class centralises all AEP metric names and provides typed helpers
 * so every component emits metrics in the same way.
 *
 * <p><b>Metric naming conventions:</b> {@code aep.<component>.<operation>}
 * (e.g. {@code aep.engine.events.processed}, {@code aep.consent.evaluations}).
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepMetricsCollector metrics = AepMetricsCollector.create();
 * metrics.incrementEventsProcessed("tenant-1");
 * metrics.incrementConsentDenied("tenant-1");
 * }</pre>
 *
 * <p>For tests use {@link #noop()}; for production pass a registry from the
 * platform observability module via {@link #of(MetricsCollector)}.
 *
 * @doc.type class
 * @doc.purpose Standardised AEP metrics with consistent naming
 * @doc.layer product
 * @doc.pattern Facade
 * @since 1.1.0
 */
public final class AepMetricsCollector {

    // ─── Canonical metric names ────────────────────────────────────────────────

    public static final String EVENTS_PROCESSED    = "aep.engine.events.processed";
    public static final String EVENTS_SKIPPED      = "aep.engine.events.skipped";
    public static final String EVENTS_FAILED       = "aep.engine.events.failed";
    public static final String SCHEMA_VIOLATIONS   = "aep.engine.schema.violations";
    public static final String CONSENT_ALLOWED     = "aep.consent.evaluations.allowed";
    public static final String CONSENT_DENIED      = "aep.consent.evaluations.denied";
    public static final String PATTERNS_MATCHED    = "aep.engine.patterns.matched";
    public static final String IDEMPOTENCY_HITS    = "aep.engine.idempotency.hits";
    public static final String DELIVERY_SUCCESS    = "aep.delivery.success";
    public static final String DELIVERY_FAILED     = "aep.delivery.failed";
    public static final String CONNECTOR_RETRIES       = "aep.connector.retries";

    // ─── Performance / latency metrics (AEP-018) ──────────────────────────────
    public static final String EVENT_PROCESSING_TIME   = "aep.engine.events.processing.time.ms";
    public static final String CONSENT_EVAL_TIME       = "aep.consent.eval.time.ms";
    public static final String PATTERN_MATCH_TIME      = "aep.engine.pattern.match.time.ms";
    public static final String DELIVERY_TIME           = "aep.delivery.time.ms";
    public static final String CONNECTOR_RETRY_LATENCY = "aep.connector.retry.latency.ms";

    // ─── Operational metrics (AEP-017) ────────────────────────────────────────
    public static final String ACTIVE_PATTERNS         = "aep.engine.patterns.active";
    public static final String RATE_LIMITED_EVENTS     = "aep.engine.events.rate.limited";
    public static final String CACHE_CONSENT_HITS      = "aep.cache.consent.hits";
    public static final String CACHE_CONSENT_MISSES    = "aep.cache.consent.misses";
    public static final String VERSION_MIGRATIONS      = "aep.engine.version.migrations";

    private final MetricsCollector delegate;

    private AepMetricsCollector(MetricsCollector delegate) {
        this.delegate = Objects.requireNonNull(delegate, "MetricsCollector must not be null");
    }

    /**
     * Creates an instance backed by a default {@link MetricsCollector} from the factory.
     */
    public static AepMetricsCollector create() {
        return new AepMetricsCollector(MetricsProvider.getCollector());
    }

    /**
     * Wraps the supplied collector.
     *
     * @param delegate underlying collector; must not be {@code null}
     */
    public static AepMetricsCollector of(MetricsCollector delegate) {
        return new AepMetricsCollector(delegate);
    }

    /**
     * Returns a no-op instance suitable for tests.
     */
    public static AepMetricsCollector noop() {
        return new AepMetricsCollector(NoopMetricsCollector.getInstance());
    }

    // ─── Typed helpers ─────────────────────────────────────────────────────────

    /** Increments {@value #EVENTS_PROCESSED}. */
    public void incrementEventsProcessed(String tenantId) {
        delegate.incrementCounter(EVENTS_PROCESSED, "tenantId", tenantId);
    }

    /** Increments {@value #EVENTS_SKIPPED}. */
    public void incrementEventsSkipped(String tenantId) {
        delegate.incrementCounter(EVENTS_SKIPPED, "tenantId", tenantId);
    }

    /** Increments {@value #EVENTS_FAILED}. */
    public void incrementEventsFailed(String tenantId) {
        delegate.incrementCounter(EVENTS_FAILED, "tenantId", tenantId);
    }

    /** Increments {@value #SCHEMA_VIOLATIONS}. */
    public void incrementSchemaViolations(String tenantId) {
        delegate.incrementCounter(SCHEMA_VIOLATIONS, "tenantId", tenantId);
    }

    /** Increments {@value #CONSENT_ALLOWED}. */
    public void incrementConsentAllowed(String tenantId) {
        delegate.incrementCounter(CONSENT_ALLOWED, "tenantId", tenantId);
    }

    /** Increments {@value #CONSENT_DENIED}. */
    public void incrementConsentDenied(String tenantId) {
        delegate.incrementCounter(CONSENT_DENIED, "tenantId", tenantId);
    }

    /** Increments {@value #PATTERNS_MATCHED}. */
    public void incrementPatternsMatched(String tenantId, String patternId) {
        delegate.incrementCounter(PATTERNS_MATCHED, "tenantId", tenantId, "patternId", patternId);
    }

    /** Increments {@value #IDEMPOTENCY_HITS}. */
    public void incrementIdempotencyHits(String tenantId) {
        delegate.incrementCounter(IDEMPOTENCY_HITS, "tenantId", tenantId);
    }

    /** Increments {@value #DELIVERY_SUCCESS}. */
    public void incrementDeliverySuccess(String tenantId) {
        delegate.incrementCounter(DELIVERY_SUCCESS, "tenantId", tenantId);
    }

    /** Increments {@value #DELIVERY_FAILED}. */
    public void incrementDeliveryFailed(String tenantId) {
        delegate.incrementCounter(DELIVERY_FAILED, "tenantId", tenantId);
    }

    // ─── Performance / latency recording (AEP-018) ────────────────────────────

    /**
     * Records end-to-end event processing duration in milliseconds.
     *
     * @param tenantId      owning tenant
     * @param durationMs    elapsed wall-clock time from intake to completion
     */
    public void recordEventProcessingTime(String tenantId, long durationMs) {
        delegate.recordTimer(EVENT_PROCESSING_TIME, durationMs, "tenantId", tenantId);
    }

    /**
     * Records time spent evaluating consent in milliseconds.
     *
     * @param tenantId   owning tenant
     * @param durationMs elapsed time
     */
    public void recordConsentEvalTime(String tenantId, long durationMs) {
        delegate.recordTimer(CONSENT_EVAL_TIME, durationMs, "tenantId", tenantId);
    }

    /**
     * Records time spent in pattern matching logic in milliseconds.
     *
     * @param tenantId   owning tenant
     * @param durationMs elapsed time
     */
    public void recordPatternMatchTime(String tenantId, long durationMs) {
        delegate.recordTimer(PATTERN_MATCH_TIME, durationMs, "tenantId", tenantId);
    }

    /**
     * Records time spent delivering events to external destinations in milliseconds.
     *
     * @param tenantId      owning tenant
     * @param durationMs    elapsed time
     */
    public void recordDeliveryTime(String tenantId, long durationMs) {
        delegate.recordTimer(DELIVERY_TIME, durationMs, "tenantId", tenantId);
    }

    /** Increments {@value #RATE_LIMITED_EVENTS} when an event is dropped by rate limiting. */
    public void incrementRateLimited(String tenantId) {
        delegate.incrementCounter(RATE_LIMITED_EVENTS, "tenantId", tenantId);
    }

    /** Increments {@value #CACHE_CONSENT_HITS} when a consent decision is served from cache. */
    public void incrementConsentCacheHit(String tenantId) {
        delegate.incrementCounter(CACHE_CONSENT_HITS, "tenantId", tenantId);
    }

    /** Increments {@value #CACHE_CONSENT_MISSES} when consent cache does not have the entry. */
    public void incrementConsentCacheMiss(String tenantId) {
        delegate.incrementCounter(CACHE_CONSENT_MISSES, "tenantId", tenantId);
    }

    /** Records the current number of active registered patterns for a tenant. */
    public void gaugeActivePatterns(String tenantId, int count) {
        // Gauge doesn't support per-tenant tags in the platform interface;
        // use a namespaced metric name that encodes the tenant dimension.
        delegate.recordGauge(ACTIVE_PATTERNS + "." + tenantId, (double) count);
    }

    /** Increments {@value #VERSION_MIGRATIONS} when an event is migrated to the current version. */
    public void incrementVersionMigrations(String tenantId, String fromVersion) {
        delegate.incrementCounter(VERSION_MIGRATIONS, "tenantId", tenantId, "fromVersion", fromVersion);
    }

    // ─── Generic delegation towards the underlying collector ──────────────────

    /**
     * Generic counter increment with arbitrary tags. Prefer the typed helpers above.
     */
    public void counter(String name, String... tags) {
        delegate.incrementCounter(name, tags);
    }

    /**
     * Records a gauge value (current snapshot).
     *
     * @param name  metric name (use {@value #EVENTS_PROCESSED} naming convention)
     * @param value current gauge value
     */
    public void gauge(String name, double value) {
        delegate.recordGauge(name, value);
    }

    /**
     * Returns the underlying {@link MetricsCollector} for cases where raw access is required.
     */
    public MetricsCollector delegate() {
        return delegate;
    }
}
