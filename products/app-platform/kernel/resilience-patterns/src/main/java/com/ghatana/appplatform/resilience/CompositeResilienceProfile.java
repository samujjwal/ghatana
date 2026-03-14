/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import com.ghatana.platform.resilience.RetryPolicy;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Pre-defined composite resilience profiles for kernel services (K18-010).
 *
 * <p>Three tiers, each combining a circuit breaker, bulkhead, and retry policy:
 *
 * <table border="1">
 *   <tr><th>Profile</th><th>Use Case</th><th>CB</th><th>Bulkhead</th><th>Max Retries</th></tr>
 *   <tr><td>TRADING_CRITICAL</td><td>Settlement, ledger post, DVP</td>
 *       <td>STRICT (60s open)</td><td>50</td><td>2 (50ms→500ms exp)</td></tr>
 *   <tr><td>STANDARD_SERVICE</td><td>IAM, config reads, calendar</td>
 *       <td>STANDARD (30s open)</td><td>100</td><td>3 (100ms→2s exp)</td></tr>
 *   <tr><td>BACKGROUND_JOB</td><td>Outbox relay, cleanup, snapshots</td>
 *       <td>LENIENT (15s open)</td><td>10</td><td>5 (500ms→30s exp)</td></tr>
 * </table>
 *
 * @doc.type class
 * @doc.purpose Named composite resilience profiles — TRADING_CRITICAL / STANDARD_SERVICE / BACKGROUND_JOB (K18-010)
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class CompositeResilienceProfile {

    private static final Logger log = LoggerFactory.getLogger(CompositeResilienceProfile.class);

    // ─── Profile names (stable identifiers used in CB registry) ──────────────

    public static final String TRADING_CRITICAL  = "TRADING_CRITICAL";
    public static final String STANDARD_SERVICE  = "STANDARD_SERVICE";
    public static final String BACKGROUND_JOB    = "BACKGROUND_JOB";

    // ─── Circuit breakers ─────────────────────────────────────────────────────

    private final CircuitBreaker tradingCb;
    private final CircuitBreaker standardCb;
    private final CircuitBreaker backgroundCb;

    // ─── Bulkheads ────────────────────────────────────────────────────────────

    private final Bulkhead tradingBulkhead;
    private final Bulkhead standardBulkhead;
    private final Bulkhead backgroundBulkhead;

    // ─── Retry policies ───────────────────────────────────────────────────────

    private final RetryPolicy tradingRetry;
    private final RetryPolicy standardRetry;
    private final RetryPolicy backgroundRetry;

    /**
     * Creates a {@code CompositeResilienceProfile} with the given name suffix for
     * circuit breaker registry keys (allows multiple instances per service).
     *
     * @param nameSuffix optional suffix appended to CB keys (e.g., tenant ID)
     */
    public CompositeResilienceProfile(String nameSuffix) {
        String suffix = (nameSuffix == null || nameSuffix.isBlank()) ? "" : "." + nameSuffix;

        // ── TRADING_CRITICAL ──
        this.tradingCb = CircuitBreakerProfiles.strict("profile.trading" + suffix);
        this.tradingBulkhead = Bulkhead.of("profile.trading" + suffix, 50);
        this.tradingRetry = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(50))
                .maxDelay(Duration.ofMillis(500))
                .multiplier(2.0)
                .jitter(0.1)
                .build();

        // ── STANDARD_SERVICE ──
        this.standardCb = CircuitBreakerProfiles.standard("profile.standard" + suffix);
        this.standardBulkhead = Bulkhead.of("profile.standard" + suffix, 100);
        this.standardRetry = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(2))
                .multiplier(2.0)
                .jitter(0.25)
                .build();

        // ── BACKGROUND_JOB ──
        this.backgroundCb = CircuitBreakerProfiles.lenient("profile.background" + suffix);
        this.backgroundBulkhead = Bulkhead.of("profile.background" + suffix, 10);
        this.backgroundRetry = RetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofSeconds(30))
                .multiplier(3.0)
                .jitter(0.3)
                .build();
    }

    /** Convenience constructor with no suffix. */
    public CompositeResilienceProfile() {
        this(null);
    }

    // ─── Execution API ────────────────────────────────────────────────────────

    /**
     * Executes {@code operation} under the {@code TRADING_CRITICAL} profile
     * (strict CB, bulkhead=50, 2 retries with 50–500ms backoff).
     */
    public <T> Promise<T> trading(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(tradingBulkhead,
                () -> tradingRetry.execute(eventloop,
                        () -> tradingCb.execute(eventloop, operation, null)));
    }

    /**
     * Executes {@code operation} under the {@code STANDARD_SERVICE} profile
     * (standard CB, bulkhead=100, 3 retries with 100ms–2s backoff).
     */
    public <T> Promise<T> standard(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(standardBulkhead,
                () -> standardRetry.execute(eventloop,
                        () -> standardCb.execute(eventloop, operation, null)));
    }

    /**
     * Executes {@code operation} under the {@code BACKGROUND_JOB} profile
     * (lenient CB, bulkhead=10, 5 retries with 500ms–30s backoff).
     */
    public <T> Promise<T> background(Eventloop eventloop, Supplier<Promise<T>> operation) {
        return withBulkhead(backgroundBulkhead,
                () -> backgroundRetry.execute(eventloop,
                        () -> backgroundCb.execute(eventloop, operation, null)));
    }

    // ─── State inspection ─────────────────────────────────────────────────────

    /** Circuit breaker state for the TRADING_CRITICAL profile. */
    public CircuitBreaker.State tradingState()    { return tradingCb.getState(); }
    /** Circuit breaker state for the STANDARD_SERVICE profile. */
    public CircuitBreaker.State standardState()  { return standardCb.getState(); }
    /** Circuit breaker state for the BACKGROUND_JOB profile. */
    public CircuitBreaker.State backgroundState(){ return backgroundCb.getState(); }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private <T> Promise<T> withBulkhead(Bulkhead bulkhead, Supplier<Promise<T>> operation) {
        try {
            return bulkhead.tryExecuteBlocking(operation::get);
        } catch (Bulkhead.BulkheadFullException e) {
            log.warn("[resilience] bulkhead {} full", bulkhead.getName());
            return Promise.ofException(e);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
