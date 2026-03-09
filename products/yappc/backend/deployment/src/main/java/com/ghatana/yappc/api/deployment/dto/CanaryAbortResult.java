/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Result of canary abort.
 *
 * @param executionId Execution ID
 * @param status Status
 * @param reason Reason for abort
 * @param timestamp Timestamp
 *
 * @doc.type record
 * @doc.purpose DTO for abort result
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CanaryAbortResult(
    String executionId,
    String status,
    String reason,
    Instant timestamp
) {}
