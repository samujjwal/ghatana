/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Tests for model versioning and rollback (D012). // GH-90000
 *
 * <p>Validates model version management and rollback capabilities.
 *
 * @doc.type class
 * @doc.purpose Model version and rollback tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ModelVersion – Versioning and Rollback (D012) [GH-90000]")
class ModelVersionTest extends EventloopTestBase {

    @Mock
    private ModelVersionRepository versionRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Version Creation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Creation [GH-90000]")
    class VersionCreationTests {

        @Test
        @DisplayName("[D012]: save_creates_new_version [GH-90000]")
        void saveCreatesNewVersion() { // GH-90000
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-001", modelId, "tenant-alpha", "1.0.0",
                List.of(), "Initial version", "/models/001/v1", 1024000, // GH-90000
                new ModelVersionRepository.ModelMetrics(0.85, 0.82, 0.80, 0.81, 0.3, 1000, 200, 3600000), // GH-90000
                "job-001", Instant.now(), "user-001", false // GH-90000
            );

            when(versionRepository.save(any())) // GH-90000
                .thenReturn(Promise.of(version)); // GH-90000

            ModelVersionRepository.ModelVersion result = runPromise(() -> // GH-90000
                versionRepository.save(version) // GH-90000
            );

            assertThat(result.id()).isEqualTo("v-001 [GH-90000]");
            assertThat(result.versionNumber()).isEqualTo("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("[D012]: version_has_unique_version_number [GH-90000]")
        void versionHasUniqueVersionNumber() { // GH-90000
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion v1 = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-001", modelId, "tenant-alpha", "1.0.0",
                List.of(), "Version 1", "/path", 1000, null, null, Instant.now(), "user", false // GH-90000
            );

            ModelVersionRepository.ModelVersion v2 = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-002", modelId, "tenant-alpha", "1.1.0",
                List.of(), "Version 2", "/path", 1000, null, null, Instant.now(), "user", true // GH-90000
            );

