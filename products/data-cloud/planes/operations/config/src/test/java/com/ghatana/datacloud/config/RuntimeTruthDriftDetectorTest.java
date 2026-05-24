/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Phase 3: Contract tests for RuntimeTruthDriftDetector.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Stale state detection</li>
 *   <li>Unexpected status change detection</li>
 *   <li>Missing plane detection</li>
 * </ul>
 */
@DisplayName("Runtime Truth Drift Detector Tests (Phase 3)")
class RuntimeTruthDriftDetectorTest {

    private final RuntimeTruthService runtimeTruthService = new RuntimeTruthService();
    private final RuntimeTruthDriftDetector driftDetector = new RuntimeTruthDriftDetector(runtimeTruthService);

    // =========================================================================
    //  Stale State Detection
    // =========================================================================

    @Nested
    @DisplayName("Stale State Detection")
    class StaleStateTests {

        @Test
        @DisplayName("detects stale plane states")
        void detectsStalePlaneStates() {
            // Set a plane state with old timestamp
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            
            // Manually set lastUpdated to be old (this is a test workaround)
            // In production, the state would naturally age
            
            // Use custom config with very short threshold
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofSeconds(1), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);
            
            // Wait for threshold to pass
            try {
                Thread.sleep(1100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = detector.detectDrift();
            
            assertThat(issues).isNotEmpty();
            assertThat(issues.get(0).driftType()).isEqualTo(RuntimeTruthDriftDetector.DriftType.STALE_STATE);
        }

        @Test
        @DisplayName("does not detect drift for fresh states")
        void doesNotDetectDriftForFreshStates() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            
            // Disable missing plane detection for this test
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofMinutes(5), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);

            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = detector.detectDrift();

