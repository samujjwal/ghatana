/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for permission check.
 *
 * @doc.type record
 * @doc.purpose Permission check response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CheckPermissionResponse(
    @JsonProperty("hasPermission") boolean hasPermission,
    @JsonProperty("reason") String reason,
    @JsonProperty("userId") String userId,
    @JsonProperty("permission") String permission,
    @JsonProperty("grantedVia") String grantedVia) {
  public static CheckPermissionResponse granted(
      String userId, String permission, String grantedVia) {
    return new CheckPermissionResponse(true, "Permission granted", userId, permission, grantedVia);
  }

  public static CheckPermissionResponse denied(String userId, String permission, String reason) {
    return new CheckPermissionResponse(false, reason, userId, permission, null);
  }
}
