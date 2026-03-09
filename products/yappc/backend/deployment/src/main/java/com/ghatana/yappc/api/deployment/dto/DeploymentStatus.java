/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Deployment status record.
 * 
 * @param deploymentId Deployment ID
 * @param status Current status (PENDING/IN_PROGRESS/COMPLETED/FAILED/ROLLED_BACK)
 * @param applicationName Application name
 * @param environment Environment
 * @param version Deployed version
 * @param progress Progress percentage (0-100)
 * @param startedAt Start timestamp
 * @param completedAt Completion timestamp
 * @param message Status message
 * 
 * @doc.type record
 * @doc.purpose Deployment status DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record DeploymentStatus(
        String deploymentId,
        String status,
        String applicationName,
        String environment,
        String version,
        int progress,
        Instant startedAt,
        Instant completedAt,
        String message
) {}
