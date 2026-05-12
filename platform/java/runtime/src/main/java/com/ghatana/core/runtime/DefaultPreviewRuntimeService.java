/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-05-11
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of PreviewRuntimeService.
 * Provides health status queries for preview, generation, and runtime resources.
 *
 * <p>This implementation maintains an in-memory registry of health status.
 * In production, this should be replaced with a implementation that queries
 * the actual preview runtime infrastructure (e.g., Kubernetes, container orchestrator).</p>
 *
 * @doc.type class
 * @doc.purpose Default in-memory implementation of preview runtime health service
 * @doc.layer platform
 * @doc.pattern Service
 */
public class DefaultPreviewRuntimeService implements PreviewRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPreviewRuntimeService.class);

    // In-memory health status registry
    private final Map<String, PreviewHealthStatus> previewHealthRegistry = new ConcurrentHashMap<>();
    private final Map<String, GenerationHealthStatus> generationHealthRegistry = new ConcurrentHashMap<>();
    private final Map<String, RuntimeHealthStatus> runtimeHealthRegistry = new ConcurrentHashMap<>();

    public DefaultPreviewRuntimeService() {
        // Initialize with default healthy states
        log.info("DefaultPreviewRuntimeService initialized with in-memory health registry");
    }

    @Override
    public PreviewHealthStatus getHealth(String previewId) {
        if (previewId == null) {
            log.warn("previewId is null, returning unhealthy status");
            return new PreviewHealthStatus(false, "invalid", List.of("previewId is null"));
        }

        PreviewHealthStatus status = previewHealthRegistry.get(previewId);
        if (status == null) {
            log.debug("No health status found for previewId={}, returning default healthy status", previewId);
            return new PreviewHealthStatus(true, "healthy", List.of(), java.time.Instant.now(), previewId);
        }

        log.debug("Retrieved health status for previewId={}: healthy={}, status={}", 
            previewId, status.healthy(), status.status());
        return status;
    }

    @Override
    public GenerationHealthStatus getGenerationHealth(String generationId) {
        if (generationId == null) {
            log.warn("generationId is null, returning unhealthy status");
            return new GenerationHealthStatus(false, "invalid", generationId, List.of("generationId is null"));
        }

        GenerationHealthStatus status = generationHealthRegistry.get(generationId);
        if (status == null) {
            log.debug("No health status found for generationId={}, returning default healthy status", generationId);
            return new GenerationHealthStatus(true, "healthy", generationId, List.of(), java.time.Instant.now(), null);
        }

        log.debug("Retrieved health status for generationId={}: healthy={}, status={}", 
            generationId, status.healthy(), status.status());
        return status;
    }

    @Override
    public RuntimeHealthStatus getRuntimeHealth(String runtimeId) {
        if (runtimeId == null) {
            log.warn("runtimeId is null, returning unhealthy status");
            return new RuntimeHealthStatus(false, "invalid", runtimeId, List.of("runtimeId is null"));
        }

        RuntimeHealthStatus status = runtimeHealthRegistry.get(runtimeId);
        if (status == null) {
            log.debug("No health status found for runtimeId={}, returning default healthy status", runtimeId);
            return new RuntimeHealthStatus(true, "healthy", runtimeId, List.of(), java.time.Instant.now(), null);
        }

        log.debug("Retrieved health status for runtimeId={}: healthy={}, status={}", 
            runtimeId, status.healthy(), status.status());
        return status;
    }

    /**
     * Update the health status for a preview.
     * This method can be called by external systems to update health status.
     *
     * @param previewId The preview identifier
     * @param healthy Whether the preview is healthy
     * @param status The status string
     * @param issues List of issues if any
     */
    public void updatePreviewHealth(String previewId, boolean healthy, String status, List<String> issues) {
        PreviewHealthStatus healthStatus = new PreviewHealthStatus(healthy, status, issues, java.time.Instant.now(), previewId);
        previewHealthRegistry.put(previewId, healthStatus);
        log.info("Updated preview health: previewId={}, healthy={}, status={}, issues={}", 
            previewId, healthy, status, issues.size());
    }

    /**
     * Update the health status for a generation.
     *
     * @param generationId The generation identifier
     * @param healthy Whether the generation is healthy
     * @param status The status string
     * @param issues List of issues if any
     * @param previewId The associated preview ID
     */
    public void updateGenerationHealth(String generationId, boolean healthy, String status, List<String> issues, String previewId) {
        GenerationHealthStatus healthStatus = new GenerationHealthStatus(healthy, status, generationId, issues, java.time.Instant.now(), previewId);
        generationHealthRegistry.put(generationId, healthStatus);
        log.info("Updated generation health: generationId={}, healthy={}, status={}, issues={}, previewId={}", 
            generationId, healthy, status, issues.size(), previewId);
    }

    /**
     * Update the health status for a runtime.
     *
     * @param runtimeId The runtime identifier
     * @param healthy Whether the runtime is healthy
     * @param status The status string
     * @param issues List of issues if any
     * @param generationId The associated generation ID
     */
    public void updateRuntimeHealth(String runtimeId, boolean healthy, String status, List<String> issues, String generationId) {
        RuntimeHealthStatus healthStatus = new RuntimeHealthStatus(healthy, status, runtimeId, issues, java.time.Instant.now(), generationId);
        runtimeHealthRegistry.put(runtimeId, healthStatus);
        log.info("Updated runtime health: runtimeId={}, healthy={}, status={}, issues={}, generationId={}", 
            runtimeId, healthy, status, issues.size(), generationId);
    }

    /**
     * Clear all health status from the registry.
     * Useful for testing or reset scenarios.
     */
    public void clear() {
        previewHealthRegistry.clear();
        generationHealthRegistry.clear();
        runtimeHealthRegistry.clear();
        log.info("Cleared all health status from registry");
    }

    /**
     * Clear health status for a specific preview.
     *
     * @param previewId The preview identifier
     */
    public void clearPreview(String previewId) {
        previewHealthRegistry.remove(previewId);
        log.debug("Cleared health status for previewId={}", previewId);
    }

    /**
     * Clear health status for a specific generation.
     *
     * @param generationId The generation identifier
     */
    public void clearGeneration(String generationId) {
        generationHealthRegistry.remove(generationId);
        log.debug("Cleared health status for generationId={}", generationId);
    }

    /**
     * Clear health status for a specific runtime.
     *
     * @param runtimeId The runtime identifier
     */
    public void clearRuntime(String runtimeId) {
        runtimeHealthRegistry.remove(runtimeId);
        log.debug("Cleared health status for runtimeId={}", runtimeId);
    }
}
