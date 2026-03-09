/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.port;

import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Port for session storage operations.
 *
 * <p>Abstracts session persistence for user sessions.
 *
 * @doc.type interface
 * @doc.purpose Port for user session storage, retrieval, and invalidation
 * @doc.layer security
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface SessionStore {

    /**
     * Stores a session.
     *
     * @param session the session to store
     * @return promise completing when stored
     */
    Promise<Void> store(Session session);

    /**
     * Retrieves a session by ID.
     *
     * @param tenantId the tenant ID
     * @param sessionId the session identifier
     * @return promise of optional session
     */
    Promise<Optional<Session>> findById(TenantId tenantId, SessionId sessionId);

    /**
     * Retrieves a session by user ID.
     *
     * @param tenantId the tenant ID
     * @param userId the user identifier
     * @return promise of optional session
     */
    Promise<Optional<Session>> findByUserId(TenantId tenantId, UserId userId);

    /**
     * Invalidates a session.
     *
     * @param tenantId the tenant ID
     * @param sessionId the session identifier
     * @return promise completing when invalidated
     */
    Promise<Void> invalidate(TenantId tenantId, SessionId sessionId);

    /**
     * Invalidates all sessions for a user.
     *
     * @param tenantId the tenant ID
     * @param userId the user identifier
     * @return promise of count of sessions invalidated
     */
    Promise<Integer> invalidateAllForUser(TenantId tenantId, UserId userId);

    /**
     * Updates last accessed time.
     *
     * @param tenantId the tenant ID
     * @param sessionId the session identifier
     * @return promise completing when updated
     */
    Promise<Void> touch(TenantId tenantId, SessionId sessionId);
}
