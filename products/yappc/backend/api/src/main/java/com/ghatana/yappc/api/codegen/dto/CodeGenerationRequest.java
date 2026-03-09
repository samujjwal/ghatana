package com.ghatana.yappc.api.codegen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request to generate code from specification.
 * 
 * @doc.type record
 * @doc.purpose Code generation configuration
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CodeGenerationRequest(
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("sourceContent") String sourceContent,
        @JsonProperty("outputPath") String outputPath,
        @JsonProperty("packageName") String packageName,
        @JsonProperty("configuration") Map<String, Object> configuration) {
    
    /**
     * Validates code generation request.
     * 
     * @return true if all required fields are present
     */
    public boolean isValid() {
        return sourceType != null && !sourceType.isBlank() &&
               sourceContent != null && !sourceContent.isBlank() &&
               outputPath != null && !outputPath.isBlank() &&
               packageName != null && !packageName.isBlank();
    }
}
