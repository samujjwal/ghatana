/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response DTO for workspace settings.
 *
 * @doc.type record
 * @doc.purpose Workspace settings response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record WorkspaceSettingsResponse(
    @JsonProperty("workspaceId") String workspaceId,
    @JsonProperty("aiSuggestionsEnabled") boolean aiSuggestionsEnabled,
    @JsonProperty("autoVersioningEnabled") boolean autoVersioningEnabled,
    @JsonProperty("requireApprovalForChanges") boolean requireApprovalForChanges,
    @JsonProperty("defaultReviewers") int defaultReviewers,
    @JsonProperty("suggestionExpirationDays") int suggestionExpirationDays,
    @JsonProperty("timezone") String timezone,
    @JsonProperty("language") String language,
    @JsonProperty("customSettings") Map<String, String> customSettings) {}
