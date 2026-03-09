/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for permission check.
 *
 * @doc.type record
 * @doc.purpose Permission check request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CheckPermissionRequest(
    @NotBlank @JsonProperty("userId") String userId,
    @NotBlank @JsonProperty("tenantId") String tenantId,
    @NotBlank @JsonProperty("permission") String permission,
    @JsonProperty("resourceType") String resourceType,
    @JsonProperty("resourceId") String resourceId) {}
