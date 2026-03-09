/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for accepting an AI suggestion.
 *
 * @doc.type record
 * @doc.purpose AI suggestion accept request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record AcceptSuggestionRequest(
    @NotBlank @JsonProperty("suggestionId") String suggestionId,
    @JsonProperty("modifications") String modifications,
    @JsonProperty("applyTo") String applyTo,
    @JsonProperty("feedback") String feedback,
    @JsonProperty("rating") Integer rating) {}
