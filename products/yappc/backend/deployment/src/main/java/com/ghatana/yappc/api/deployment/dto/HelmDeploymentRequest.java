/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.util.Map;

/**
 * HelmDeploymentRequest.
 *
 * @doc.type record
 * @doc.purpose helm deployment request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record HelmDeploymentRequest(
        String chartName,
        String releaseName,
        String namespace,
        String chartVersion,
        Map<String, String> values,
        String repository
) {}
