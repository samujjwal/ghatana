/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Configuration reference for inline or file-based configs.
 *
 * @doc.type record
 * @doc.purpose Reference to external or inline configuration
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigReference(
        @JsonProperty("ref")
        String ref,
        @JsonProperty("inline")
        Map<String, Object> inline
        ) {

    public boolean isReference() {
        return ref != null && !ref.isBlank();
    }

    public boolean isInline() {
        return inline != null && !inline.isEmpty();
    }
}

