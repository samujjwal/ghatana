/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.runtime;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginResourceQuota;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces per-plugin resource quotas at runtime (STORY-K04-014).
 *
 * <p>Tracks cumulative resource usage for each plugin and rejects invocations that
 * would exceed the limits declared in {@link PluginManifest#resourceQuota()}.
 * Currently enforced limits:
 * <ul>
 *   <li>Max API calls per minute (sliding window per plugin)</li>
 *   <li>Max payload size per invocation (kilobytes)</li>
 * </ul>
 *
 * <p>CPU and memory limits for T2 plugins are enforced separately by {@code T2RuleSandbox}.
 * T3 CPU/memory limits are enforced by Kubernetes cgroups.
 *
 * @doc.type  class
 * @doc.purpose Runtime quota enforcement for plugin API calls and payload sizes (K04-014)
 * @doc.layer kernel
 * @doc.pattern Guard
 */
public final class PluginResourceEnforcer {

    private static final Logger log = LoggerFactory.getLogger(PluginResourceEnforcer.class);

    /** Sliding window duration in milliseconds. */
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, WindowedCounter> apiCallCounters = new ConcurrentHashMap<>();
    private final Counter quotaViolations;

    public PluginResourceEnforcer(MeterRegistry meterRegistry) {
        this.quotaViolations = Counter.builder("plugin.quota.violations.total")
                .description("Number of plugin quota violations detected")
                .register(Objects.requireNonNull(meterRegistry, "meterRegistry"));
    }

    /**
     * Asserts that the given plugin may make another API call with the specified payload.
     *
     * @param manifest     the plugin manifest containing quota limits
     * @param payloadBytes size of the request/response payload in bytes
     * @throws QuotaExceededException if a quota limit is breached
     */
    public void enforce(PluginManifest manifest, int payloadBytes) {
        Objects.requireNonNull(manifest, "manifest");

        PluginResourceQuota quota = manifest.resourceQuota();

        // Payload size check
        int payloadKb = (payloadBytes + 1023) / 1024; // ceil
        if (payloadKb > quota.maxPayloadKb()) {
            quotaViolations.increment();
            throw new QuotaExceededException(
                    "Plugin '" + manifest.name() + "' payload " + payloadKb
                            + " KB exceeds limit " + quota.maxPayloadKb() + " KB");
        }

        // API-calls-per-minute check (0 = unlimited)
        if (quota.maxApiCallsPerMinute() > 0) {
            WindowedCounter counter = apiCallCounters.computeIfAbsent(
                    manifest.name(), k -> new WindowedCounter(WINDOW_MS));

            long current = counter.incrementAndGet();
            if (current > quota.maxApiCallsPerMinute()) {
                quotaViolations.increment();
                throw new QuotaExceededException(
                        "Plugin '" + manifest.name() + "' exceeded API call limit: "
                                + current + " > " + quota.maxApiCallsPerMinute() + "/min");
            }
        }
    }

    // ── Internal: sliding window counter ─────────────────────────────────────

    /** Thread-safe sliding window counter. Resets the count when epoch changes. */
    private static final class WindowedCounter {
        private final long windowMs;
        private volatile long epoch;
        private volatile long count;

        WindowedCounter(long windowMs) {
            this.windowMs = windowMs;
            this.epoch    = currentEpoch(windowMs);
        }

        synchronized long incrementAndGet() {
            long now = currentEpoch(windowMs);
            if (now != epoch) {
                epoch = now;
                count = 0;
            }
            return ++count;
        }

        private static long currentEpoch(long windowMs) {
            return System.currentTimeMillis() / windowMs;
        }
    }

    /** Thrown when a plugin exceeds a declared resource quota. */
    public static final class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) { super(message); }
    }
}
