/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for an AI suggestion.
 *
 * @doc.type record
 * @doc.purpose AI suggestion response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record AISuggestionResponse(
    @JsonProperty("id") String id,
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("projectId") String projectId,
    @JsonProperty("suggestionType") String suggestionType,
    @JsonProperty("status") String status,
    @JsonProperty("targetEntityId") String targetEntityId,
    @JsonProperty("targetEntityType") String targetEntityType,
    @JsonProperty("content") SuggestionContent content,
    @JsonProperty("confidence") ConfidenceScore confidence,
    @JsonProperty("reasoning") String reasoning,
    @JsonProperty("generatedAt") Instant generatedAt,
    @JsonProperty("expiresAt") Instant expiresAt,
    @JsonProperty("generatedBy") String generatedBy,
    @JsonProperty("model") String model,
    @JsonProperty("metadata") Map<String, String> metadata) {
  /** Suggestion content (varies by type). */
  public record SuggestionContent(
      @JsonProperty("title") String title,
      @JsonProperty("description") String description,
      @JsonProperty("items") List<SuggestionItem> items,
      @JsonProperty("rawOutput") String rawOutput,
      @JsonProperty("structuredData") Map<String, Object> structuredData) {}

  /** Individual suggestion item. */
  public record SuggestionItem(
      @JsonProperty("id") String id,
      @JsonProperty("type") String type,
      @JsonProperty("content") String content,
      @JsonProperty("priority") String priority,
      @JsonProperty("impact") String impact,
      @JsonProperty("additionalData") Map<String, Object> additionalData) {}

  /** Confidence scoring. */
  public record ConfidenceScore(
      @JsonProperty("level") String level,
      @JsonProperty("score") double score,
      @JsonProperty("factors") List<ConfidenceFactor> factors) {
    public static ConfidenceScore high(double score) {
      return new ConfidenceScore("high", score, List.of());
    }

    public static ConfidenceScore medium(double score) {
      return new ConfidenceScore("medium", score, List.of());
    }

    public static ConfidenceScore low(double score) {
      return new ConfidenceScore("low", score, List.of());
    }
  }

  /** Factor affecting confidence. */
  public record ConfidenceFactor(
      @JsonProperty("factor") String factor,
      @JsonProperty("impact") String impact,
      @JsonProperty("value") double value) {}

  /** Status constants. */
  public static final String STATUS_PENDING = "pending";

  public static final String STATUS_ACCEPTED = "accepted";
  public static final String STATUS_REJECTED = "rejected";
  public static final String STATUS_EXPIRED = "expired";
}
