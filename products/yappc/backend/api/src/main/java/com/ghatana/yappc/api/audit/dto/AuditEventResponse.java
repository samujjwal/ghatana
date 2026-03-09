/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for an audit event.
 *
 * @doc.type record
 * @doc.purpose Audit event response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record AuditEventResponse(
    @JsonProperty("id") String id,
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("category") String category,
    @JsonProperty("action") String action,
    @JsonProperty("actorId") String actorId,
    @JsonProperty("actorName") String actorName,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("entityId") String entityId,
    @JsonProperty("entityName") String entityName,
    @JsonProperty("severity") String severity,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("details") Map<String, Object> details,
    @JsonProperty("oldValue") Object oldValue,
    @JsonProperty("newValue") Object newValue,
    @JsonProperty("metadata") Map<String, String> metadata) {}
