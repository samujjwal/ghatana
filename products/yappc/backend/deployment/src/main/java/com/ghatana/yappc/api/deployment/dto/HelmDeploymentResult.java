/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * HelmDeploymentResult.
 *
 * @doc.type record
 * @doc.purpose helm deployment result
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HelmDeploymentResult(
    String deploymentId,
    String status,
    String message,
    String version,
    Instant timestamp
) {}
