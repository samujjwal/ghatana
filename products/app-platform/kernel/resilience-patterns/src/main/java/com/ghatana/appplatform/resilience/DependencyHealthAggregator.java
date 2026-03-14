/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.resilience;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates health checks from all registered kernel dependencies (K18-012).
 *
 * <p>Each dependency registers a named {@link HealthCheck} probe. Callers invoke
 * {@link #checkAll()} to run all probes in parallel and aggregate the results into a
 * {@link SystemHealth} summary.
 *
 * <p>Overall status rules:
 * <ul>
 *   <li>{@code UP} — all probes healthy</li>
 *   <li>{@code DEGRADED} — at least one probe is WARNING</li>
 *   <li>{@code DOWN} — at least one probe is CRITICAL</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Parallel dependency health aggregation for K-18 health endpoint (K18-012)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DependencyHealthAggregator {

    private static final Logger log = LoggerFactory.getLogger(DependencyHealthAggregator.class);

    private final Map<String, HealthCheck> checks = new ConcurrentHashMap<>();

    /**
     * Registers a named health probe.
     *
     * @param name  dependency name (e.g., {@code "postgres"}, {@code "redis"})
     * @param probe async probe returning a {@link CheckResult}
     */
    public void register(String name, HealthCheck probe) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(probe, "probe");
        checks.put(name, probe);
    }

    /**
     * Runs all registered probes in parallel and returns an aggregated health summary.
     *
     * @return aggregated {@link SystemHealth}
     */
    public Promise<SystemHealth> checkAll() {
        if (checks.isEmpty()) {
            return Promise.of(new SystemHealth(Status.UP, List.of(), Instant.now()));
        }

        List<Promise<NamedResult>> promises = new ArrayList<>();
        for (Map.Entry<String, HealthCheck> entry : checks.entrySet()) {
            String name   = entry.getKey();
            HealthCheck p = entry.getValue();
            promises.add(
                p.check()
                 .mapException(e -> new RuntimeException(name + " check failed: " + e.getMessage(), e))
                 .map(r -> new NamedResult(name, r))
                 .mapException(e -> new NamedResult(name, new CheckResult(
                     Status.CRITICAL, e.getMessage(), Instant.now())))
            );
        }

        return Promises.toList(promises).map(results -> {
            Status overall = aggregateStatus(results);
            log.info("Health check complete: overall={} checks={}", overall, results.size());
            return new SystemHealth(overall, results, Instant.now());
        });
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private static Status aggregateStatus(List<NamedResult> results) {
        boolean hasCritical = results.stream().anyMatch(r -> r.result().status() == Status.CRITICAL);
        boolean hasWarning  = results.stream().anyMatch(r -> r.result().status() == Status.WARNING);
        if (hasCritical) return Status.DOWN;
        if (hasWarning)  return Status.DEGRADED;
        return Status.UP;
    }

    // ─── Domain types ─────────────────────────────────────────────────────────

    /** Health status for a single dependency or the overall system. */
    public enum Status {
        /** Fully operational. */
        UP,
        /** One or more non-critical dependencies are degraded. */
        DEGRADED,
        /** One or more critical dependencies are unavailable. */
        DOWN,
        /** Probe result is warning — service is operational but approaching limits. */
        WARNING,
        /** Probe result is critical — service is unavailable. */
        CRITICAL
    }

    /**
     * Result of a single health probe.
     *
     * @param status    probe status (UP, WARNING, or CRITICAL)
     * @param message   human-readable detail (used in health endpoint JSON)
     * @param checkedAt timestamp when the probe completed
     */
    public record CheckResult(Status status, String message, Instant checkedAt) {}

    /** {@link CheckResult} paired with its probe name. */
    public record NamedResult(String name, CheckResult result) {}

    /**
     * Aggregated health of all registered dependencies.
     *
     * @param overall   aggregate system status
     * @param checks    per-probe results
     * @param checkedAt timestamp of the aggregation
     */
    public record SystemHealth(Status overall, List<NamedResult> checks, Instant checkedAt) {}

    /**
     * Async health probe interface.
     *
     * @doc.type interface
     * @doc.purpose Async health probe port for dependency health checks (K18-012)
     * @doc.layer product
     * @doc.pattern Port
     */
    @FunctionalInterface
    public interface HealthCheck {
        Promise<CheckResult> check();
    }
}
