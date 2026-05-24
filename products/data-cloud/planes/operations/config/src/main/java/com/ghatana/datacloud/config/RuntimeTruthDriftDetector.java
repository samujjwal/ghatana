/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Detects drift in runtime truth across planes.
 *
 * <p>This service monitors plane states for drift detection, including:
 * - Stale plane states (not updated within expected time window)
 * - Unexpected status changes
 * - Metadata inconsistencies
 *
 * @doc.type class
 * @doc.purpose Detects drift in runtime truth across Data-Cloud planes
 * @doc.layer product
 * @doc.pattern DriftDetector
 */
public final class RuntimeTruthDriftDetector {

    /**
     * Represents a detected drift issue.
     *
     * @param planeName the affected plane
     * @param driftType the type of drift detected
     * @param severity the severity level
     * @param description human-readable description
     * @param detectedAt when the drift was detected
     */
    public record DriftIssue(
            String planeName,
            DriftType driftType,
            Severity severity,
            String description,
            Instant detectedAt) {

        public DriftIssue {
            Objects.requireNonNull(planeName, "planeName must not be null");
            Objects.requireNonNull(driftType, "driftType must not be null");
            Objects.requireNonNull(severity, "severity must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        }
    }

    /**
     * Types of drift that can be detected.
     */
    public enum DriftType {
        STALE_STATE,
        UNEXPECTED_STATUS_CHANGE,
        METADATA_INCONSISTENCY,
        MISSING_PLANE
    }

    /**
     * Severity levels for drift issues.
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Configuration for drift detection thresholds.
     *
     * @param staleThreshold time threshold for considering a state stale
     * @param requireAllPlanes whether all expected planes must be present
     */
    public record DriftDetectionConfig(
            Duration staleThreshold,
            boolean requireAllPlanes) {

        public static DriftDetectionConfig defaults() {
            return new DriftDetectionConfig(Duration.ofMinutes(5), true);
        }
    }

    private final RuntimeTruthService runtimeTruthService;
    private final DriftDetectionConfig config;

    public RuntimeTruthDriftDetector(RuntimeTruthService runtimeTruthService) {
        this(runtimeTruthService, DriftDetectionConfig.defaults());
    }

    public RuntimeTruthDriftDetector(RuntimeTruthService runtimeTruthService, DriftDetectionConfig config) {
        this.runtimeTruthService = Objects.requireNonNull(runtimeTruthService, "runtimeTruthService must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Detects drift across all planes.
     *
     * @return list of detected drift issues
     */
    public java.util.List<DriftIssue> detectDrift() {
        java.util.List<DriftIssue> issues = new java.util.ArrayList<>();
        Instant now = Instant.now();

        RuntimeTruthService.RuntimeTruth truth = runtimeTruthService.getRuntimeTruth();

        // Check for stale states
        for (Map.Entry<String, RuntimeTruthService.PlaneState> entry : truth.planeStates().entrySet()) {
            String planeName = entry.getKey();
            RuntimeTruthService.PlaneState state = entry.getValue();

            // Check if state is stale
            Duration age = Duration.between(state.lastUpdated(), now);
            if (age.compareTo(config.staleThreshold()) > 0) {
                issues.add(new DriftIssue(
                    planeName,
                    DriftType.STALE_STATE,
                    age.multipliedBy(2).compareTo(config.staleThreshold()) > 0 ? Severity.HIGH : Severity.MEDIUM,
                    "Plane state not updated for " + age.toMinutes() + " minutes",
                    now));
            }

            // Check for unexpected status (DOWN without explicit error)
            if (state.status() == RuntimeTruthService.PlaneStatus.DOWN
                    && !state.metadata().containsKey("error")) {
                issues.add(new DriftIssue(
                    planeName,
                    DriftType.UNEXPECTED_STATUS_CHANGE,
                    Severity.HIGH,
                    "Plane is DOWN without error metadata",
                    now));
            }
        }

        // Check for missing expected planes
        if (config.requireAllPlanes()) {
            java.util.Set<String> expectedPlanes = java.util.Set.of(
                "data-plane", "event-plane", "governance-plane", "operations-plane");

            for (String expectedPlane : expectedPlanes) {
                if (!truth.planeStates().containsKey(expectedPlane)) {
                    issues.add(new DriftIssue(
                        expectedPlane,
                        DriftType.MISSING_PLANE,
                        Severity.CRITICAL,
                        "Expected plane not registered in runtime truth",
                        now));
                }
            }
        }

        return issues;
    }

    /**
     * Checks if any drift is detected.
     *
     * @return true if drift is detected
     */
    public boolean hasDrift() {
        return !detectDrift().isEmpty();
    }

    /**
     * Gets the drift detection configuration.
     *
     * @return the configuration
     */
    public DriftDetectionConfig getConfig() {
        return config;
    }
}
