/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Canary metrics record - real-time metrics during canary deployment.
 * 
 * @param canaryId Canary ID
 * @param errorRate Error rate (0.0-1.0)
 * @param latencyP99 P99 latency in milliseconds
 * @param latencyP95 P95 latency in milliseconds
 * @param latencyP50 P50 latency in milliseconds
 * @param requestCount Total request count
 * @param cpuUsage CPU usage percentage
 * @param memoryUsage Memory usage percentage
 * @param healthStatus Overall health status (HEALTHY/DEGRADED/UNHEALTHY)
 * @param timestamp Metrics timestamp
 * 
 * @doc.type record
 * @doc.purpose Canary metrics DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CanaryMetrics(
        String canaryId,
        double errorRate,
        double latencyP99,
        double latencyP95,
        double latencyP50,
        int requestCount,
        double cpuUsage,
        double memoryUsage,
        String healthStatus,
        Instant timestamp
) {}
