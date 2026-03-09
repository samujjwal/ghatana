/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.util.Map;

/**
 * Deployment request record.
 * 
 * @param applicationName Application name
 * @param environment Target environment (dev/staging/prod)
 * @param version Version/tag to deploy
 * @param imageRegistry Container registry URL
 * @param manifestPath Path to Kubernetes manifests
 * @param strategy Deployment strategy (rolling/blue-green/canary)
 * @param canaryPercentage Initial canary traffic percentage (if canary strategy)
 * @param replicas Number of replicas
 * @param configOverrides Configuration overrides
 * 
 * @doc.type record
 * @doc.purpose Deployment request DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record DeploymentRequest(
        String applicationName,
        String environment,
        String version,
        String imageRegistry,
        String manifestPath,
        String strategy,
        Integer canaryPercentage,
        Integer replicas,
        Map<String, String> configOverrides
) {}
