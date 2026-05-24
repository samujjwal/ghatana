/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
}
