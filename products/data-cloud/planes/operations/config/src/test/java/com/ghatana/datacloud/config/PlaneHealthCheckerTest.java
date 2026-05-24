/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Phase 3: Contract tests for PlaneHealthChecker.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Plane health checking</li>
 *   <li>System health aggregation</li>
 *   <li>Health status computation based on plane states</li>
 * </ul>
 */
@DisplayName("Plane Health Checker Tests (Phase 3)")
class PlaneHealthCheckerTest {

    private final RuntimeTruthService runtimeTruthService = new RuntimeTruthService();
    private final PlaneHealthChecker healthChecker = new PlaneHealthChecker(runtimeTruthService);

    // =========================================================================
    //  Plane Health Checks
    // =========================================================================

    @Nested
    @DisplayName("Plane Health Checks")
    class PlaneHealthTests {

        @Test
        @DisplayName("returns unhealthy for unregistered plane")
        void returnsUnhealthyForUnregisteredPlane() {
            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("unknown-plane");

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("not registered");
        }

        @Test
        @DisplayName("returns unhealthy for DOWN plane")
        void returnsUnhealthyForDownPlane() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("data-plane");

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("DOWN");
        }

        @Test
        @DisplayName("returns unhealthy for UNKNOWN plane")
        void returnsUnhealthyForUnknownPlane() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UNKNOWN, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("data-plane");

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("UNKNOWN");
        }

        @Test
        @DisplayName("returns healthy for UP plane")
        void returnsHealthyForUpPlane() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("data-plane");

            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).contains("healthy");
        }

        @Test
        @DisplayName("returns healthy for DEGRADED plane with warning")
        void returnsHealthyForDegradedPlaneWithWarning() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DEGRADED, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("data-plane");

            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).contains("DEGRADED");
        }

        @Test
        @DisplayName("returns unhealthy when plane metadata contains error")
        void returnsUnhealthyWhenPlaneMetadataContainsError() {
            Map<String, Object> metadata = Map.of("error", "Connection timeout");
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, metadata);

            PlaneHealthChecker.HealthStatus status = healthChecker.checkPlaneHealth("data-plane");

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("Connection timeout");
        }

        @Test
        @DisplayName("requires non-null plane name")
        void requiresNonNullPlaneName() {
            assertThatNullPointerException()
                .isThrownBy(() -> healthChecker.checkPlaneHealth(null))
                .withMessageContaining("planeName must not be null");
        }
    }

    // =========================================================================
    //  System Health Aggregation
    // =========================================================================

    @Nested
    @DisplayName("System Health Aggregation")
    class SystemHealthTests {

        @Test
        @DisplayName("returns healthy when all planes are healthy")
        void returnsHealthyWhenAllPlanesAreHealthy() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.getSystemHealth();

            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).contains("All planes are healthy");
        }

        @Test
        @DisplayName("returns unhealthy when all planes are unhealthy")
        void returnsUnhealthyWhenAllPlanesAreUnhealthy() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.getSystemHealth();

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("All planes are unhealthy");
        }

        @Test
        @DisplayName("returns unhealthy with count when some planes are unhealthy")
        void returnsUnhealthyWithCountWhenSomePlanesAreUnhealthy() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.DOWN, Map.of());
            runtimeTruthService.updatePlaneState("governance-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            PlaneHealthChecker.HealthStatus status = healthChecker.getSystemHealth();

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).contains("1 of 3");
        }

        @Test
        @DisplayName("checkAllPlanes returns health for all registered planes")
        void checkAllPlanesReturnsHealthForAllRegisteredPlanes() {
            runtimeTruthService.updatePlaneState("data-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());
            runtimeTruthService.updatePlaneState("event-plane", RuntimeTruthService.PlaneStatus.UP, Map.of());

            Map<String, PlaneHealthChecker.HealthStatus> healthMap = healthChecker.checkAllPlanes();

            assertThat(healthMap).hasSize(2);
            assertThat(healthMap).containsKey("data-plane");
            assertThat(healthMap).containsKey("event-plane");
        }
    }

    // =========================================================================
    //  Health Status Records
    // =========================================================================

    @Nested
    @DisplayName("Health Status Records")
    class StatusRecordTests {

        @Test
        @DisplayName("healthy factory creates healthy status")
        void healthyFactoryCreatesHealthyStatus() {
            PlaneHealthChecker.HealthStatus status = PlaneHealthChecker.HealthStatus.healthy("OK");

            assertThat(status.healthy()).isTrue();
            assertThat(status.message()).isEqualTo("OK");
            assertThat(status.details()).isEmpty();
        }

        @Test
        @DisplayName("unhealthy factory creates unhealthy status")
        void unhealthyFactoryCreatesUnhealthyStatus() {
            PlaneHealthChecker.HealthStatus status = PlaneHealthChecker.HealthStatus.unhealthy("Error");

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).isEqualTo("Error");
            assertThat(status.details()).isEmpty();
        }

        @Test
        @DisplayName("unhealthy with details factory includes details")
        void unhealthyWithDetailsFactoryIncludesDetails() {
            Map<String, Object> details = Map.of("code", 500);
            PlaneHealthChecker.HealthStatus status = PlaneHealthChecker.HealthStatus.unhealthy("Error", details);

            assertThat(status.healthy()).isFalse();
            assertThat(status.message()).isEqualTo("Error");
            assertThat(status.details()).isEqualTo(details);
        }
    }
}
