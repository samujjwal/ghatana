/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

/**
 * Immutable aggregate statistics for a tool within a tenant.
 *
 * @param tenantId         the owning tenant
 * @param toolName         the tool name
 * @param totalInvocations total number of times the tool was called
 * @param successCount     number of successful executions
 * @param failureCount     number of failed executions
 * @param avgDurationMs    average wall-clock duration in milliseconds
 *
 * @doc.type record
 * @doc.purpose Immutable snapshot of per-tool execution statistics
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolExecutionStats(
    String tenantId,
    String toolName,
    long totalInvocations,
    long successCount,
    long failureCount,
    double avgDurationMs
) {
    /** Returns the overall success rate as a value between 0.0 and 1.0. */
    public double successRate() {
        return totalInvocations == 0 ? 0.0 : (double) successCount / totalInvocations;
    }
}
