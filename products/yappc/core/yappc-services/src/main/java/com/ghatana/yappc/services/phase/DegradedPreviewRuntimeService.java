package com.ghatana.yappc.services.phase;

import com.ghatana.core.runtime.PreviewRuntimeService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Degraded preview runtime adapter used when runtime health integration is unavailable.
 *
 * @doc.type class
 * @doc.purpose Explicit degraded PreviewRuntimeService adapter for phase packet wiring
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DegradedPreviewRuntimeService implements PreviewRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(DegradedPreviewRuntimeService.class);

    private final String degradedReason;

    public DegradedPreviewRuntimeService(@NotNull String degradedReason) {
        this.degradedReason = degradedReason;
        log.warn("Using DegradedPreviewRuntimeService: {}", degradedReason);
    }

    @Override
    public PreviewHealthStatus getHealth(String previewId) {
        log.warn("Preview health requested in degraded mode: previewId={}, reason={}", previewId, degradedReason);
        return new PreviewHealthStatus(false, "degraded", List.of(degradedReason), Instant.now(), previewId);
    }

    @Override
    public GenerationHealthStatus getGenerationHealth(String generationId) {
        log.warn("Generation health requested in degraded mode: generationId={}, reason={}", generationId, degradedReason);
        return new GenerationHealthStatus(false, "degraded", generationId, List.of(degradedReason), Instant.now(), null);
    }

    @Override
    public RuntimeHealthStatus getRuntimeHealth(String runtimeId) {
        log.warn("Runtime health requested in degraded mode: runtimeId={}, reason={}", runtimeId, degradedReason);
        return new RuntimeHealthStatus(false, "degraded", runtimeId, List.of(degradedReason), Instant.now(), null);
    }
}
