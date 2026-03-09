/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

/**
 * RollbackRequest.
 *
 * @doc.type record
 * @doc.purpose rollback request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RollbackRequest(
        String deploymentId,
        String targetVersion
) {}
