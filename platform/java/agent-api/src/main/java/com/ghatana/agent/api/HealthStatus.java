/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

/**
 * Operational status of an agent during its lifecycle.
 *
 * @doc.type enum
 * @doc.purpose Agent lifecycle status
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum HealthStatus {

    /** Fully operational and accepting input. */
    HEALTHY,

    /** Operational but experiencing degraded performance. */
    DEGRADED,

    /** Not operational. All processing requests will fail. */
    UNHEALTHY,

    /** Initializing — not yet accepting input. */
    STARTING,

    /** Shutting down — will stop accepting new input. */
    STOPPING,

    /** Status unknown (e.g., health check timed out). */
    UNKNOWN
}
