/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import java.util.Map;
import java.util.Objects;

/**
 * Health checker for individual planes in the Data-Cloud system.
 *
 * <p>This service provides health check capabilities for each plane,
 * including dependency checks, resource availability, and operational status.
 *
 * @doc.type class
 * @doc.purpose Provides health check capabilities for Data-Cloud planes
 * @doc.layer product
 * @doc.pattern HealthCheck
 */
public final class PlaneHealthChecker {

    /**
     * Represents the health status of a plane.
     *
     * @param healthy whether the plane is healthy
     * @param message human-readable status message
     * @param details additional health details
     */
    public record HealthStatus(boolean healthy, String message, Map<String, Object> details) {
        public static HealthStatus healthy(String message) {
            return new HealthStatus(true, message, Map.of());
        }

        public static HealthStatus unhealthy(String message) {
            return new HealthStatus(false, message, Map.of());
        }

        public static HealthStatus unhealthy(String message, Map<String, Object> details) {
            return new HealthStatus(false, message, details);
        }
    }

    private final RuntimeTruthService runtimeTruthService;

    public PlaneHealthChecker(RuntimeTruthService runtimeTruthService) {
        this.runtimeTruthService = Objects.requireNonNull(runtimeTruthService, "runtimeTruthService must not be null");
    }

    /**
     * Checks the health of a specific plane.
     *
     * @param planeName the name of the plane to check
     * @return the health status
     */
    public HealthStatus checkPlaneHealth(String planeName) {
        Objects.requireNonNull(planeName, "planeName must not be null");

        RuntimeTruthService.PlaneState planeState = runtimeTruthService.getPlaneState(planeName);

        if (planeState == null) {
            return HealthStatus.unhealthy("Plane not registered in runtime truth");
        }

        // Check if plane is UP or DEGRADED
        if (planeState.status() == RuntimeTruthService.PlaneStatus.DOWN) {
            return HealthStatus.unhealthy("Plane is in DOWN state");
        }

        if (planeState.status() == RuntimeTruthService.PlaneStatus.UNKNOWN) {
            return HealthStatus.unhealthy("Plane status is UNKNOWN");
        }

        // Check metadata for health indicators
        Map<String, Object> metadata = planeState.metadata();
        if (metadata.containsKey("error")) {
            return HealthStatus.unhealthy(
                "Plane reported error: " + metadata.get("error"),
                metadata);
        }

        if (planeState.status() == RuntimeTruthService.PlaneStatus.DEGRADED) {
            return new HealthStatus(
                true,
                "Plane is DEGRADED but operational",
                metadata);
        }

        return new HealthStatus(
            true,
            "Plane is healthy",
            metadata);
    }

    /**
     * Checks the health of all planes.
     *
     * @return map of plane names to their health status
     */
    public Map<String, HealthStatus> checkAllPlanes() {
        RuntimeTruthService.RuntimeTruth truth = runtimeTruthService.getRuntimeTruth();

        return truth.planeStates().keySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                planeName -> planeName,
                this::checkPlaneHealth));
    }

    /**
     * Gets the overall system health.
     *
     * @return the overall health status
     */
    public HealthStatus getSystemHealth() {
        Map<String, HealthStatus> planeHealth = checkAllPlanes();

        long unhealthyCount = planeHealth.values().stream()
            .filter(status -> !status.healthy())
            .count();

        if (unhealthyCount == 0) {
            return HealthStatus.healthy("All planes are healthy");
        }

        if (unhealthyCount == planeHealth.size()) {
            return HealthStatus.unhealthy("All planes are unhealthy");
        }

        return new HealthStatus(
            false,
            unhealthyCount + " of " + planeHealth.size() + " planes are unhealthy",
            Map.of("unhealthyPlanes", unhealthyCount, "totalPlanes", planeHealth.size()));
    }
}
