/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for rejecting an AI suggestion.
 *
 * @doc.type record
 * @doc.purpose AI suggestion reject request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record RejectSuggestionRequest(
    @NotBlank @JsonProperty("suggestionId") String suggestionId,
    @NotBlank @JsonProperty("reason") String reason,
    @JsonProperty("reasonCode") RejectionReason reasonCode,
    @JsonProperty("feedback") String feedback) {
  public enum RejectionReason {
    NOT_RELEVANT,
    INCORRECT,
    ALREADY_DONE,
    NOT_NOW,
    QUALITY_ISSUE,
    OTHER
  }
}
