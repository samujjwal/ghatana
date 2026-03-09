/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Result of canary promotion.
 *
 * @param executionId Execution ID
 * @param trafficPercentage New traffic percentage
 * @param message Message
 * @param timestamp Timestamp
 *
 * @doc.type record
 * @doc.purpose DTO for promotion result
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CanaryPromotionResult(
    String executionId,
    int trafficPercentage,
    String message,
    Instant timestamp
) {}
