/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

/**
 * Metric thresholds for canary analysis.
 *
 * @param maxErrorRate Maximum allowed error rate (0.0-1.0)
 * @param maxLatencyP99 Maximum allowed P99 latency in ms
 *
 * @doc.type record
 * @doc.purpose DTO for metric thresholds
 * @doc.layer product
 * @doc.pattern DTO
 */
public record MetricThresholds(
    Double maxErrorRate,
    Double maxLatencyP99
) {}
