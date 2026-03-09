/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Result of canary deployment initiation.
 *
 * @param executionId Execution ID
 * @param status Status
 * @param message Message
 * @param timestamp Timestamp
 *
 * @doc.type record
 * @doc.purpose DTO for deployment result
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CanaryDeploymentResult(
    String executionId,
    String status,
    String message,
    Instant timestamp
) {}
