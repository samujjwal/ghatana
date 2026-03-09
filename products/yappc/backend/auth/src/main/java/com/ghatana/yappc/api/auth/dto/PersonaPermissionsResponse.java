/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for persona permissions.
 *
 * @doc.type record
 * @doc.purpose Persona permissions response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record PersonaPermissionsResponse(
    @JsonProperty("persona") String persona,
    @JsonProperty("defaultRole") String defaultRole,
    @JsonProperty("permissions") List<String> permissions,
    @JsonProperty("permissionCount") int permissionCount,
    @JsonProperty("category") String category,
    @JsonProperty("description") String description) {
  /** Persona categories. */
  public enum PersonaCategory {
    EXECUTION("Execution", "Personas focused on building and delivering"),
    GOVERNANCE("Governance", "Personas focused on oversight and compliance"),
    STRATEGIC("Strategic", "Personas focused on planning and direction"),
    OPERATIONS("Operations", "Personas focused on release and support"),
    ADMINISTRATIVE("Administrative", "Personas focused on workspace management");

    private final String name;
    private final String description;

    PersonaCategory(String name, String description) {
      this.name = name;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }
}
