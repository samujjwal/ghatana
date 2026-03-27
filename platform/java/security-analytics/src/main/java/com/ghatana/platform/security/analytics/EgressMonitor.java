/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import io.activej.promise.Promise;

/**
 * Monitors agent data-egress events and emits alerts when egress exceeds
 * byte or rate thresholds.
 *
 * <p>Implementations apply per-tenant thresholds. Violations are reported
 * as {@link EgressAlert} records which callers may route to an incident
 * pipeline or logging infrastructure.
 *
 * @doc.type interface
 * @doc.purpose Monitor and alert on excessive data egress from agents
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface EgressMonitor {

    /**
     * Record an egress event.
     *
     * @param tenantId   owning tenant
     * @param agentId    the agent that caused egress
     * @param toolName   the tool that produced the egress
     * @param bytesCount number of bytes transferred
     * @return a promise: completes normally when within limits, fails with {@link EgressLimitExceededException} otherwise
     */
    Promise<Void> record(String tenantId, String agentId, String toolName, long bytesCount);

    /**
     * Returns the current total egress byte count for the given agent within the active window.
     *
     * @param tenantId owning tenant
     * @param agentId  the agent to query
     * @return promise resolving to current window byte total
     */
    Promise<Long> currentWindowBytes(String tenantId, String agentId);
}
