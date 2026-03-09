/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request DTO for updating workspace settings.
 *
 * @doc.type record
 * @doc.purpose Update settings request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record UpdateSettingsRequest(
    @JsonProperty("aiSuggestionsEnabled") Boolean aiSuggestionsEnabled,
    @JsonProperty("autoVersioningEnabled") Boolean autoVersioningEnabled,
    @JsonProperty("requireApprovalForChanges") Boolean requireApprovalForChanges,
    @JsonProperty("defaultReviewers") Integer defaultReviewers,
    @JsonProperty("suggestionExpirationDays") Integer suggestionExpirationDays,
    @JsonProperty("timezone") String timezone,
    @JsonProperty("language") String language,
    @JsonProperty("customSettings") Map<String, String> customSettings) {}
