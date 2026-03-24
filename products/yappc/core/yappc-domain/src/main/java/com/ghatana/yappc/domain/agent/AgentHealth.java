package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Health status of an AI agent.
 *
 * @doc.type record
 * @doc.purpose Agent health status
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record AgentHealth(
        boolean healthy,
        long latencyMs,
        @NotNull Instant lastCheck,
        @NotNull Map<String, DependencyStatus> dependencies,
        @Nullable String errorMessage
) {

    /**
     * Dependency health status.
     */
    public enum DependencyStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }

    /**
     * Creates a healthy status.
     */
    public static AgentHealth healthy(long latencyMs) {
        return new AgentHealth(true, latencyMs, Instant.now(), Map.of(), null);
    }

    /**
     * Creates a healthy status with dependencies.
     */
    public static AgentHealth healthy(long latencyMs, Map<String, DependencyStatus> dependencies) {
        return new AgentHealth(true, latencyMs, Instant.now(), dependencies, null);
    }

    /**
     * Creates an unhealthy status.
     */
    public static AgentHealth unhealthy(String errorMessage) {
        return new AgentHealth(false, 0, Instant.now(), Map.of(), errorMessage);
    }

    /**
     * Creates an unhealthy status with latency.
     */
    public static AgentHealth unhealthy(long latencyMs, String errorMessage) {
        return new AgentHealth(false, latencyMs, Instant.now(), Map.of(), errorMessage);
    }
}
