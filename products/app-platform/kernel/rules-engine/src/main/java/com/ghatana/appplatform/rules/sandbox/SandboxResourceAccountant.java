/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.sandbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks resource usage per T2 rule execution (STORY-K03-006).
 *
 * <p>Metrics collected per jurisdiction through Micrometer:
 * <ul>
 *   <li>{@code t2.rule.executions_total} — execution count</li>
 *   <li>{@code t2.rule.errors_total}     — error count</li>
 *   <li>{@code t2.rule.timeouts_total}   — timeout count</li>
 *   <li>{@code t2.rule.duration_ms}      — execution duration histogram</li>
 * </ul>
 *
 * <p>A warning is logged when execution rate approaches quota limits (80%).
 *
 * @doc.type  class
 * @doc.purpose Per-jurisdiction T2 rule resource accounting and quota monitoring (K03-006)
 * @doc.layer product
 * @doc.pattern Monitor
 */
public final class SandboxResourceAccountant {

    private static final Logger log = LoggerFactory.getLogger(SandboxResourceAccountant.class);

    private final String jurisdictionId;
    private final AtomicLong executionCount  = new AtomicLong();
    private final AtomicLong errorCount      = new AtomicLong();
    private final AtomicLong timeoutCount    = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> ruleCallCounts = new ConcurrentHashMap<>();

    private MeterRegistry meterRegistry;

    public SandboxResourceAccountant(String jurisdictionId) {
        this.jurisdictionId = Objects.requireNonNull(jurisdictionId, "jurisdictionId");
    }

    /**
     * Optionally bind a Micrometer registry for metric export.
     *
     * @param registry Micrometer meter registry
     */
    public void bindMeterRegistry(MeterRegistry registry) {
        this.meterRegistry = Objects.requireNonNull(registry, "registry");
    }

    // ── Recording methods ─────────────────────────────────────────────────────

    void recordExecution(String ruleId) {
        long count = executionCount.incrementAndGet();
        ruleCallCounts.computeIfAbsent(ruleId, k -> new AtomicLong()).incrementAndGet();
        if (meterRegistry != null) {
            counter("t2.rule.executions_total", ruleId).increment();
        }
        log.debug("[T2:{}] rule={} executions={}", jurisdictionId, ruleId, count);
    }

    void recordDuration(String ruleId, long elapsedMs) {
        if (meterRegistry != null) {
            timer("t2.rule.duration_ms", ruleId).record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        if (elapsedMs > 80) { // warn at 80% of 100ms default timeout
            log.warn("[T2:{}] rule={} slow execution: {}ms (quota=100ms)", jurisdictionId, ruleId, elapsedMs);
        }
    }

    void recordError(String ruleId) {
        errorCount.incrementAndGet();
        if (meterRegistry != null) {
            counter("t2.rule.errors_total", ruleId).increment();
        }
        log.warn("[T2:{}] rule={} execution error recorded", jurisdictionId, ruleId);
    }

    void recordTimeout(String ruleId) {
        timeoutCount.incrementAndGet();
        if (meterRegistry != null) {
            counter("t2.rule.timeouts_total", ruleId).increment();
        }
        log.warn("[T2:{}] rule={} execution timed out", jurisdictionId, ruleId);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public long executionCount()  { return executionCount.get(); }
    public long errorCount()      { return errorCount.get(); }
    public long timeoutCount()    { return timeoutCount.get(); }
    public String jurisdictionId(){ return jurisdictionId; }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Counter counter(String name, String ruleId) {
        return Counter.builder(name)
                .tag("jurisdiction", jurisdictionId)
                .tag("rule_id", ruleId)
                .register(meterRegistry);
    }

    private Timer timer(String name, String ruleId) {
        return Timer.builder(name)
                .tag("jurisdiction", jurisdictionId)
                .tag("rule_id", ruleId)
                .register(meterRegistry);
    }
}
