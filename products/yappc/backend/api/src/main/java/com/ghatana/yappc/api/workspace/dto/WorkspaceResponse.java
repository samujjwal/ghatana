/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for a workspace.
 *
 * @doc.type record
 * @doc.purpose Workspace response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record WorkspaceResponse(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("tenantId") String tenantId,
    @JsonProperty("ownerId") String ownerId,
    @JsonProperty("status") String status,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("projectCount") int projectCount,
    @JsonProperty("memberCount") int memberCount,
    @JsonProperty("stats") WorkspaceStats stats,
    @JsonProperty("metadata") Map<String, String> metadata) {
  /** Workspace statistics summary. */
  public record WorkspaceStats(
      @JsonProperty("requirementsCount") int requirementsCount,
      @JsonProperty("pendingSuggestionsCount") int pendingSuggestionsCount,
      @JsonProperty("activeIssuesCount") int activeIssuesCount) {}
}
