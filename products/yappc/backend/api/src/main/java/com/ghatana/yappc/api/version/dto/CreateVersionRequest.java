/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for creating a new version.
 *
 * @doc.type record
 * @doc.purpose Version creation request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CreateVersionRequest(
    @NotBlank @JsonProperty("entityId") String entityId,
    @NotBlank @JsonProperty("entityType") String entityType,
    @NotBlank @JsonProperty("authorId") String authorId,
    @JsonProperty("authorName") String authorName,
    @JsonProperty("reason") String reason,
    @JsonProperty("snapshot") Map<String, Object> snapshot,
    @JsonProperty("changes") Map<String, ChangeDetail> changes,
    @JsonProperty("metadata") Map<String, String> metadata) {
  /** Represents a single field change. */
  public record ChangeDetail(
      @JsonProperty("field") String field,
      @JsonProperty("oldValue") Object oldValue,
      @JsonProperty("newValue") Object newValue,
      @JsonProperty("changeType") String changeType) {}
}
