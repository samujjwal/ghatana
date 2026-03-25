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
 * Port for user session storage, retrieval, and invalidation.
 *
 * <p><b>Contract</b><br>
 * All operations are tenant-scoped.  Implementations MUST guarantee
 * strict tenant isolation — no operation on tenant A may read or modify data
 * belonging to tenant B.  Implementations SHOULD be thread-safe, as multiple
 * ActiveJ event-loop threads may call these methods concurrently.
 *
 * <p><b>Implementation guidelines</b><br>
 * <ul>
 *   <li><b>Storage backend</b>: A Redis or Dragonfly sorted-set per-tenant
 *       keyed by {@code SessionId} is the recommended backend (O(1) look-ups).
 *       An in-memory {@code ConcurrentHashMap} is acceptable for testing only.</li>
 *   <li><b>Expiry</b>: Callers manage session TTL via
 *       {@link Session#expiresAt()}.  Implementations that natively support
 *       TTL (e.g. Redis {@code EXPIRE}) SHOULD rely on it rather than
 *       scheduling deletion manually.</li>
 *   <li><b>Consistency</b>: {@link #store} and {@link #invalidate} MUST be
 *       atomic with respect to concurrent reads.  Prefer a single-key
 *       {@code SET} / {@code DEL} rather than read-modify-write.</li>
 *   <li><b>Async</b>: Return {@link io.activej.promise.Promise} wrapping the
 *       result.  Use {@link io.activej.promise.Promise#ofBlocking} for any
 *       blocking I/O so the ActiveJ event loop is never blocked.</li>
 *   <li><b>Error handling</b>: Propagate storage errors as failed
 *       {@link io.activej.promise.Promise} instances — never swallow
 *       exceptions silently.</li>
 * </ul>
 *
 * <p><b>Usage example</b><br>
 * <pre>{@code
 * // Inject the store from the DI container
 * SessionStore sessions = injector.getInstance(SessionStore.class);
 *
 * // 1. Create and persist a new session
 * Session session = Session.create(tenantId, userId, Duration.ofHours(1));
 * sessions.store(session)
 *     .whenResult(() -> log.info("Session {} stored", session.sessionId()));
 *
 * // 2. Look up an incoming request session
 * sessions.findById(tenantId, incomingSessionId)
 *     .then(opt -> {
 *         if (opt.isEmpty()) return Promise.ofException(new UnauthorizedException());
 *         return sessions.touch(tenantId, incomingSessionId)   // refresh TTL
 *             .map($ -> opt.get());
 *     });
 *
 * // 3. Logout — invalidate all sessions for the user
 * sessions.invalidateAllForUser(tenantId, userId)
 *     .whenResult(count -> log.info("Invalidated {} session(s)", count));
 * }</pre>
 *
 * @see Session
 * @see SessionId
 * @see TokenStore for token-level (JWT) persistence
 *
 * @doc.type interface
 * @doc.purpose Port for user session storage, retrieval, and invalidation
 * @doc.layer security
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface SessionStore {

    /**
     * Stores a session, overwriting any existing session with the same ID.
     *
     * <p>Implementations SHOULD set the storage entry TTL to
     * {@code session.expiresAt()} so expired sessions are automatically
     * cleaned up by the backend.
     *
     * @param session the session to persist; must not be {@code null}
     * @return a {@link Promise} that completes when the session is durably stored
     */
    Promise<Void> store(Session session);

    /**
     * Retrieves a session by its unique identifier, scoped to a tenant.
     *
     * <p>Returns {@link Optional#empty()} when the session does not exist
     * <em>or</em> has expired.  Callers that need to distinguish between the
     * two cases should check {@link Session#expiresAt()} after a successful
     * look-up.
     *
     * @param tenantId  the tenant scope; must not be {@code null}
     * @param sessionId the session identifier to look up; must not be {@code null}
     * @return a {@link Promise} of the session if found, or
     *         {@link Optional#empty()} otherwise
     */
    Promise<Optional<Session>> findById(TenantId tenantId, SessionId sessionId);

    /**
     * Retrieves the most recent active session for a user within a tenant.
     *
     * <p>If the user has multiple active sessions this method returns any one
     * of them (implementation-defined).  Callers that need all sessions should
     * use {@link #findById} after enumerating session IDs from the domain
     * layer.
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @param userId   the user identifier; must not be {@code null}
     * @return a {@link Promise} of the session if found, or
     *         {@link Optional#empty()} if the user has no active sessions
     */
    Promise<Optional<Session>> findByUserId(TenantId tenantId, UserId userId);

    /**
     * Invalidates (deletes) a specific session.
     *
     * <p>If the session does not exist the operation completes successfully
     * without error (idempotent).
     *
     * @param tenantId  the tenant scope; must not be {@code null}
     * @param sessionId the session to invalidate; must not be {@code null}
     * @return a {@link Promise} that completes when the session has been removed
     */
    Promise<Void> invalidate(TenantId tenantId, SessionId sessionId);

    /**
     * Invalidates all active sessions for the given user within a tenant.
     *
     * <p>Useful during logout-all, password change, or account suspension flows.
     * If the user has no active sessions the operation completes successfully
     * and returns {@code 0} (idempotent).
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @param userId   the user whose sessions should all be invalidated;
     *                 must not be {@code null}
     * @return a {@link Promise} of the number of sessions that were invalidated
     */
    Promise<Integer> invalidateAllForUser(TenantId tenantId, UserId userId);

    /**
     * Updates the last-accessed timestamp for a session, effectively sliding
     * its expiry window forward.
     *
     * <p>Implementations SHOULD update both the in-memory/cached representation
     * and the backing storage TTL atomically.  If the session does not exist the
     * operation should complete without error (idempotent).
     *
     * @param tenantId  the tenant scope; must not be {@code null}
     * @param sessionId the session to touch; must not be {@code null}
     * @return a {@link Promise} that completes when the TTL has been refreshed
     */
    Promise<Void> touch(TenantId tenantId, SessionId sessionId);
}
