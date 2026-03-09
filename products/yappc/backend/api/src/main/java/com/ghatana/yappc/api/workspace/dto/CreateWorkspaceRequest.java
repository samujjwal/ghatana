/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for creating a workspace.
 *
 * @doc.type record
 * @doc.purpose Workspace creation request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CreateWorkspaceRequest(
    @NotBlank @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("metadata") Map<String, String> metadata) {}
