/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Query parameters for fetching audit events.
 *
 * @doc.type record
 * @doc.purpose Audit event query parameters
 * @doc.layer api
 * @doc.pattern DTO
 */
public record QueryAuditEventsRequest(
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("categories") List<String> categories,
    @JsonProperty("severities") List<String> severities,
    @JsonProperty("actorId") String actorId,
    @JsonProperty("entityType") String entityType,
    @JsonProperty("entityId") String entityId,
    @JsonProperty("startTime") Instant startTime,
    @JsonProperty("endTime") Instant endTime,
    @JsonProperty("searchQuery") String searchQuery,
    @JsonProperty("page") Integer page,
    @JsonProperty("pageSize") Integer pageSize,
    @JsonProperty("sortBy") String sortBy,
    @JsonProperty("sortOrder") String sortOrder) {
  public QueryAuditEventsRequest {
    if (page == null) page = 1;
    if (pageSize == null) pageSize = 20;
    if (sortBy == null) sortBy = "timestamp";
    if (sortOrder == null) sortOrder = "desc";
  }
}
