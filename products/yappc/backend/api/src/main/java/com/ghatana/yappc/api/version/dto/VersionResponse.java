/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for a version.
 *
 * @doc.type record
 * @doc.purpose Version response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record VersionResponse(
    @JsonProperty("id") String id,
    @JsonProperty("entityId") String entityId,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("versionNumber") int versionNumber,
    @JsonProperty("authorId") String authorId,
    @JsonProperty("authorName") String authorName,
    @JsonProperty("reason") String reason,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("status") String status,
    @JsonProperty("approvedBy") String approvedBy,
    @JsonProperty("approvedAt") Instant approvedAt,
    @JsonProperty("snapshot") Map<String, Object> snapshot,
    @JsonProperty("changes") List<ChangeDetail> changes,
    @JsonProperty("metadata") Map<String, String> metadata) {
  public record ChangeDetail(
      @JsonProperty("field") String field,
      @JsonProperty("oldValue") Object oldValue,
      @JsonProperty("newValue") Object newValue,
      @JsonProperty("changeType") String changeType) {}
}
