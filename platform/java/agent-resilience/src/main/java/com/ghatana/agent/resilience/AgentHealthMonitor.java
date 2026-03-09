/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.resilience;

import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the health of registered agents and their associated resilience stacks.
 *
 * <p>Aggregates:
 * <ul>
 *   <li>Per-agent {@link HealthStatus} from {@code TypedAgent.healthCheck()}</li>
 *   <li>Circuit-breaker state per agent</li>
 *   <li>Bulkhead utilization per agent</li>
 *   <li>Last-seen health check timestamp</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentHealthMonitor monitor = new AgentHealthMonitor();
 * monitor.register("my-agent", myAgent, circuitBreaker, bulkhead);
 *
 * // Periodic health scan
 * monitor.checkAll().whenResult(report -> log.info("Health: {}", report));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Aggregated health monitor for TypedAgent resilience stacks
 * @doc.layer platform
 * @doc.pattern Observer
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class AgentHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthMonitor.class);

    /**
     * Immutable snapshot of an agent's health state at a point in time.
     */
    public record AgentHealthSnapshot(
            String agentId,
            HealthStatus agentStatus,
            CircuitBreaker.State circuitBreakerState,
            double bulkheadUtilization,
            Instant checkedAt,
            String summary
    ) {
        public boolean isHealthy() {
            return agentStatus == HealthStatus.HEALTHY
                    && circuitBreakerState != CircuitBreaker.State.OPEN;
        }
    }

    private record Entry(
            TypedAgent<?, ?> agent,
            CircuitBreaker circuitBreaker,
            AgentBulkhead bulkhead
    ) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentHealthSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    /**
     * Registers an agent (with optional resilience components) for monitoring.
     *
     * @param agentId        agent identifier
     * @param agent          the agent to monitor
     * @param circuitBreaker associated circuit breaker (may be null)
     * @param bulkhead       associated bulkhead (may be null)
     */
    public void register(
            @NotNull String agentId,
            @NotNull TypedAgent<?, ?> agent,
            CircuitBreaker circuitBreaker,
            AgentBulkhead bulkhead) {
        entries.put(agentId, new Entry(agent, circuitBreaker, bulkhead));
        log.debug("AgentHealthMonitor: registered agent '{}'", agentId);
    }

    /**
     * Unregisters an agent from monitoring.
     */
    public void unregister(@NotNull String agentId) {
        entries.remove(agentId);
        lastSnapshots.remove(agentId);
    }

    /**
     * Checks the health of a single registered agent.
     *
     * @param agentId agent identifier
     * @return a Promise of the health snapshot
     */
    @NotNull
    public Promise<AgentHealthSnapshot> check(@NotNull String agentId) {
        Entry entry = entries.get(agentId);
        if (entry == null) {
            return Promise.ofException(new NoSuchElementException("Agent not registered: " + agentId));
        }
        return buildSnapshot(agentId, entry);
    }

    /**
     * Checks the health of all registered agents and returns a map of snapshots.
     *
     * @return Promise of agentId → snapshot map
     */
    @NotNull
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Promise<Map<String, AgentHealthSnapshot>> checkAll() {
        if (entries.isEmpty()) {
            return Promise.of(Map.of());
        }

        List<Promise<AgentHealthSnapshot>> checks = entries.entrySet().stream()
                .map(e -> buildSnapshot(e.getKey(), e.getValue()))
                .toList();

        return Promises.toList(checks.toArray(new Promise[0]))
                .map(snapshots -> {
                    Map<String, AgentHealthSnapshot> result = new LinkedHashMap<>();
                    List<AgentHealthSnapshot> snapshotList = (List<AgentHealthSnapshot>) snapshots;
                    for (AgentHealthSnapshot s : snapshotList) {
                        result.put(s.agentId(), s);
                        lastSnapshots.put(s.agentId(), s);
                    }
                    return Collections.unmodifiableMap(result);
                });
    }

    /**
     * Returns the most recent cached snapshot for the given agent, or empty if not yet checked.
     */
    public Optional<AgentHealthSnapshot> getLastSnapshot(@NotNull String agentId) {
        return Optional.ofNullable(lastSnapshots.get(agentId));
    }

    /**
     * Returns the overall system health: HEALTHY only if all agents are healthy.
     * Returns DEGRADED if any agent is degraded. Returns UNHEALTHY if any is unhealthy.
     */
    @NotNull
    public Promise<HealthStatus> overallHealth() {
        return checkAll().map(snapshots -> {
            if (snapshots.isEmpty()) return HealthStatus.HEALTHY;
            AtomicReference<HealthStatus> worst = new AtomicReference<>(HealthStatus.HEALTHY);
            for (AgentHealthSnapshot s : snapshots.values()) {
                if (s.agentStatus() == HealthStatus.UNHEALTHY
                        || s.circuitBreakerState() == CircuitBreaker.State.OPEN) {
                    worst.set(HealthStatus.UNHEALTHY);
                    break;
                }
                if (s.agentStatus() == HealthStatus.DEGRADED
                        || s.circuitBreakerState() == CircuitBreaker.State.HALF_OPEN) {
                    worst.set(HealthStatus.DEGRADED);
                }
            }
            return worst.get();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Promise<AgentHealthSnapshot> buildSnapshot(String agentId, Entry entry) {
        return entry.agent().healthCheck()
                .map(status -> {
                    CircuitBreaker.State cbState = entry.circuitBreaker() != null
                            ? entry.circuitBreaker().getState()
                            : CircuitBreaker.State.CLOSED;

                    double utilization = entry.bulkhead() != null
                            ? entry.bulkhead().getUtilization()
                            : 0.0;

                    String summary = buildSummary(agentId, status, cbState, utilization);
                    AgentHealthSnapshot snap = new AgentHealthSnapshot(
                            agentId, status, cbState, utilization, Instant.now(), summary);
                    lastSnapshots.put(agentId, snap);

                    if (!snap.isHealthy()) {
                        log.warn("Agent health issue: {}", summary);
                    }
                    return snap;
                })
                .mapException(e -> {
                    AgentHealthSnapshot snap = new AgentHealthSnapshot(
                            agentId, HealthStatus.UNHEALTHY,
                            entry.circuitBreaker() != null
                                    ? entry.circuitBreaker().getState()
                                    : CircuitBreaker.State.CLOSED,
                            0.0, Instant.now(),
                            agentId + ": health-check threw " + e.getMessage());
                    lastSnapshots.put(agentId, snap);
                    return e;
                });
    }

    private static String buildSummary(
            String agentId, HealthStatus status,
            CircuitBreaker.State cbState, double utilization) {
        return String.format("%s status=%s cb=%s bulkhead=%.0f%%",
                agentId, status, cbState, utilization * 100);
    }
}
