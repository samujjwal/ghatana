/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Health check facade for the AEP engine.
 *
 * <p>Collects readiness signals from registered probes (pipeline availability,
 * state-store reachability, agent-registry load state, etc.) and aggregates
 * them into an overall {@link Status}. This maps directly to the
 * {@code /health} and {@code /health/ready} endpoints exposed by the AEP HTTP
 * layer.
 *
 * <p>Probes are registered once at start-up via {@link #registerProbe} and
 * evaluated on every {@link #check()} call. A single {@code DOWN} probe
 * causes the aggregate status to be {@code DOWN}.
 *
 * <p>This class is thread-safe.
 *
 * @doc.type class
 * @doc.purpose Aggregate AEP health signals into an overall readiness status
 * @doc.layer observability
 * @doc.pattern HealthCheck
 */
public final class AepHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(AepHealthCheck.class);

    /**
     * Possible health statuses.
     */
    public enum Status {
        /** All probes are healthy. */
        UP,
        /** One or more probes are unhealthy. */
        DOWN
    }

    /**
     * Result of a health-check evaluation.
     *
     * @param status    aggregate status across all probes
     * @param checkedAt timestamp at which the check was performed
     * @param details   per-probe status map ({@code probeName → "UP" | "DOWN"})
     */
    public record HealthResult(
            Status status,
            Instant checkedAt,
            Map<String, String> details) {}

    private final Map<String, BooleanSupplier> probes = new ConcurrentHashMap<>();

    /**
     * Registers a named health probe.
     *
     * <p>If a probe with the same name already exists it is replaced.
     *
     * @param name  unique probe name (e.g., {@code "state-store"})
     * @param probe returns {@code true} when healthy
     */
    public void registerProbe(String name, BooleanSupplier probe) {
        Objects.requireNonNull(name,  "probe name must not be null");
        Objects.requireNonNull(probe, "probe must not be null");
        probes.put(name, probe);
    }

    /**
     * Removes a named probe.
     *
     * @param name the probe to remove
     */
    public void deregisterProbe(String name) {
        probes.remove(name);
    }

    /**
     * Evaluates all registered probes and returns an aggregated result.
     *
     * <p>Probe exceptions are caught and treated as {@code DOWN}.
     *
     * @return aggregated {@link HealthResult}
     */
    public HealthResult check() {
        Map<String, String> details = new LinkedHashMap<>();
        boolean allUp = true;

        for (Map.Entry<String, BooleanSupplier> entry : probes.entrySet()) {
            String probeName = entry.getKey();
            boolean healthy;
            try {
                healthy = entry.getValue().getAsBoolean();
            } catch (Exception e) {
                log.warn("Health probe '{}' threw an exception — treating as DOWN", probeName, e);
                healthy = false;
            }
            details.put(probeName, healthy ? Status.UP.name() : Status.DOWN.name());
            if (!healthy) {
                allUp = false;
            }
        }

        Status aggregate = allUp ? Status.UP : Status.DOWN;
        return new HealthResult(aggregate, Instant.now(), Collections.unmodifiableMap(details));
    }

    /**
     * Convenience method: returns {@code true} when {@link #check()} yields {@link Status#UP}.
     *
     * @return {@code true} if AEP is healthy
     */
    public boolean isHealthy() {
        return check().status() == Status.UP;
    }

    /**
     * Returns the number of registered probes.
     *
     * @return probe count
     */
    public int probeCount() {
        return probes.size();
    }
}
