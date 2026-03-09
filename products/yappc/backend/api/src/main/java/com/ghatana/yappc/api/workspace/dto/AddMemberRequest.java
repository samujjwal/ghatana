/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a member to a workspace.
 *
 * @doc.type record
 * @doc.purpose Add member request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record AddMemberRequest(
    @NotBlank @Email @JsonProperty("email") String email,
    @JsonProperty("name") String name,
    @NotBlank @JsonProperty("role") String role,
    @JsonProperty("persona") String persona) {
  /** Valid workspace roles. */
  public static final String ROLE_OWNER = "OWNER";

  public static final String ROLE_ADMIN = "ADMIN";
  public static final String ROLE_MEMBER = "MEMBER";
  public static final String ROLE_VIEWER = "VIEWER";
}
