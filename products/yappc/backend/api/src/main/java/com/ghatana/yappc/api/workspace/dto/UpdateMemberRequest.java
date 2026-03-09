/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating a workspace member's role/persona.
 *
 * @doc.type record
 * @doc.purpose Update member request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record UpdateMemberRequest(
    @JsonProperty("role") String role, @JsonProperty("persona") String persona) {}
