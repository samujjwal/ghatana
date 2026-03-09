/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

/**
 * CanaryAbortRequest.
 *
 * @doc.type record
 * @doc.purpose canary abort request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryAbortRequest(
        String canaryId,
        String reason
) {}
