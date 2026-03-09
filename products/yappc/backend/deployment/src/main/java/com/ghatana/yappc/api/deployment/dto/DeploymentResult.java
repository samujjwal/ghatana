/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Deployment result record.
 * 
 * @param deploymentId Unique deployment ID
 * @param status Deployment status
 * @param timestamp Deployment timestamp
 * 
 * @doc.type record
 * @doc.purpose Deployment result DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record DeploymentResult(
        String deploymentId,
        String status,
        Instant timestamp
) {}