            assertThat(issues).isEmpty();
        }
    }

    // =========================================================================
    //  Unexpected Status Detection
    // =========================================================================

    @Nested
    @DisplayName("Unexpected Status Detection")
    class StatusTests {

        @Test
        @DisplayName("detects DOWN plane without error metadata")
        void detectsDownPlaneWithoutErrorMetadata() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            
            // Disable missing plane detection for this test
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofMinutes(5), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);

            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = detector.detectDrift();

            assertThat(issues).hasSize(1);
            assertThat(issues.get(0).driftType()).isEqualTo(RuntimeTruthDriftDetector.DriftType.UNEXPECTED_STATUS_CHANGE);
            assertThat(issues.get(0).severity()).isEqualTo(RuntimeTruthDriftDetector.Severity.HIGH);
        }

        @Test
        @DisplayName("does not detect drift for DOWN plane with error metadata")
        void doesNotDetectDriftForDownPlaneWithErrorMetadata() {
            Map<String, Object> metadata = Map.of("error", "Connection timeout");
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, metadata);
            
            // Disable missing plane detection for this test
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofMinutes(5), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);

            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = detector.detectDrift();

            assertThat(issues).isEmpty();
        }
    }

    // =========================================================================
    //  Missing Plane Detection
    // =========================================================================

    @Nested
    @DisplayName("Missing Plane Detection")
    class MissingPlaneTests {

        @Test
        @DisplayName("detects missing expected planes")
        void detectsMissingExpectedPlanes() {
            // No planes registered
            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = driftDetector.detectDrift();

            assertThat(issues).isNotEmpty();
            
            long missingCount = issues.stream()
                .filter(i -> i.driftType() == RuntimeTruthDriftDetector.DriftType.MISSING_PLANE)
                .count();
            
            assertThat(missingCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("does not detect missing planes when all present")
        void doesNotDetectMissingPlanesWhenAllPresent() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("operations-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = driftDetector.detectDrift();

            long missingCount = issues.stream()
                .filter(i -> i.driftType() == RuntimeTruthDriftDetector.DriftType.MISSING_PLANE)
                .count();
            
            assertThat(missingCount).isEqualTo(0);
        }

        @Test
        @DisplayName("can disable missing plane detection")
        void canDisableMissingPlaneDetection() {
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofMinutes(5), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);

            java.util.List<RuntimeTruthDriftDetector.DriftIssue> issues = detector.detectDrift();

            long missingCount = issues.stream()
                .filter(i -> i.driftType() == RuntimeTruthDriftDetector.DriftType.MISSING_PLANE)
                .count();
            
            assertThat(missingCount).isEqualTo(0);
        }
    }

    // =========================================================================
    //  Drift Detection API
    // =========================================================================

    @Nested
    @DisplayName("Drift Detection API")
    class ApiTests {

        @Test
        @DisplayName("hasDrift returns true when drift detected")
        void hasDriftReturnsTrueWhenDriftDetected() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            assertThat(driftDetector.hasDrift()).isTrue();
        }

        @Test
        @DisplayName("hasDrift returns false when no drift")
        void hasDriftReturnsFalseWhenNoDrift() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            
            // Disable missing plane detection for this test
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                new RuntimeTruthDriftDetector.DriftDetectionConfig(Duration.ofMinutes(5), false, false, false, "test");
            RuntimeTruthDriftDetector detector = new RuntimeTruthDriftDetector(runtimeTruthService, config);

            assertThat(detector.hasDrift()).isFalse();
        }

        @Test
        @DisplayName("getConfig returns configuration")
        void getConfigReturnsConfiguration() {
            RuntimeTruthDriftDetector.DriftDetectionConfig config = driftDetector.getConfig();

            assertThat(config).isNotNull();
            assertThat(config.staleThreshold()).isNotNull();
        }
    }

    // =========================================================================
    //  Configuration
    // =========================================================================

    @Nested
    @DisplayName("Configuration")
    class ConfigTests {

        @Test
        @DisplayName("default configuration has reasonable thresholds")
        void defaultConfigurationHasReasonableThresholds() {
            RuntimeTruthDriftDetector.DriftDetectionConfig config = 
                RuntimeTruthDriftDetector.DriftDetectionConfig.defaults();

            assertThat(config.staleThreshold()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.requireAllPlanes()).isTrue();
        }

        @Test
        @DisplayName("requires non-null runtime truth service")
        void requiresNonNullRuntimeTruthService() {
            assertThatNullPointerException()
                .isThrownBy(() -> new RuntimeTruthDriftDetector(null))
                .withMessageContaining("runtimeTruthService must not be null");
        }

        @Test
        @DisplayName("requires non-null config")
        void requiresNonNullConfig() {
            assertThatNullPointerException()
                .isThrownBy(() -> new RuntimeTruthDriftDetector(runtimeTruthService, null))
                .withMessageContaining("config must not be null");
        }
    }

    // =========================================================================
    //  Drift Issue Records
    // =========================================================================

    @Nested
    @DisplayName("Drift Issue Records")
    class IssueRecordTests {

        @Test
        @DisplayName("drift issue contains all fields")
        void driftIssueContainsAllFields() {
            RuntimeTruthDriftDetector.DriftIssue issue = new RuntimeTruthDriftDetector.DriftIssue(
                "data-plane",
                RuntimeTruthDriftDetector.DriftType.STALE_STATE,
                RuntimeTruthDriftDetector.Severity.HIGH,
                "Test description",
                Instant.now()
            );

            assertThat(issue.planeName()).isEqualTo("data-plane");
            assertThat(issue.driftType()).isEqualTo(RuntimeTruthDriftDetector.DriftType.STALE_STATE);
            assertThat(issue.severity()).isEqualTo(RuntimeTruthDriftDetector.Severity.HIGH);
            assertThat(issue.description()).isEqualTo("Test description");
            assertThat(issue.detectedAt()).isNotNull();
        }

        @Test
        @DisplayName("requires non-null fields in drift issue")
        void requiresNonNullFieldsInDriftIssue() {
            assertThatNullPointerException()
                .isThrownBy(() -> new RuntimeTruthDriftDetector.DriftIssue(
                    null,
                    RuntimeTruthDriftDetector.DriftType.STALE_STATE,
                    RuntimeTruthDriftDetector.Severity.HIGH,
                    "description",
                    Instant.now()
                ))
                .withMessageContaining("planeName must not be null");
        }
    }
}
