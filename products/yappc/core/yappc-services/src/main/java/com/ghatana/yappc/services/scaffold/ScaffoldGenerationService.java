/**
 * Scaffold Generation Service
 * 
 * Generates scaffolds from packs.
 * Orchestrates the scaffold generation process including validation, dependency resolution, and template rendering.
 * 
 * @doc.type interface
 * @doc.purpose Scaffold generation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;

import java.util.Map;

/**
 * Service interface for generating scaffolds from packs.
 */
public interface ScaffoldGenerationService {

    /**
     * Generates a scaffold from a pack.
     * 
     * @param packMetadata The pack metadata
     * @param variables The template variables
     * @param outputLocation The output location
     * @return ScaffoldGenerationResult containing the generated scaffold and any errors
     */
    ScaffoldGenerationResult generateScaffold(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation);

    /**
     * Generates a scaffold with validation and dependency resolution.
     * 
     * @param packMetadata The pack metadata
     * @param variables The template variables
     * @param outputLocation The output location
     * @return ScaffoldGenerationResult containing the generated scaffold and any errors
     */
    ScaffoldGenerationResult generateScaffoldWithValidation(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation);
}

/**
 * Scaffold generation result.
 */
record ScaffoldGenerationResult(
    boolean success,
    String scaffoldId,
    String outputLocation,
    java.util.List<GeneratedFile> generatedFiles,
    java.util.List<String> errors,
    java.util.List<String> warnings
) {
    public ScaffoldGenerationResult {
        if (generatedFiles == null) {
            generatedFiles = java.util.List.of();
        }
        if (errors == null) {
            errors = java.util.List.of();
        }
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}

/**
 * Generated file.
 */
record GeneratedFile(
    String filePath,
    String content,
    long size,
    String checksum
) {}
