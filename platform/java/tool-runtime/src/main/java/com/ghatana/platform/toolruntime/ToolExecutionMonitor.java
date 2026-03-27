/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import io.activej.promise.Promise;
import java.time.Duration;

/**
 * Tracks resource usage for a single tool execution.
 *
 * <p>Implementations record latency, result size, and optionally memory/CPU
 * metrics, then expose aggregate statistics for alerting and capacity planning.
 *
 * @doc.type interface
 * @doc.purpose Record and expose per-tool resource usage metrics
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ToolExecutionMonitor {

    /**
     * Record a completed tool execution.
     *
     * @param tenantId    owning tenant
     * @param agentId     calling agent
     * @param toolName    the tool that was executed
     * @param duration    wall-clock time of the execution
     * @param outputBytes approximate byte size of the tool output
     * @param success     {@code true} if the execution completed without error
     * @return completed promise
     */
    Promise<Void> record(
        String tenantId,
        String agentId,
        String toolName,
        Duration duration,
        long outputBytes,
        boolean success);

    /**
     * Returns aggregate statistics for a given tool within a tenant.
     *
     * @param tenantId owning tenant
     * @param toolName the tool name
     * @return a snapshot of the execution statistics
     */
    Promise<ToolExecutionStats> getStats(String tenantId, String toolName);
}
