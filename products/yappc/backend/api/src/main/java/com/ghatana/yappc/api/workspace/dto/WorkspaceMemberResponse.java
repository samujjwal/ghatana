/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response DTO for a workspace member.
 *
 * @doc.type record
 * @doc.purpose Workspace member response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record WorkspaceMemberResponse(
    @JsonProperty("userId") String userId,
    @JsonProperty("email") String email,
    @JsonProperty("name") String name,
    @JsonProperty("role") String role,
    @JsonProperty("persona") String persona,
    @JsonProperty("joinedAt") Instant joinedAt,
    @JsonProperty("lastActiveAt") Instant lastActiveAt,
    @JsonProperty("status") String status) {}
