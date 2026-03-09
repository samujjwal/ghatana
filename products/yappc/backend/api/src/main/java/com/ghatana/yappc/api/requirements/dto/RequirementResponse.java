/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for a requirement.
 *
 * @doc.type record
 * @doc.purpose Requirement response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record RequirementResponse(
    @JsonProperty("id") String id,
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("projectId") String projectId,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("type") String type,
    @JsonProperty("priority") String priority,
    @JsonProperty("status") String status,
    @JsonProperty("stage") String stage,
    @JsonProperty("version") int version,
    @JsonProperty("createdBy") String createdBy,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedBy") String updatedBy,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("acceptanceCriteria") List<AcceptanceCriterionResponse> acceptanceCriteria,
    @JsonProperty("qualityScore") QualityScore qualityScore,
    @JsonProperty("tags") List<String> tags,
    @JsonProperty("parentId") String parentId,
    @JsonProperty("childIds") List<String> childIds,
    @JsonProperty("linkedTaskIds") List<String> linkedTaskIds,
    @JsonProperty("approvals") List<ApprovalRecord> approvals,
    @JsonProperty("metadata") Map<String, String> metadata) {
  public record AcceptanceCriterionResponse(
      @JsonProperty("id") String id,
      @JsonProperty("description") String description,
      @JsonProperty("testable") boolean testable,
      @JsonProperty("verified") boolean verified,
      @JsonProperty("verifiedBy") String verifiedBy,
      @JsonProperty("verifiedAt") Instant verifiedAt) {}

  public record QualityScore(
      @JsonProperty("overall") int overall,
      @JsonProperty("clarity") int clarity,
      @JsonProperty("testability") int testability,
      @JsonProperty("completeness") int completeness,
      @JsonProperty("consistency") int consistency,
      @JsonProperty("issues") List<QualityIssue> issues) {}

  public record QualityIssue(
      @JsonProperty("type") String type,
      @JsonProperty("severity") String severity,
      @JsonProperty("message") String message,
      @JsonProperty("suggestion") String suggestion) {}

  public record ApprovalRecord(
      @JsonProperty("approverType") String approverType,
      @JsonProperty("approverId") String approverId,
      @JsonProperty("approverName") String approverName,
      @JsonProperty("status") String status,
      @JsonProperty("comment") String comment,
      @JsonProperty("timestamp") Instant timestamp) {}
}
