package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request to add feature pack to project.
 * 
 * @doc.type record
 * @doc.purpose Feature pack addition configuration
 * @doc.layer product
 * @doc.pattern DTO
 */
public record FeaturePackRequest(
        @JsonProperty("projectId") String projectId,
        @JsonProperty("featurePackId") String featurePackId,
        @JsonProperty("configuration") Map<String, Object> configuration) {
    
    public boolean isValid() {
        return projectId != null && !projectId.isBlank() 
                && featurePackId != null && !featurePackId.isBlank();
    }
}
