/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response for audit events.
 *
 * @doc.type record
 * @doc.purpose Paginated audit events response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record AuditEventsPageResponse(
    @JsonProperty("events") List<AuditEventResponse> events,
    @JsonProperty("total") long total,
    @JsonProperty("page") int page,
    @JsonProperty("pageSize") int pageSize,
    @JsonProperty("totalPages") int totalPages,
    @JsonProperty("hasMore") boolean hasMore) {
  public static AuditEventsPageResponse of(
      List<AuditEventResponse> events, long total, int page, int pageSize) {
    int totalPages = (int) Math.ceil((double) total / pageSize);
    boolean hasMore = page < totalPages;
    return new AuditEventsPageResponse(events, total, page, pageSize, totalPages, hasMore);
  }

  public static AuditEventsPageResponse empty() {
    return new AuditEventsPageResponse(List.of(), 0, 1, 20, 0, false);
  }
}
