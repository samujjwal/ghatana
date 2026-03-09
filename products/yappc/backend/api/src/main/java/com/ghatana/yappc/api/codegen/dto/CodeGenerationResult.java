package com.ghatana.yappc.api.codegen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Result of code generation operation.
 * 
 * @doc.type record
 * @doc.purpose Generated files and metadata
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CodeGenerationResult(
        @JsonProperty("generatedFiles") List<GeneratedFile> generatedFiles,
        @JsonProperty("outputPath") String outputPath,
        @JsonProperty("statistics") GenerationStatistics statistics) {
    
    /**
     * Information about a generated file.
     */
    public record GeneratedFile(
            @JsonProperty("path") String path,
            @JsonProperty("type") String type,
            @JsonProperty("linesOfCode") int linesOfCode) {
    }
    
    /**
     * Statistics about code generation.
     */
    public record GenerationStatistics(
            @JsonProperty("totalFiles") int totalFiles,
            @JsonProperty("totalLines") int totalLines,
            @JsonProperty("controllerCount") int controllerCount,
            @JsonProperty("dtoCount") int dtoCount,
            @JsonProperty("entityCount") int entityCount) {
    }
}
