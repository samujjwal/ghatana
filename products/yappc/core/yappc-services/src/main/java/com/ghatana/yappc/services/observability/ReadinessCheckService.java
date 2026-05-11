/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for checking system readiness and health.
 *
 * <p>This service provides readiness checks for all critical system components:
 * database, artifact content store, preview runtime, scaffold packs, generated route registry,
 * and Data Cloud+AEP connectivity.
 *
 * @doc.type interface
 * @doc.purpose System readiness and health checks
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ReadinessCheckService {

    /**
     * Performs all readiness checks.
     *
     * @return ReadinessCheckResult containing overall status and component details
     */
    ReadinessCheckResult checkReadiness();

    /**
     * Checks database connectivity.
     *
     * @return ComponentHealth containing database health status
     */
    ComponentHealth checkDatabase();

    /**
     * Checks artifact content store availability.
     *
     * @return ComponentHealth containing artifact content store health status
     */
    ComponentHealth checkArtifactContentStore();

    /**
     * Checks preview runtime availability.
     *
     * @return ComponentHealth containing preview runtime health status
     */
    ComponentHealth checkPreviewRuntime();

    /**
     * Checks scaffold packs availability.
     *
     * @return ComponentHealth containing scaffold packs health status
     */
    ComponentHealth checkScaffoldPacks();

    /**
     * Checks generated route registry availability.
     *
     * @return ComponentHealth containing route registry health status
     */
    ComponentHealth checkGeneratedRouteRegistry();

    /**
     * Checks Data Cloud+AEP connectivity.
     *
     * @return ComponentHealth containing platform connectivity health status
     */
    ComponentHealth checkPlatformConnectivity();

    /**
     * Readiness check result.
     */
    record ReadinessCheckResult(
        boolean isReady,
        String overallStatus,
        List<ComponentHealth> componentHealth,
        Map<String, String> metadata,
        Instant timestamp
    ) {
        public ReadinessCheckResult {
            if (componentHealth == null) {
                componentHealth = List.of();
            }
            if (metadata == null) {
                metadata = Map.of();
            }
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }
    }

    /**
     * Component health status.
     */
    record ComponentHealth(
        String componentName,
        boolean isHealthy,
        String status,
        String message,
        Map<String, Object> details,
        Instant lastChecked
    ) {
        public ComponentHealth {
            if (details == null) {
                details = Map.of();
            }
            lastChecked = lastChecked != null ? lastChecked : java.time.Instant.now();
        }
    }
}
