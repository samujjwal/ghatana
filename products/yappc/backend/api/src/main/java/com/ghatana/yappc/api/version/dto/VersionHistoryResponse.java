/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for version history.
 *
 * @doc.type record
 * @doc.purpose Version history response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record VersionHistoryResponse(
    @JsonProperty("entityId") String entityId,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("versions") List<VersionResponse> versions,
    @JsonProperty("total") int total,
    @JsonProperty("currentVersion") int currentVersion) {
  public static VersionHistoryResponse of(
      String entityId, String entityType, List<VersionResponse> versions) {
    int currentVersion = versions.stream().mapToInt(VersionResponse::versionNumber).max().orElse(0);
    return new VersionHistoryResponse(
        entityId, entityType, versions, versions.size(), currentVersion);
  }
}
