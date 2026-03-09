/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for recording an audit event.
 *
 * @doc.type record
 * @doc.purpose Audit event creation request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record RecordAuditEventRequest(
    @NotBlank @JsonProperty("workspaceId") String workspaceId,
    @NotBlank @JsonProperty("category") String category,
    @NotBlank @JsonProperty("action") String action,
    @NotBlank @JsonProperty("actorId") String actorId,
    @JsonProperty("actorName") String actorName,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("entityId") String entityId,
    @JsonProperty("entityName") String entityName,
    @JsonProperty("severity") String severity,
    @JsonProperty("details") Map<String, Object> details,
    @JsonProperty("oldValue") Object oldValue,
    @JsonProperty("newValue") Object newValue,
    @JsonProperty("metadata") Map<String, String> metadata) {
  /** Creates request with required fields only. */
  public RecordAuditEventRequest(
      String workspaceId, String category, String action, String actorId) {
    this(
        workspaceId,
        category,
        action,
        actorId,
        null,
        null,
        null,
        null,
        "info",
        null,
        null,
        null,
        null);
  }
}
