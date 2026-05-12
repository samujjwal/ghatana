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

import java.time.Instant;
import java.util.List;

/**
 * Service for querying preview, generation, and runtime health status.
 *
 * @doc.type interface
 * @doc.purpose Service for querying preview runtime health status
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface PreviewRuntimeService {

    /**
     * Get health status for a specific preview.
     *
     * @param previewId The preview identifier
     * @return Health status of the preview
     */
    PreviewHealthStatus getHealth(String previewId);

    /**
     * Get health status for a specific generation.
     *
     * @param generationId The generation identifier
     * @return Health status of the generation
     */
    GenerationHealthStatus getGenerationHealth(String generationId);

    /**
     * Get health status for a specific runtime.
     *
     * @param runtimeId The runtime identifier
     * @return Health status of the runtime
     */
    RuntimeHealthStatus getRuntimeHealth(String runtimeId);

    /**
     * Health status for a preview.
     */
    record PreviewHealthStatus(
        boolean healthy,
        String status,
        List<String> issues,
        Instant lastChecked,
        String previewId
    ) {
        public PreviewHealthStatus(boolean healthy, String status, List<String> issues) {
            this(healthy, status, issues, Instant.now(), null);
        }
    }

    /**
     * Health status for a generation.
     */
    record GenerationHealthStatus(
        boolean healthy,
        String status,
        String generationId,
        List<String> issues,
        Instant lastChecked,
        String previewId
    ) {
        public GenerationHealthStatus(boolean healthy, String status, String generationId, List<String> issues) {
            this(healthy, status, generationId, issues, Instant.now(), null);
        }
    }

    /**
     * Health status for a runtime.
     */
    record RuntimeHealthStatus(
        boolean healthy,
        String status,
        String runtimeId,
        List<String> issues,
        Instant lastChecked,
        String generationId
    ) {
        public RuntimeHealthStatus(boolean healthy, String status, String runtimeId, List<String> issues) {
            this(healthy, status, runtimeId, issues, Instant.now(), null);
        }
    }
}
