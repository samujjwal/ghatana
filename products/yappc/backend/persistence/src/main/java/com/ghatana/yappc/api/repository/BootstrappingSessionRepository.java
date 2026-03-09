/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.BootstrappingSession;
import com.ghatana.yappc.api.domain.BootstrappingSession.SessionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for BootstrappingSession entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for bootstrapping session persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface BootstrappingSessionRepository {

    /**
     * Save a bootstrapping session.
     *
     * @param session the session to save
     * @return Promise with saved session
     */
    Promise<BootstrappingSession> save(BootstrappingSession session);

    /**
     * Find a session by ID.
     *
     * @param tenantId the tenant ID
     * @param id       the session ID
     * @return Promise with optional session
     */
    Promise<Optional<BootstrappingSession>> findById(String tenantId, UUID id);

    /**
     * Find all sessions for a tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise with list of sessions
     */
    Promise<List<BootstrappingSession>> findAllByTenant(String tenantId);

    /**
     * Find sessions by user.
     *
     * @param tenantId the tenant ID
     * @param userId   the user ID
     * @return Promise with list of sessions
     */
    Promise<List<BootstrappingSession>> findByUser(String tenantId, String userId);

    /**
     * Find sessions by status.
     *
     * @param tenantId the tenant ID
     * @param status   the session status
     * @return Promise with list of sessions
     */
    Promise<List<BootstrappingSession>> findByStatus(String tenantId, SessionStatus status);

    /**
     * Find active (non-terminal) sessions for a user.
     *
     * @param tenantId the tenant ID
     * @param userId   the user ID
     * @return Promise with list of active sessions
     */
    Promise<List<BootstrappingSession>> findActiveByUser(String tenantId, String userId);

    /**
     * Find inactive sessions (for cleanup).
     *
     * @param tenantId    the tenant ID
     * @param timeoutDays number of days of inactivity
     * @return Promise with list of inactive sessions
     */
    Promise<List<BootstrappingSession>> findInactive(String tenantId, int timeoutDays);

    /**
     * Delete a session.
     *
     * @param tenantId the tenant ID
     * @param id       the session ID
     * @return Promise with success status
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Check if a session exists.
     *
     * @param tenantId the tenant ID
     * @param id       the session ID
     * @return Promise with existence status
     */
    Promise<Boolean> exists(String tenantId, UUID id);

    /**
     * Count sessions by status.
     *
     * @param tenantId the tenant ID
     * @param status   the session status
     * @return Promise with count
     */
    Promise<Long> countByStatus(String tenantId, SessionStatus status);
}
