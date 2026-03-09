/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for requirements funnel analytics.
 *
 * @doc.type record
 * @doc.purpose Requirements funnel response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record RequirementsFunnelResponse(
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("projectId") String projectId,
    @JsonProperty("stages") List<StageMetrics> stages,
    @JsonProperty("totalRequirements") int totalRequirements,
    @JsonProperty("averageQualityScore") double averageQualityScore,
    @JsonProperty("conversionRates") Map<String, Double> conversionRates) {
  /** Metrics for a single stage. */
  public record StageMetrics(
      @JsonProperty("stage") String stage,
      @JsonProperty("label") String label,
      @JsonProperty("count") int count,
      @JsonProperty("percentage") double percentage,
      @JsonProperty("averageTimeInStage") long averageTimeInStageMs,
      @JsonProperty("blockedCount") int blockedCount,
      @JsonProperty("requirements") List<RequirementSummary> requirements) {}

  /** Summary of a requirement for funnel view. */
  public record RequirementSummary(
      @JsonProperty("id") String id,
      @JsonProperty("title") String title,
      @JsonProperty("priority") String priority,
      @JsonProperty("qualityScore") int qualityScore,
      @JsonProperty("daysInStage") int daysInStage) {}

  /** Stage constants. */
  public static final String STAGE_DRAFT = "draft";

  public static final String STAGE_REVIEW = "review";
  public static final String STAGE_APPROVED = "approved";
  public static final String STAGE_IMPLEMENTATION = "implementation";
  public static final String STAGE_TESTING = "testing";
  public static final String STAGE_DEPLOYED = "deployed";
  public static final String STAGE_DONE = "done";

  public static List<String> ALL_STAGES =
      List.of(
          STAGE_DRAFT,
          STAGE_REVIEW,
          STAGE_APPROVED,
          STAGE_IMPLEMENTATION,
          STAGE_TESTING,
          STAGE_DEPLOYED,
          STAGE_DONE);
}
