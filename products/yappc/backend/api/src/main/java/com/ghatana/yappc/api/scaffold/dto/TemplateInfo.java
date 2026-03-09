package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Information about a project template.
 * 
 * @doc.type record
 * @doc.purpose Template metadata and configuration
 * @doc.layer product
 * @doc.pattern DTO
 */
public record TemplateInfo(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("category") String category,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("version") String version,
        @JsonProperty("configSchema") Object configSchema) {
}
