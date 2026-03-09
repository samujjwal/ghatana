/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * RollbackResult.
 *
 * @doc.type record
 * @doc.purpose rollback result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RollbackResult(
    String deploymentId,
    String status,
    String reason,
    Instant timestamp
) {}
