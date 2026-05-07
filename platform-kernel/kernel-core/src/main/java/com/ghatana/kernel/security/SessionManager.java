package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Optional;

/**
 * Manager for user session operations.
 *
 * @doc.type interface
 * @doc.purpose Session management operations (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public interface SessionManager {

    /**
     * Create a new session for a user.
     *
     * @param userId user identifier
     * @param tenantId tenant identifier
     * @param ttl session time-to-live
     * @return Promise containing the session ID
     */
    Promise<String> createSession(String userId, String tenantId, Duration ttl);

    /**
     * Get session by ID.
     *
     * @param sessionId session identifier
     * @return Promise containing session if found
     */
    Promise<Optional<Session>> getSession(String sessionId);

    /**
     * Validate a session.
     *
     * @param sessionId session identifier
     * @return Promise containing true if session is valid
     */
    Promise<Boolean> validateSession(String sessionId);

    /**
     * Refresh a session.
     *
     * @param sessionId session identifier
     * @param ttl new time-to-live
     * @return Promise that completes when refresh is finished
     */
    Promise<Void> refreshSession(String sessionId, Duration ttl);

    /**
     * Invalidate a session.
     *
     * @param sessionId session identifier
     * @return Promise that completes when invalidation is finished
     */
    Promise<Void> invalidateSession(String sessionId);

    /**
     * Invalidate all sessions for a user.
     *
     * @param userId user identifier
     * @return Promise that completes when invalidation is finished
     */
    Promise<Void> invalidateUserSessions(String userId);

    /**
     * Clean up expired sessions.
     *
     * @return Promise containing count of sessions cleaned
     */
    Promise<Integer> cleanupExpiredSessions();
}
