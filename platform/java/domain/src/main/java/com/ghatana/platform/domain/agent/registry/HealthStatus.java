package com.ghatana.platform.domain.agent.registry;

/**
 * Operational health status of an agent instance.
 *
 * <p>This is the canonical health-status type for the platform domain layer,
 * used by {@link AgentMetrics#getHealthStatus()} and related observability contracts.
 *
 * @doc.type enum
 * @doc.purpose Canonical agent health status for the domain/observability layer
 * @doc.layer domain
 *
 * @since 2.1.0
 */
public enum HealthStatus {

    /** Agent is fully operational and accepting input. */
    HEALTHY,

    /** Agent is operational but experiencing degraded performance. */
    DEGRADED,

    /** Agent is not operational. All processing requests will fail. */
    UNHEALTHY,

    /** Agent status is unknown (e.g., health check timed out). */
    UNKNOWN
}
