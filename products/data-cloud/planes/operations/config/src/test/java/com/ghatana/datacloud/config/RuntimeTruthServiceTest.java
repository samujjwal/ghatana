/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Phase 3: Contract tests for RuntimeTruthService.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Runtime truth aggregation from all planes</li>
 *   <li>System status computation based on plane states</li>
 *   <li>Plane state registration and retrieval</li>
 * </ul>
 */
@DisplayName("Runtime Truth Service Tests (Phase 3)")
class RuntimeTruthServiceTest {

    private final RuntimeTruthService service = new RuntimeTruthService();

    // =========================================================================
    //  Plane State Registration
    // =========================================================================

    @Nested
    @DisplayName("Plane State Registration")
    class StateRegistrationTests {

        @Test
        @DisplayName("can register plane state")
        void canRegisterPlaneState() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of("entities", 1000));

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state).isNotNull();
            assertThat(state.planeName()).isEqualTo("data-plane");
            assertThat(state.status()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
        }

        @Test
        @DisplayName("can update existing plane state")
        void canUpdateExistingPlaneState() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of("entities", 1000));
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of("entities", 500));

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.status()).isEqualTo(RuntimeTruthService.PlaneStatus.DEGRADED);
        }

        @Test
        @DisplayName("requires non-null plane name")
        void requiresNonNullPlaneName() {
            assertThatNullPointerException()
                .isThrownBy(() -> service.updatePlaneState(null, RuntimeTruthService.PlaneStatus.UP, Map.of()))
                .withMessageContaining("planeName must not be null");
        }

        @Test
        @DisplayName("requires non-null status")
        void requiresNonNullStatus() {
            assertThatNullPointerException()
                .isThrownBy(() -> service.updatePlaneState("data-plane", null, Map.of()))
                .withMessageContaining("status must not be null");
        }

        @Test
        @DisplayName("requires non-null metadata")
        void requiresNonNullMetadata() {
            assertThatNullPointerException()
                .isThrownBy(() -> service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, null))
                .withMessageContaining("metadata must not be null");
        }

        @Test
        @DisplayName("metadata is stored correctly")
        void metadataIsStoredCorrectly() {
            Map<String, Object> metadata = Map.of("entities", 1000, "collections", 50);
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.metadata()).containsAllEntriesOf(metadata);
        }
    }

    // =========================================================================
    //  Plane State Retrieval
    // =========================================================================

    @Nested
    @DisplayName("Plane State Retrieval")
    class StateRetrievalTests {

        @Test
        @DisplayName("returns null for unregistered plane")
        void returnsNullForUnregisteredPlane() {
            RuntimeTruthService.PlaneState state = service.getPlaneState("unknown-plane");
            assertThat(state).isNull();
        }

        @Test
        @DisplayName("returns registered plane state")
        void returnsRegisteredPlaneState() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state).isNotNull();
            assertThat(state.planeName()).isEqualTo("data-plane");
        }

        @Test
        @DisplayName("lastUpdated timestamp is set")
        void lastUpdatedTimestampIsSet() {
            Instant before = Instant.now();
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            Instant after = Instant.now();

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.lastUpdated()).isBetween(before, after);
        }
    }

    // =========================================================================
    //  Runtime Truth Aggregation
    // =========================================================================

    @Nested
    @DisplayName("Runtime Truth Aggregation")
    class AggregationTests {

        @Test
        @DisplayName("system status is UNKNOWN when no planes registered")
        void systemStatusIsUnknownWhenNoPlanesRegistered() {
            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.UNKNOWN);
        }

        @Test
        @DisplayName("system status is UP when all planes are UP")
        void systemStatusIsUpWhenAllPlanesAreUp() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
        }

        @Test
        @DisplayName("system status is DOWN when any plane is DOWN")
        void systemStatusIsDownWhenAnyPlaneIsDown() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);
        }

        @Test
        @DisplayName("system status is DEGRADED when any plane is DEGRADED and none are DOWN")
        void systemStatusIsDegradedWhenAnyPlaneIsDegraded() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DEGRADED);
        }

        @Test
        @DisplayName("runtime truth includes all plane states")
        void runtimeTruthIncludesAllPlaneStates() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.planeStates()).hasSize(2);
            assertThat(truth.planeStates()).containsKey("data-plane");
            assertThat(truth.planeStates()).containsKey("event-plane");
        }

        @Test
        @DisplayName("runtime truth timestamp is current")
        void runtimeTruthTimestampIsCurrent() {
            Instant before = Instant.now();
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            Instant after = Instant.now();

            assertThat(truth.timestamp()).isBetween(before, after);
        }
    }

    // =========================================================================
    //  Clear Operation
    // =========================================================================

    @Nested
    @DisplayName("Clear Operation")
    class ClearTests {

        @Test
        @DisplayName("clear removes all plane states")
        void clearRemovesAllPlaneStates() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            service.clear();

            assertThat(service.getPlaneState("data-plane")).isNull();
            assertThat(service.getPlaneState("event-plane")).isNull();
        }

        @Test
        @DisplayName("clear resets system status to UNKNOWN")
        void clearResetsSystemStatusToUnknown() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.clear();

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.UNKNOWN);
        }
    }

    // =========================================================================
    //  DC-P6-004: Runtime Truth Requirements
    // =========================================================================

    @Nested
    @DisplayName("Runtime Truth Requirements (DC-P6-004)")
    class RuntimeTruthRequirementsTests {

        @Test
        @DisplayName("runtime truth exposes live/degraded/unavailable status")
        void runtimeTruthExposesStatus() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.planeStates().get("data-plane").status()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
            assertThat(truth.planeStates().get("event-plane").status()).isEqualTo(RuntimeTruthService.PlaneStatus.DEGRADED);
            assertThat(truth.planeStates().get("governance-plane").status()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);
        }

        @Test
        @DisplayName("runtime truth exposes dependencies in metadata")
        void runtimeTruthExposesDependencies() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("dependencies", List.of("database", "cache", "message-queue"));
            metadata.put("database", "postgresql://localhost:5432");
            metadata.put("cache", "redis://localhost:6379");

            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.metadata()).containsKey("dependencies");
            assertThat(state.metadata()).containsKey("database");
            assertThat(state.metadata()).containsKey("cache");
        }

        @Test
        @DisplayName("runtime truth exposes health snapshots")
        void runtimeTruthExposesHealthSnapshots() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("health", "healthy");
            metadata.put("cpu", 45.2);
            metadata.put("memory", 78.5);
            metadata.put("disk", 62.1);

            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.metadata()).containsKey("health");
            assertThat(state.metadata()).containsKey("cpu");
            assertThat(state.metadata()).containsKey("memory");
            assertThat(state.metadata()).containsKey("disk");
        }

        @Test
        @DisplayName("runtime truth exposes tenant scoping")
        void runtimeTruthExposesTenantScoping() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("tenantId", "tenant-123");
            metadata.put("tenantCount", 5);
            metadata.put("activeTenants", List.of("tenant-123", "tenant-456", "tenant-789"));

            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.metadata()).containsKey("tenantId");
            assertThat(state.metadata()).containsKey("tenantCount");
            assertThat(state.metadata()).containsKey("activeTenants");
        }

        @Test
        @DisplayName("runtime truth exposes provenance refs")
        void runtimeTruthExposesProvenanceRefs() {
            service.setCommitSha("abc123def456");
            service.setEnvironment("production");

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.commitSha()).isEqualTo("abc123def456");
            assertThat(truth.environment()).isEqualTo("production");
        }

        @Test
        @DisplayName("runtime truth exposes artifact refs in metadata")
        void runtimeTruthExposesArtifactRefs() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("artifactId", "data-cloud-plane-1.0.0.jar");
            metadata.put("artifactVersion", "1.0.0");
            metadata.put("buildTime", "2026-03-27T10:00:00Z");

            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            RuntimeTruthService.PlaneState state = service.getPlaneState("data-plane");
            assertThat(state.metadata()).containsKey("artifactId");
            assertThat(state.metadata()).containsKey("artifactVersion");
            assertThat(state.metadata()).containsKey("buildTime");
        }
    }

    // =========================================================================
    //  DC-P6-004: Failure Injection Tests
    // =========================================================================

    @Nested
    @DisplayName("Failure Injection Tests (DC-P6-004)")
    class FailureInjectionTests {

        @Test
        @DisplayName("system status reflects single plane failure")
        void systemStatusReflectsSinglePlaneFailure() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);
        }

        @Test
        @DisplayName("system status reflects multiple plane failures")
        void systemStatusReflectsMultiplePlaneFailures() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);
        }

        @Test
        @DisplayName("system status recovers when failed plane recovers")
        void systemStatusRecoversWhenFailedPlaneRecovers() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            RuntimeTruthService.RuntimeTruth truth1 = service.getRuntimeTruth();
            assertThat(truth1.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);

            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth2 = service.getRuntimeTruth();
            assertThat(truth2.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.UP);
        }
    }

    // =========================================================================
    //  DC-P6-004: Degraded Dependency Tests
    // =========================================================================

    @Nested
    @DisplayName("Degraded Dependency Tests (DC-P6-004)")
    class DegradedDependencyTests {

        @Test
        @DisplayName("system status is DEGRADED when dependency is degraded")
        void systemStatusIsDegradedWhenDependencyIsDegraded() {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("dependencies", List.of("database", "cache"));
            metadata.put("database", "degraded");
            metadata.put("cache", "healthy");

            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DEGRADED, metadata);
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DEGRADED);
        }

        @Test
        @DisplayName("system status remains DEGRADED with mixed plane states")
        void systemStatusRemainsDegradedWithMixedPlaneStates() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DEGRADED);
        }

        @Test
        @DisplayName("system status prioritizes DOWN over DEGRADED")
        void systemStatusPrioritizesDownOverDegraded() {
            service.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of());
            service.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            service.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            RuntimeTruthService.RuntimeTruth truth = service.getRuntimeTruth();
            assertThat(truth.systemStatus()).isEqualTo(RuntimeTruthService.PlaneStatus.DOWN);
        }
    }
}
