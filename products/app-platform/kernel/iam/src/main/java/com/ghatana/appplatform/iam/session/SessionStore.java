package com.ghatana.appplatform.iam.session;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for session store operations. Implementations must be thread-safe.
 *
 * @doc.type interface
 * @doc.purpose Session store port for distributed session management (STORY-K01-RBAC)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SessionStore {

    /**
     * Create or refresh a session.
     *
     * @param sessionId  Unique session identifier
     * @param tenantId   Tenant scope
     * @param principalId  Authenticated principal (user / service)
     * @param ttlSeconds Session time-to-live
     */
    void put(String sessionId, String tenantId, String principalId, int ttlSeconds);

    /**
     * Look up a session by ID. Returns empty if expired or not found.
     */
    Optional<SessionEntry> get(String sessionId);

    /**
     * Invalidate a session immediately.
     */
    void invalidate(String sessionId);

    /**
     * Extend an existing session's TTL.
     */
    void refresh(String sessionId, int ttlSeconds);

    /**
     * Invalidate all sessions belonging to the given principal (STORY-K01-007).
     * Used for global logout or security revocation.
     *
     * @param principalId the user/service whose sessions should all be terminated
     */
    void invalidateAllForUser(String principalId);

    // ── Value Object ──────────────────────────────────────────────────────────

    record SessionEntry(String sessionId, String tenantId, String principalId, Instant expiresAt) {}
}
