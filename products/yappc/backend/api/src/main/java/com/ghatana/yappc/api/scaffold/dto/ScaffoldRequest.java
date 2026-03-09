package com.ghatana.yappc.api.scaffold.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request to scaffold a new project from template.
 * 
 * @doc.type record
 * @doc.purpose Project scaffolding configuration
 * @doc.layer product
 * @doc.pattern DTO
 */
public record ScaffoldRequest(
        @JsonProperty("templateId") String templateId,
        @JsonProperty("projectName") String projectName,
        @JsonProperty("outputPath") String outputPath,
        @JsonProperty("configuration") Map<String, Object> configuration,
        @JsonProperty("async") boolean async) {
    
    public boolean isValid() {
        return templateId != null && !templateId.isBlank() &&
               projectName != null && !projectName.isBlank() &&
               outputPath != null && !outputPath.isBlank();
    }
}
