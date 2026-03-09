package com.ghatana.evaluation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Performance metrics collected during an evaluation.
 * This class captures metrics related to execution time, resource usage, and throughput.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceMetrics {

    /**
     * The time taken to start up the component in milliseconds.
     */
    private Long startupTimeMs;

    /**
     * The time taken to shut down the component in milliseconds.
     */
    private Long shutdownTimeMs;

    /**
     * The peak memory usage in megabytes.
     */
    private Double peakMemoryUsageMb;

    /**
     * The average memory usage in megabytes.
     */
    private Double averageMemoryUsageMb;

    /**
     * The peak CPU usage as a percentage (0-100).
     */
    private Double peakCpuUsagePercent;

    /**
     * The average CPU usage as a percentage (0-100).
     */
    private Double averageCpuUsagePercent;

    /**
     * The peak disk I/O in bytes per second.
     */
    private Long peakDiskIoBps;

    /**
     * The average disk I/O in bytes per second.
     */
    private Long averageDiskIoBps;

    /**
     * The peak network I/O in bytes per second.
     */
    private Long peakNetworkIoBps;

    /**
     * The average network I/O in bytes per second.
     */
    private Long averageNetworkIoBps;

    /**
     * The throughput in operations per second.
     */
    private Double throughputOps;

    /**
     * The average latency in milliseconds.
     */
    private Double averageLatencyMs;

    /**
     * The 95th percentile latency in milliseconds.
     */
    private Double p95LatencyMs;

    /**
     * The 99th percentile latency in milliseconds.
     */
    private Double p99LatencyMs;

    /**
     * The maximum latency in milliseconds.
     */
    private Double maxLatencyMs;

    /**
     * Additional performance metrics.
     */
    private Map<String, Object> additionalMetrics;
}
