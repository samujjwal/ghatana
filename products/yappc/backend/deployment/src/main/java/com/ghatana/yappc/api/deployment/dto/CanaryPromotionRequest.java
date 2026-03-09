/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

/**
 * CanaryPromotionRequest.
 *
 * @doc.type record
 * @doc.purpose canary promotion request
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryPromotionRequest(String canaryId) {}
