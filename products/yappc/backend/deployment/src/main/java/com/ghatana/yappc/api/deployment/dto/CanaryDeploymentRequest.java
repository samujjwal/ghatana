/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.util.List;

/**
 * CanaryDeploymentRequest.
 *
 * @doc.type record
 * @doc.purpose canary deployment request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryDeploymentRequest(
        String applicationName,
        String environment,
        String version,
        String imageRegistry,
        List<Integer> stages,
        Integer monitorDurationSeconds,
        MetricThresholds thresholds,
        boolean autoPromote,
        Integer replicas
) {}
