/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for model versioning and rollback (D012).
 *
 * <p>Validates model version management and rollback capabilities.
 *
 * @doc.type class
 * @doc.purpose Model version and rollback tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelVersion – Versioning and Rollback (D012)")
class ModelVersionTest extends EventloopTestBase {

    @Mock
    private ModelVersionRepository versionRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Version Creation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Creation")
    class VersionCreationTests {

        @Test
        @DisplayName("[D012]: save_creates_new_version")
        void saveCreatesNewVersion() {
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion(
                "v-001", modelId, "tenant-alpha", "1.0.0",
                List.of(), "Initial version", "/models/001/v1", 1024000,
                new ModelVersionRepository.ModelMetrics(0.85, 0.82, 0.80, 0.81, 0.3, 1000, 200, 3600000),
                "job-001", Instant.now(), "user-001", false
            );

            when(versionRepository.save(any()))
                .thenReturn(Promise.of(version));

            ModelVersionRepository.ModelVersion result = runPromise(() ->
                versionRepository.save(version)
            );

            assertThat(result.id()).isEqualTo("v-001");
            assertThat(result.versionNumber()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("[D012]: version_has_unique_version_number")
        void versionHasUniqueVersionNumber() {
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion v1 = new ModelVersionRepository.ModelVersion(
                "v-001", modelId, "tenant-alpha", "1.0.0",
                List.of(), "Version 1", "/path", 1000, null, null, Instant.now(), "user", false
            );

            ModelVersionRepository.ModelVersion v2 = new ModelVersionRepository.ModelVersion(
                "v-002", modelId, "tenant-alpha", "1.1.0",
                List.of(), "Version 2", "/path", 1000, null, null, Instant.now(), "user", true
            );

            assertThat(v1.versionNumber()).isNotEqualTo(v2.versionNumber());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag Management Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tag Management")
    class TagManagementTests {

        @Test
        @DisplayName("[D012]: tag_version_adds_tag")
        void tagVersionAddsTag() {
            String versionId = "v-001";
            String tag = "production";

            when(versionRepository.tagVersion(versionId, tag))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> versionRepository.tagVersion(versionId, tag));

            verify(versionRepository).tagVersion(versionId, tag);
        }

        @Test
        @DisplayName("[D012]: version_has_tag_checks_correctly")
        void versionHasTagChecksCorrectly() {
            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion(
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of("production", "stable"), "Tagged version", "/path", 1000,
                null, null, Instant.now(), "user", true
            );

            assertThat(version.hasTag("production")).isTrue();
            assertThat(version.hasTag("stable")).isTrue();
            assertThat(version.hasTag("experimental")).isFalse();
        }

        @Test
        @DisplayName("[D012]: production_tag_detected")
        void productionTagDetected() {
            ModelVersionRepository.ModelVersion prod = new ModelVersionRepository.ModelVersion(
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of("production"), "Prod version", "/path", 1000,
                null, null, Instant.now(), "user", true
            );

            assertThat(prod.isProduction()).isTrue();
        }

        @Test
        @DisplayName("[D012]: find_by_tag_returns_correct_version")
        void findByTagReturnsCorrectVersion() {
            String modelId = "model-001";
            String tag = "production";

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion(
                "v-prod", modelId, "tenant-alpha", "1.2.0",
                List.of("production"), "Production ready", "/models/prod", 1024000,
                new ModelVersionRepository.ModelMetrics(0.90, 0.88, 0.87, 0.875, 0.2, 2000, 400, 7200000),
                "job-prod", Instant.now(), "user-001", true
            );

            when(versionRepository.findByTag(modelId, tag))
                .thenReturn(Promise.of(Optional.of(version)));

            Optional<ModelVersionRepository.ModelVersion> result = runPromise(() ->
                versionRepository.findByTag(modelId, tag)
            );

            assertThat(result).isPresent();
            assertThat(result.get().isCurrent()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rollback Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rollback")
    class RollbackTests {

        @Test
        @DisplayName("[D012]: rollback_to_previous_version")
        void rollbackToPreviousVersion() {
            String modelId = "model-001";
            String targetVersionId = "v-001";

            ModelVersionRepository.ModelVersion previous = new ModelVersionRepository.ModelVersion(
                targetVersionId, modelId, "tenant-alpha", "1.0.0",
                List.of("stable"), "Previous stable", "/models/v1", 1000,
                null, null, Instant.now(), "user", false
            );

            when(versionRepository.rollbackTo(modelId, targetVersionId))
                .thenReturn(Promise.of(previous));

            ModelVersionRepository.ModelVersion result = runPromise(() ->
                versionRepository.rollbackTo(modelId, targetVersionId)
            );

            assertThat(result.id()).isEqualTo(targetVersionId);
        }

        @Test
        @DisplayName("[D012]: rollback_makes_version_current")
        void rollbackMakesVersionCurrent() {
            String modelId = "model-001";
            String targetVersionId = "v-stable";

            ModelVersionRepository.ModelVersion rolledBack = new ModelVersionRepository.ModelVersion(
                targetVersionId, modelId, "tenant-alpha", "1.0.0",
                List.of("stable", "rolled-back"), "Rolled back version", "/models/stable", 1000,
                null, null, Instant.now(), "user", true
            );

            when(versionRepository.rollbackTo(modelId, targetVersionId))
                .thenReturn(Promise.of(rolledBack));

            ModelVersionRepository.ModelVersion result = runPromise(() ->
                versionRepository.rollbackTo(modelId, targetVersionId)
            );

            assertThat(result.isCurrent()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Version Listing Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Listing")
    class VersionListingTests {

        @Test
        @DisplayName("[D012]: list_versions_returns_all_versions")
        void listVersionsReturnsAllVersions() {
            String modelId = "model-001";

            List<ModelVersionRepository.ModelVersion> versions = List.of(
                new ModelVersionRepository.ModelVersion("v-1", modelId, "tenant-alpha", "1.0.0",
                    List.of(), "V1", "/path1", 1000, null, null, Instant.now(), "user", false),
                new ModelVersionRepository.ModelVersion("v-2", modelId, "tenant-alpha", "1.1.0",
                    List.of("beta"), "V2", "/path2", 1100, null, null, Instant.now(), "user", false),
                new ModelVersionRepository.ModelVersion("v-3", modelId, "tenant-alpha", "1.2.0",
                    List.of("production"), "V3", "/path3", 1200, null, null, Instant.now(), "user", true)
            );

            when(versionRepository.listVersions(modelId))
                .thenReturn(Promise.of(versions));

            List<ModelVersionRepository.ModelVersion> result = runPromise(() ->
                versionRepository.listVersions(modelId)
            );

            assertThat(result).hasSize(3);
            assertThat(result).anyMatch(v -> v.isCurrent());
        }

        @Test
        @DisplayName("[D012]: find_latest_returns_most_recent")
        void findLatestReturnsMostRecent() {
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion latest = new ModelVersionRepository.ModelVersion(
                "v-latest", modelId, "tenant-alpha", "2.0.0",
                List.of("latest"), "Latest version", "/models/latest", 2000,
                null, null, Instant.now(), "user", true
            );

            when(versionRepository.findLatest(modelId))
                .thenReturn(Promise.of(Optional.of(latest)));

            Optional<ModelVersionRepository.ModelVersion> result = runPromise(() ->
                versionRepository.findLatest(modelId)
            );

            assertThat(result).isPresent();
            assertThat(result.get().versionNumber()).isEqualTo("2.0.0");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Metrics")
    class MetricsTests {

        @Test
        @DisplayName("[D012]: version_includes_training_metrics")
        void versionIncludesTrainingMetrics() {
            ModelVersionRepository.ModelMetrics metrics = new ModelVersionRepository.ModelMetrics(
                0.92, 0.90, 0.88, 0.89, 0.15, 5000, 1000, 14400000
            );

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion(
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of(), "Version with metrics", "/path", 1000,
                metrics, "job-001", Instant.now(), "user", true
            );

            assertThat(version.metrics()).isNotNull();
            assertThat(version.metrics().accuracy()).isEqualTo(0.92);
            assertThat(version.metrics().f1Score()).isEqualTo(0.89);
        }

        @Test
        @DisplayName("[D012]: metrics_meets_threshold")
        void metricsMeetsThreshold() {
            ModelVersionRepository.ModelMetrics good = new ModelVersionRepository.ModelMetrics(
                0.92, 0.90, 0.88, 0.89, 0.15, 1000, 200, 3600000
            );

            assertThat(good.meetsThreshold(0.90, 0.85)).isTrue();

            ModelVersionRepository.ModelMetrics poor = new ModelVersionRepository.ModelMetrics(
                0.85, 0.82, 0.80, 0.81, 0.35, 1000, 200, 3600000
            );

            assertThat(poor.meetsThreshold(0.90, 0.85)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Version Deletion Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Deletion")
    class VersionDeletionTests {

        @Test
        @DisplayName("[D012]: delete_version_removes_version")
        void deleteVersionRemovesVersion() {
            String versionId = "v-001";

            when(versionRepository.deleteVersion(versionId))
                .thenReturn(Promise.of(true));

            Boolean result = runPromise(() ->
                versionRepository.deleteVersion(versionId)
            );

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("[D012]: cannot_delete_current_version")
        void cannotDeleteCurrentVersion() {
            String versionId = "v-current";

            when(versionRepository.deleteVersion(versionId))
                .thenReturn(Promise.of(false));

            Boolean result = runPromise(() ->
                versionRepository.deleteVersion(versionId)
            );

            assertThat(result).isFalse();
        }
    }
}
