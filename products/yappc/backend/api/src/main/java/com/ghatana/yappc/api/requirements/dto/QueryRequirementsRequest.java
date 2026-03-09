/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Query parameters for fetching requirements.
 *
 * @doc.type record
 * @doc.purpose Requirements query parameters
 * @doc.layer api
 * @doc.pattern DTO
 */
public record QueryRequirementsRequest(
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("projectId") String projectId,
    @JsonProperty("stages") List<String> stages,
    @JsonProperty("types") List<String> types,
    @JsonProperty("priorities") List<String> priorities,
    @JsonProperty("statuses") List<String> statuses,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("createdBy") String createdBy,
    @JsonProperty("assignedTo") String assignedTo,
    @JsonProperty("searchQuery") String searchQuery,
    @JsonProperty("minQualityScore") Integer minQualityScore,
    @JsonProperty("maxQualityScore") Integer maxQualityScore,
    @JsonProperty("includeChildren") Boolean includeChildren,
    @JsonProperty("page") Integer page,
    @JsonProperty("pageSize") Integer pageSize,
    @JsonProperty("sortBy") String sortBy,
    @JsonProperty("sortOrder") String sortOrder) {
  public QueryRequirementsRequest {
    if (page == null) page = 1;
    if (pageSize == null) pageSize = 20;
    if (sortBy == null) sortBy = "createdAt";
    if (sortOrder == null) sortOrder = "desc";
    if (includeChildren == null) includeChildren = false;
  }
}
