/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Central runtime truth service aggregating state from all Data-Cloud planes.
 *
 * <p>This service provides a unified view of the system state by aggregating
 * runtime truth from:
 * - Data Plane (entity storage state)
 * - Event Plane (event log state)
 * - Governance Plane (policy state)
 * - Action Plane (agent state)
 *
 * @doc.type class
 * @doc.purpose Central runtime truth aggregation for all Data-Cloud planes
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RuntimeTruthService {

    /**
     * Represents the runtime state of a specific plane.
     *
     * @param planeName the name of the plane
     * @param status the current status (UP, DOWN, DEGRADED)
     * @param metadata additional plane-specific metadata
     * @param lastUpdated timestamp of last state update
     */
    public record PlaneState(
            String planeName,
            PlaneStatus status,
            Map<String, Object> metadata,
            Instant lastUpdated) {}

    /**
     * Enumeration of plane status values.
     */
    public enum PlaneStatus {
        UP,
        DOWN,
        DEGRADED,
        UNKNOWN
    }

    /**
     * Represents the aggregated runtime truth for the entire system.
     *
     * @param systemStatus overall system status
     * @param planeStates individual plane states
     * @param timestamp timestamp of this truth snapshot
     */
    public record RuntimeTruth(
            PlaneStatus systemStatus,
            Map<String, PlaneState> planeStates,
            Instant timestamp) {}

    private final Map<String, PlaneState> planeStates = new HashMap<>();

    /**
     * Registers or updates the state of a plane.
     *
     * @param planeName the name of the plane
     * @param status the current status
     * @param metadata additional metadata
     */
    public void updatePlaneState(String planeName, PlaneStatus status, Map<String, Object> metadata) {
        Objects.requireNonNull(planeName, "planeName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        planeStates.put(planeName, new PlaneState(planeName, status, metadata, Instant.now()));
    }

    /**
     * Gets the current state of a specific plane.
     *
     * @param planeName the name of the plane
     * @return the plane state, or null if not registered
     */
    public PlaneState getPlaneState(String planeName) {
        return planeStates.get(planeName);
    }

    /**
     * Gets the aggregated runtime truth for the entire system.
     *
     * @return the current runtime truth snapshot
     */
    public RuntimeTruth getRuntimeTruth() {
        PlaneStatus systemStatus = computeSystemStatus();
        return new RuntimeTruth(systemStatus, Map.copyOf(planeStates), Instant.now());
    }

    /**
     * Computes the overall system status based on plane states.
     *
     * @return the computed system status
     */
    private PlaneStatus computeSystemStatus() {
        if (planeStates.isEmpty()) {
            return PlaneStatus.UNKNOWN;
        }

        boolean anyDown = planeStates.values().stream()
            .anyMatch(state -> state.status() == PlaneStatus.DOWN);

        boolean anyDegraded = planeStates.values().stream()
            .anyMatch(state -> state.status() == PlaneStatus.DEGRADED);

        if (anyDown) {
            return PlaneStatus.DOWN;
        } else if (anyDegraded) {
            return PlaneStatus.DEGRADED;
        } else {
            return PlaneStatus.UP;
        }
    }

    /**
     * Clears all plane states (useful for testing or reset).
     */
    public void clear() {
        planeStates.clear();
    }
}
