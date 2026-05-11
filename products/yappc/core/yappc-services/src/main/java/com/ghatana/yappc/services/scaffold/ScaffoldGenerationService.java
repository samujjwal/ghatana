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

    /**
     * Generates a scaffold from a pack in dry-run mode (preview only, no file writes).
     * 
     * @param packMetadata The pack metadata
     * @param variables The template variables
     * @param outputLocation The output location
     * @return ScaffoldGenerationResult containing the preview of changes without writing files
     */
    ScaffoldGenerationResult generateScaffoldDryRun(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation);

    /**
     * Generates a scaffold with validation and dependency resolution in dry-run mode.
     * 
     * @param packMetadata The pack metadata
     * @param variables The template variables
     * @param outputLocation The output location
     * @return ScaffoldGenerationResult containing the preview of changes without writing files
     */
    ScaffoldGenerationResult generateScaffoldWithValidationDryRun(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation);

    /**
     * Adds a feature to an existing scaffold project.
     * 
     * @param projectPath The project path
     * @param featureName The feature name
     * @param variables The template variables
     * @param dryRun Whether to run in dry-run mode (preview only)
     * @return ScaffoldGenerationResult containing the generated scaffold and any errors
     */
    ScaffoldGenerationResult addFeatureToProject(String projectPath, String featureName, Map<String, Object> variables, boolean dryRun);

    /**
     * Updates an existing scaffold project.
     * 
     * @param projectPath The project path
     * @param variables The updated template variables
     * @param dryRun Whether to run in dry-run mode (preview only)
     * @return ScaffoldGenerationResult containing the updated scaffold and any errors
     */
    ScaffoldGenerationResult updateScaffoldProject(String projectPath, Map<String, Object> variables, boolean dryRun);
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
    java.util.List<String> warnings,
    boolean dryRun,
    java.util.List<FileChange> changes
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
        if (changes == null) {
            changes = java.util.List.of();
        }
    }

    /**
     * Create a result for actual generation (not dry-run).
     */
    public static ScaffoldGenerationResult forGeneration(
        boolean success,
        String scaffoldId,
        String outputLocation,
        java.util.List<GeneratedFile> generatedFiles,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
        return new ScaffoldGenerationResult(success, scaffoldId, outputLocation, generatedFiles, errors, warnings, false, java.util.List.of());
    }

    /**
     * Create a result for dry-run preview.
     */
    public static ScaffoldGenerationResult forDryRun(
        boolean success,
        String scaffoldId,
        String outputLocation,
        java.util.List<GeneratedFile> generatedFiles,
        java.util.List<String> errors,
        java.util.List<String> warnings,
        java.util.List<FileChange> changes
    ) {
        return new ScaffoldGenerationResult(success, scaffoldId, outputLocation, generatedFiles, errors, warnings, true, changes);
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

/**
 * File change for dry-run preview.
 */
record FileChange(
    ChangeType type,
    String filePath,
    String oldContent,
    String newContent,
    long oldSize,
    long newSize
) {
    public enum ChangeType {
        CREATE,
        UPDATE,
        DELETE,
        RENAME
    }

    /**
     * Create a new file change.
     */
    public static FileChange create(String filePath, String newContent, long newSize) {
        return new FileChange(ChangeType.CREATE, filePath, null, newContent, 0, newSize);
    }

    /**
     * Create an update file change.
     */
    public static FileChange update(String filePath, String oldContent, String newContent, long oldSize, long newSize) {
        return new FileChange(ChangeType.UPDATE, filePath, oldContent, newContent, oldSize, newSize);
    }

    /**
     * Create a delete file change.
     */
    public static FileChange delete(String filePath, String oldContent, long oldSize) {
        return new FileChange(ChangeType.DELETE, filePath, oldContent, null, oldSize, 0);
    }
}