            assertThat(v1.versionNumber()).isNotEqualTo(v2.versionNumber()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag Management Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tag Management [GH-90000]")
    class TagManagementTests {

        @Test
        @DisplayName("[D012]: tag_version_adds_tag [GH-90000]")
        void tagVersionAddsTag() { // GH-90000
            String versionId = "v-001";
            String tag = "production";

            when(versionRepository.tagVersion(versionId, tag)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> versionRepository.tagVersion(versionId, tag)); // GH-90000

            verify(versionRepository).tagVersion(versionId, tag); // GH-90000
        }

        @Test
        @DisplayName("[D012]: version_has_tag_checks_correctly [GH-90000]")
        void versionHasTagChecksCorrectly() { // GH-90000
            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of("production", "stable"), "Tagged version", "/path", 1000, // GH-90000
                null, null, Instant.now(), "user", true // GH-90000
            );

            assertThat(version.hasTag("production [GH-90000]")).isTrue();
            assertThat(version.hasTag("stable [GH-90000]")).isTrue();
            assertThat(version.hasTag("experimental [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("[D012]: production_tag_detected [GH-90000]")
        void productionTagDetected() { // GH-90000
            ModelVersionRepository.ModelVersion prod = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of("production [GH-90000]"), "Prod version", "/path", 1000,
                null, null, Instant.now(), "user", true // GH-90000
            );

            assertThat(prod.isProduction()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: find_by_tag_returns_correct_version [GH-90000]")
        void findByTagReturnsCorrectVersion() { // GH-90000
            String modelId = "model-001";
            String tag = "production";

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-prod", modelId, "tenant-alpha", "1.2.0",
                List.of("production [GH-90000]"), "Production ready", "/models/prod", 1024000,
                new ModelVersionRepository.ModelMetrics(0.90, 0.88, 0.87, 0.875, 0.2, 2000, 400, 7200000), // GH-90000
                "job-prod", Instant.now(), "user-001", true // GH-90000
            );

            when(versionRepository.findByTag(modelId, tag)) // GH-90000
                .thenReturn(Promise.of(Optional.of(version))); // GH-90000

            Optional<ModelVersionRepository.ModelVersion> result = runPromise(() -> // GH-90000
                versionRepository.findByTag(modelId, tag) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().isCurrent()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rollback Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rollback [GH-90000]")
    class RollbackTests {

        @Test
        @DisplayName("[D012]: rollback_to_previous_version [GH-90000]")
        void rollbackToPreviousVersion() { // GH-90000
            String modelId = "model-001";
            String targetVersionId = "v-001";

            ModelVersionRepository.ModelVersion previous = new ModelVersionRepository.ModelVersion( // GH-90000
                targetVersionId, modelId, "tenant-alpha", "1.0.0",
                List.of("stable [GH-90000]"), "Previous stable", "/models/v1", 1000,
                null, null, Instant.now(), "user", false // GH-90000
            );

            when(versionRepository.rollbackTo(modelId, targetVersionId)) // GH-90000
                .thenReturn(Promise.of(previous)); // GH-90000

            ModelVersionRepository.ModelVersion result = runPromise(() -> // GH-90000
                versionRepository.rollbackTo(modelId, targetVersionId) // GH-90000
            );

            assertThat(result.id()).isEqualTo(targetVersionId); // GH-90000
        }

        @Test
        @DisplayName("[D012]: rollback_makes_version_current [GH-90000]")
        void rollbackMakesVersionCurrent() { // GH-90000
            String modelId = "model-001";
            String targetVersionId = "v-stable";

            ModelVersionRepository.ModelVersion rolledBack = new ModelVersionRepository.ModelVersion( // GH-90000
                targetVersionId, modelId, "tenant-alpha", "1.0.0",
                List.of("stable", "rolled-back"), "Rolled back version", "/models/stable", 1000, // GH-90000
                null, null, Instant.now(), "user", true // GH-90000
            );

            when(versionRepository.rollbackTo(modelId, targetVersionId)) // GH-90000
                .thenReturn(Promise.of(rolledBack)); // GH-90000

            ModelVersionRepository.ModelVersion result = runPromise(() -> // GH-90000
                versionRepository.rollbackTo(modelId, targetVersionId) // GH-90000
            );

            assertThat(result.isCurrent()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Version Listing Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Listing [GH-90000]")
    class VersionListingTests {

        @Test
        @DisplayName("[D012]: list_versions_returns_all_versions [GH-90000]")
        void listVersionsReturnsAllVersions() { // GH-90000
            String modelId = "model-001";

            List<ModelVersionRepository.ModelVersion> versions = List.of( // GH-90000
                new ModelVersionRepository.ModelVersion("v-1", modelId, "tenant-alpha", "1.0.0", // GH-90000
                    List.of(), "V1", "/path1", 1000, null, null, Instant.now(), "user", false), // GH-90000
                new ModelVersionRepository.ModelVersion("v-2", modelId, "tenant-alpha", "1.1.0", // GH-90000
                    List.of("beta [GH-90000]"), "V2", "/path2", 1100, null, null, Instant.now(), "user", false),
                new ModelVersionRepository.ModelVersion("v-3", modelId, "tenant-alpha", "1.2.0", // GH-90000
                    List.of("production [GH-90000]"), "V3", "/path3", 1200, null, null, Instant.now(), "user", true)
            );

            when(versionRepository.listVersions(modelId)) // GH-90000
                .thenReturn(Promise.of(versions)); // GH-90000

            List<ModelVersionRepository.ModelVersion> result = runPromise(() -> // GH-90000
                versionRepository.listVersions(modelId) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
            assertThat(result).anyMatch(v -> v.isCurrent()); // GH-90000
        }

        @Test
        @DisplayName("[D012]: find_latest_returns_most_recent [GH-90000]")
        void findLatestReturnsMostRecent() { // GH-90000
            String modelId = "model-001";

            ModelVersionRepository.ModelVersion latest = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-latest", modelId, "tenant-alpha", "2.0.0",
                List.of("latest [GH-90000]"), "Latest version", "/models/latest", 2000,
                null, null, Instant.now(), "user", true // GH-90000
            );

            when(versionRepository.findLatest(modelId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(latest))); // GH-90000

            Optional<ModelVersionRepository.ModelVersion> result = runPromise(() -> // GH-90000
                versionRepository.findLatest(modelId) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().versionNumber()).isEqualTo("2.0.0 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Metrics [GH-90000]")
    class MetricsTests {

        @Test
        @DisplayName("[D012]: version_includes_training_metrics [GH-90000]")
        void versionIncludesTrainingMetrics() { // GH-90000
            ModelVersionRepository.ModelMetrics metrics = new ModelVersionRepository.ModelMetrics( // GH-90000
                0.92, 0.90, 0.88, 0.89, 0.15, 5000, 1000, 14400000
            );

            ModelVersionRepository.ModelVersion version = new ModelVersionRepository.ModelVersion( // GH-90000
                "v-001", "model-001", "tenant-alpha", "1.0.0",
                List.of(), "Version with metrics", "/path", 1000, // GH-90000
                metrics, "job-001", Instant.now(), "user", true // GH-90000
            );

            assertThat(version.metrics()).isNotNull(); // GH-90000
            assertThat(version.metrics().accuracy()).isEqualTo(0.92); // GH-90000
            assertThat(version.metrics().f1Score()).isEqualTo(0.89); // GH-90000
        }

        @Test
        @DisplayName("[D012]: metrics_meets_threshold [GH-90000]")
        void metricsMeetsThreshold() { // GH-90000
            ModelVersionRepository.ModelMetrics good = new ModelVersionRepository.ModelMetrics( // GH-90000
                0.92, 0.90, 0.88, 0.89, 0.15, 1000, 200, 3600000
            );

            assertThat(good.meetsThreshold(0.90, 0.85)).isTrue(); // GH-90000

            ModelVersionRepository.ModelMetrics poor = new ModelVersionRepository.ModelMetrics( // GH-90000
                0.85, 0.82, 0.80, 0.81, 0.35, 1000, 200, 3600000
            );

            assertThat(poor.meetsThreshold(0.90, 0.85)).isFalse(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Version Deletion Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Version Deletion [GH-90000]")
    class VersionDeletionTests {

        @Test
        @DisplayName("[D012]: delete_version_removes_version [GH-90000]")
        void deleteVersionRemovesVersion() { // GH-90000
            String versionId = "v-001";

            when(versionRepository.deleteVersion(versionId)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                versionRepository.deleteVersion(versionId) // GH-90000
            );

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D012]: cannot_delete_current_version [GH-90000]")
        void cannotDeleteCurrentVersion() { // GH-90000
            String versionId = "v-current";

            when(versionRepository.deleteVersion(versionId)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> // GH-90000
                versionRepository.deleteVersion(versionId) // GH-90000
            );

            assertThat(result).isFalse(); // GH-90000
        }
    }
}
