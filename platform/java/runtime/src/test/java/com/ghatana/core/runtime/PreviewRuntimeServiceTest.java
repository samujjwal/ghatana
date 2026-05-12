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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PreviewRuntimeService added in Phase 3.6.
 *
 * @doc.type class
 * @doc.purpose Test preview runtime health service functionality
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PreviewRuntimeService Tests")
class PreviewRuntimeServiceTest {

    private DefaultPreviewRuntimeService previewRuntimeService;

    @BeforeEach
    void setUp() {
        previewRuntimeService = new DefaultPreviewRuntimeService();
    }

    @Test
    @DisplayName("getHealth should return default healthy status for unknown preview")
    void getHealth_shouldReturnDefaultHealthyStatusForUnknownPreview() {
        // Given
        String previewId = "unknown-preview";

        // When
        PreviewRuntimeService.PreviewHealthStatus status = previewRuntimeService.getHealth(previewId);

        // Then
        assertThat(status.healthy()).isTrue();
        assertThat(status.status()).isEqualTo("healthy");
        assertThat(status.issues()).isEmpty();
        assertThat(status.previewId()).isEqualTo(previewId);
    }

    @Test
    @DisplayName("getHealth should return unhealthy status for null previewId")
    void getHealth_shouldReturnUnhealthyStatusForNullPreviewId() {
        // When
        PreviewRuntimeService.PreviewHealthStatus status = previewRuntimeService.getHealth(null);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("invalid");
        assertThat(status.issues()).containsExactly("previewId is null");
    }

    @Test
    @DisplayName("getHealth should return updated health status")
    void getHealth_shouldReturnUpdatedHealthStatus() {
        // Given
        String previewId = "preview-123";
        previewRuntimeService.updatePreviewHealth(previewId, false, "degraded", List.of("Resource limit exceeded"));

        // When
        PreviewRuntimeService.PreviewHealthStatus status = previewRuntimeService.getHealth(previewId);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("degraded");
        assertThat(status.issues()).containsExactly("Resource limit exceeded");
        assertThat(status.previewId()).isEqualTo(previewId);
    }

    @Test
    @DisplayName("getGenerationHealth should return default healthy status for unknown generation")
    void getGenerationHealth_shouldReturnDefaultHealthyStatusForUnknownGeneration() {
        // Given
        String generationId = "unknown-generation";

        // When
        PreviewRuntimeService.GenerationHealthStatus status = previewRuntimeService.getGenerationHealth(generationId);

        // Then
        assertThat(status.healthy()).isTrue();
        assertThat(status.status()).isEqualTo("healthy");
        assertThat(status.issues()).isEmpty();
        assertThat(status.generationId()).isEqualTo(generationId);
    }

    @Test
    @DisplayName("getGenerationHealth should return unhealthy status for null generationId")
    void getGenerationHealth_shouldReturnUnhealthyStatusForNullGenerationId() {
        // When
        PreviewRuntimeService.GenerationHealthStatus status = previewRuntimeService.getGenerationHealth(null);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("invalid");
        assertThat(status.issues()).containsExactly("generationId is null");
    }

    @Test
    @DisplayName("getGenerationHealth should return updated health status")
    void getGenerationHealth_shouldReturnUpdatedHealthStatus() {
        // Given
        String generationId = "generation-123";
        String previewId = "preview-123";
        previewRuntimeService.updateGenerationHealth(generationId, false, "failed", List.of("Build error"), previewId);

        // When
        PreviewRuntimeService.GenerationHealthStatus status = previewRuntimeService.getGenerationHealth(generationId);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("failed");
        assertThat(status.issues()).containsExactly("Build error");
        assertThat(status.generationId()).isEqualTo(generationId);
        assertThat(status.previewId()).isEqualTo(previewId);
    }

    @Test
    @DisplayName("getRuntimeHealth should return default healthy status for unknown runtime")
    void getRuntimeHealth_shouldReturnDefaultHealthyStatusForUnknownRuntime() {
        // Given
        String runtimeId = "unknown-runtime";

        // When
        PreviewRuntimeService.RuntimeHealthStatus status = previewRuntimeService.getRuntimeHealth(runtimeId);

        // Then
        assertThat(status.healthy()).isTrue();
        assertThat(status.status()).isEqualTo("healthy");
        assertThat(status.issues()).isEmpty();
        assertThat(status.runtimeId()).isEqualTo(runtimeId);
    }

