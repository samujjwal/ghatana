/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a requirement.
 *
 * @doc.type record
 * @doc.purpose Requirement creation request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CreateRequirementRequest(
    @NotBlank @JsonProperty("workspaceId") String workspaceId,
    @NotBlank @JsonProperty("projectId") String projectId,
    @NotBlank @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("type") RequirementType type,
    @JsonProperty("priority") Priority priority,
    @JsonProperty("acceptanceCriteria") List<AcceptanceCriterion> acceptanceCriteria,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("parentId") String parentId,
    @JsonProperty("linkedTaskIds") List<String> linkedTaskIds,
    @JsonProperty("metadata") Map<String, String> metadata) {
  public enum RequirementType {
    FUNCTIONAL,
    NON_FUNCTIONAL,
    BUSINESS_RULE,
    CONSTRAINT,
    USER_STORY,
    EPIC,
    FEATURE
  }

  public enum Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  public record AcceptanceCriterion(
      @JsonProperty("id") String id,
      @JsonProperty("description") String description,
      @JsonProperty("testable") boolean testable) {}
}
