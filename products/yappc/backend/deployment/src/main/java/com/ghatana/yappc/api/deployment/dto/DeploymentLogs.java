/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.util.List;

/**
 * Deployment logs record.
 * 
 * @param deploymentId Deployment ID
 * @param logs List of log entries
 * 
 * @doc.type record
 * @doc.purpose Deployment logs DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record DeploymentLogs(
        String deploymentId,
        List<String> logs
) {}