    @Test
    @DisplayName("getRuntimeHealth should return unhealthy status for null runtimeId")
    void getRuntimeHealth_shouldReturnUnhealthyStatusForNullRuntimeId() {
        // When
        PreviewRuntimeService.RuntimeHealthStatus status = previewRuntimeService.getRuntimeHealth(null);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("invalid");
        assertThat(status.issues()).containsExactly("runtimeId is null");
    }

    @Test
    @DisplayName("getRuntimeHealth should return updated health status")
    void getRuntimeHealth_shouldReturnUpdatedHealthStatus() {
        // Given
        String runtimeId = "runtime-123";
        String generationId = "generation-123";
        previewRuntimeService.updateRuntimeHealth(runtimeId, false, "crashed", List.of("Container exited"), generationId);

        // When
        PreviewRuntimeService.RuntimeHealthStatus status = previewRuntimeService.getRuntimeHealth(runtimeId);

        // Then
        assertThat(status.healthy()).isFalse();
        assertThat(status.status()).isEqualTo("crashed");
        assertThat(status.issues()).containsExactly("Container exited");
        assertThat(status.runtimeId()).isEqualTo(runtimeId);
        assertThat(status.generationId()).isEqualTo(generationId);
    }

    @Test
    @DisplayName("clear should remove all health status")
    void clear_shouldRemoveAllHealthStatus() {
        // Given
        String previewId = "preview-123";
        String generationId = "generation-123";
        String runtimeId = "runtime-123";
        previewRuntimeService.updatePreviewHealth(previewId, false, "degraded", List.of("Issue"));
        previewRuntimeService.updateGenerationHealth(generationId, false, "failed", List.of("Issue"), previewId);
        previewRuntimeService.updateRuntimeHealth(runtimeId, false, "crashed", List.of("Issue"), generationId);

        // When
        previewRuntimeService.clear();

        // Then
        assertThat(previewRuntimeService.getHealth(previewId).healthy()).isTrue();
        assertThat(previewRuntimeService.getGenerationHealth(generationId).healthy()).isTrue();
        assertThat(previewRuntimeService.getRuntimeHealth(runtimeId).healthy()).isTrue();
    }

    @Test
    @DisplayName("clearPreview should remove specific preview health status")
    void clearPreview_shouldRemoveSpecificPreviewHealthStatus() {
        // Given
        String previewId = "preview-123";
        previewRuntimeService.updatePreviewHealth(previewId, false, "degraded", List.of("Issue"));

        // When
        previewRuntimeService.clearPreview(previewId);

        // Then
        assertThat(previewRuntimeService.getHealth(previewId).healthy()).isTrue();
    }

    @Test
    @DisplayName("clearGeneration should remove specific generation health status")
    void clearGeneration_shouldRemoveSpecificGenerationHealthStatus() {
        // Given
        String generationId = "generation-123";
        previewRuntimeService.updateGenerationHealth(generationId, false, "failed", List.of("Issue"), "preview-123");

        // When
        previewRuntimeService.clearGeneration(generationId);

        // Then
        assertThat(previewRuntimeService.getGenerationHealth(generationId).healthy()).isTrue();
    }

    @Test
    @DisplayName("clearRuntime should remove specific runtime health status")
    void clearRuntime_shouldRemoveSpecificRuntimeHealthStatus() {
        // Given
        String runtimeId = "runtime-123";
        previewRuntimeService.updateRuntimeHealth(runtimeId, false, "crashed", List.of("Issue"), "generation-123");

        // When
        previewRuntimeService.clearRuntime(runtimeId);

        // Then
        assertThat(previewRuntimeService.getRuntimeHealth(runtimeId).healthy()).isTrue();
    }

    @Test
    @DisplayName("health status records should have lastChecked timestamp")
    void healthStatusRecords_shouldHaveLastCheckedTimestamp() {
        // Given
        String previewId = "preview-123";
        previewRuntimeService.updatePreviewHealth(previewId, true, "healthy", List.of());

        // When
        PreviewRuntimeService.PreviewHealthStatus status = previewRuntimeService.getHealth(previewId);

        // Then
        assertThat(status.lastChecked()).isNotNull();
        assertThat(status.lastChecked()).isBefore(java.time.Instant.now().plusSeconds(1));
    }
}
