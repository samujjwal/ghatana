/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request DTO for updating a workspace.
 *
 * @doc.type record
 * @doc.purpose Workspace update request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record UpdateWorkspaceRequest(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("status") String status,
    @JsonProperty("metadata") Map<String, String> metadata) {}
