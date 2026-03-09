/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for user permissions.
 *
 * @doc.type record
 * @doc.purpose User permissions response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record UserPermissionsResponse(
    @JsonProperty("userId") String userId,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("role") String role,
    @JsonProperty("persona") String persona,
    @JsonProperty("permissions") List<String> permissions,
    @JsonProperty("resourcePermissions") Map<String, List<String>> resourcePermissions) {}
