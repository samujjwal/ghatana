package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Information about a feature pack.
 * 
 * @doc.type record
 * @doc.purpose Feature pack metadata
 * @doc.layer product
 * @doc.pattern DTO
 */
public record FeaturePackInfo(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("compatibleTemplates") List<String> compatibleTemplates,
        @JsonProperty("dependencies") List<String> dependencies) {
}
