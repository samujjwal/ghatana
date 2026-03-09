/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for generating an AI suggestion.
 *
 * @doc.type record
 * @doc.purpose AI suggestion generation request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record GenerateSuggestionRequest(
    @NotBlank @JsonProperty("workspaceId") String workspaceId,
    @NotBlank @JsonProperty("projectId") String projectId,
    @NotBlank @JsonProperty("suggestionType") SuggestionType suggestionType,
    @JsonProperty("targetEntityId") String targetEntityId,
    @JsonProperty("targetEntityType") String targetEntityType,
    @JsonProperty("context") String context,
    @JsonProperty("userPrompt") String userPrompt,

    /**
     * The user's persona (e.g., "Product Manager", "Developer", "QA Engineer"). Used to tailor AI
     * suggestions to the user's role and perspective.
     */
    @JsonProperty("persona") String persona,
    @JsonProperty("options") GenerationOptions options,
    @JsonProperty("metadata") Map<String, String> metadata) {
  /** Types of AI suggestions that can be generated. */
  public enum SuggestionType {
    REQUIREMENT_GENERATION,
    ACCEPTANCE_CRITERIA,
    TEST_CASES,
    AMBIGUITY_DETECTION,
    DEPENDENCY_ANALYSIS,
    QUALITY_IMPROVEMENT,
    TECH_DEBT_ASSESSMENT,
    ARCHITECTURE_IMPACT
  }

  /** Options for AI generation. */
  public record GenerationOptions(
      @JsonProperty("temperature") Double temperature,
      @JsonProperty("maxTokens") Integer maxTokens,
      @JsonProperty("model") String model,
      @JsonProperty("includeConfidence") Boolean includeConfidence,
      @JsonProperty("includeReasoning") Boolean includeReasoning) {
    public GenerationOptions {
      if (temperature == null) temperature = 0.7;
      if (maxTokens == null) maxTokens = 2000;
      if (includeConfidence == null) includeConfidence = true;
      if (includeReasoning == null) includeReasoning = true;
    }
  }
}
