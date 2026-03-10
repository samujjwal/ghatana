/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend Auth Module
 */
package com.ghatana.yappc.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable data model for a single persona entry loaded from {@code personas.yaml}.
 *
 * <p>Each persona maps to a platform role and an ordered list of permission strings.
 * Instances are created exclusively by {@link PersonaLoader}; do not instantiate directly.
 *
 * @doc.type class
 * @doc.purpose Data model for a persona definition read from YAML
 * @doc.layer api
 * @doc.pattern ValueObject
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PersonaDefinition {

    @JsonProperty("id")
    private String id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("category")
    private String category;

    @JsonProperty("role")
    private String role;

    @JsonProperty("permissions")
    private List<String> permissions;

    /** Required by Jackson. */
    public PersonaDefinition() {}

    public String getId()            { return id; }
    public String getDisplayName()   { return displayName; }
    public String getCategory()      { return category; }
    public String getRole()          { return role; }
    public List<String> getPermissions() {
        return permissions == null ? List.of() : List.copyOf(permissions);
    }
}
