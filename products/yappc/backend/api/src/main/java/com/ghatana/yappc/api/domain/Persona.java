/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Persona configuration representing different user roles in the DevSecOps lifecycle.
 *
 * @doc.type record
 * @doc.purpose Domain model for persona configuration
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public record Persona(
    String id,
    String label,
    String description,
    String category,
    String icon,
    String color,
    @JsonProperty("focusAreas") List<String> focusAreas,
    List<String> permissions) {}
