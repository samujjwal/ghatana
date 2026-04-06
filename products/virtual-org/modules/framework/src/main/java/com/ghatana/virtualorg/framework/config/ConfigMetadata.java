package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Shared metadata block for Virtual-Org config resources.
 *
 * @doc.type record
 * @doc.purpose Shared metadata value object for Virtual-Org configuration resources
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigMetadata(
        @JsonProperty("name")
        String name,
        @JsonProperty("namespace")
        String namespace,
        @JsonProperty("labels")
        Map<String, String> labels,
        @JsonProperty("annotations")
        Map<String, String> annotations
        ) {
}