package com.ghatana.orchestrator.loader;

import java.util.List;

import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;

import io.activej.promise.Promise;

/**
 * Interface for loading pipeline specifications from various formats.
 * 
 * Day 24 Implementation: Specification format loading support
 */
public interface SpecFormatLoader {

    /**
     * Load pipeline from YAML specification.
     */
    Promise<OrchestratorPipelineEntity> loadFromYaml(String yamlContent);

    /**
     * Load pipeline from JSON specification.
     */
    Promise<OrchestratorPipelineEntity> loadFromJson(String jsonContent);

    /**
     * Load multiple pipelines from a directory or archive.
     */
    Promise<List<OrchestratorPipelineEntity>> loadFromDirectory(String directoryPath);

    /**
     * Validate specification format.
     */
    Promise<Boolean> validateFormat(String content, String format);

    /**
     * Get supported formats.
     */
    List<String> getSupportedFormats();
}