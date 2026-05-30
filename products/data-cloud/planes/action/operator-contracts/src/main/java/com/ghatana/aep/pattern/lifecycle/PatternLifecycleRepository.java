/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.lifecycle;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for durable pattern lifecycle state and event storage.
 *
 * <p>Replaces in-memory HashMap storage with persistent storage for production
 * use. Stores pattern state, lifecycle events, promotion decisions, actor, policy
 * decision, confidence, and trace ID.
 *
 * @doc.type interface
 * @doc.purpose Durable storage for pattern lifecycle state and events
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PatternLifecycleRepository {

    /**
     * Save or update pattern lifecycle state.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param state lifecycle state
     * @return promise completing when saved
     */
    Promise<Void> saveState(String tenantId, String patternId, PatternLifecycleState state);

    /**
     * Get current pattern lifecycle state.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of current state (empty if not found)
     */
    Promise<Optional<PatternLifecycleState>> getState(String tenantId, String patternId);

    /**
     * Save a lifecycle event.
     *
     * @param event lifecycle event to save
     * @return promise completing when saved
     */
    Promise<Void> saveEvent(PatternLifecycleEvent event);

    /**
     * Get lifecycle events for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise of lifecycle events (empty list if none)
     */
    Promise<List<PatternLifecycleEvent>> getEvents(String tenantId, String patternId);

    /**
     * Delete lifecycle state and events for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return promise completing when deleted
     */
    Promise<Void> delete(String tenantId, String patternId);

    /**
     * Get all patterns in a specific state for a tenant.
     *
     * @param tenantId tenant identifier
     * @param state lifecycle state to filter by
     * @return promise of pattern IDs in the specified state
     */
    Promise<List<String>> getPatternsByState(String tenantId, PatternLifecycleState state);
}
