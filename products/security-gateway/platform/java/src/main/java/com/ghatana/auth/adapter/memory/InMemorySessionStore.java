package com.ghatana.auth.adapter.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ghatana.platform.security.port.SessionStore;
import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;

/**
 * In-memory implementation of SessionStore for testing and development.
 *
 * <p><b>Purpose</b><br>
 * Provides ephemeral session storage using in-memory ConcurrentHashMap. Suitable for:
 * - Unit testing authentication flows
 * - Local development without database
 * - Proof-of-concept implementations
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe via ConcurrentHashMap. All operations atomic.
 *
 * <p><b>Data Isolation</b><br>
 * Tenant-scoped queries: Returns only sessions matching tenant ID.
 *
 * @see SessionStore for interface contract
 * @doc.type class
 * @doc.purpose In-memory session store adapter (testing only)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemorySessionStore implements SessionStore {

    // Structure: tenantId -> sessionId -> Session
    private final Map<String, Map<String, Session>> sessionsByTenant = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> store(Session session) {
        if (session == null) {
            return Promise.ofException(new IllegalArgumentException("session must not be null"));
        }

        String tenantId = session.getTenantId().value();
        String sessionId = session.getSessionId().value();

        sessionsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>()).put(sessionId, session);
        return Promise.of(null);
    }

    @Override
    public Promise<Optional<Session>> findById(TenantId tenantId, SessionId sessionId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId must not be null"));
        }
        if (sessionId == null) {
            return Promise.ofException(new IllegalArgumentException("sessionId must not be null"));
        }

        String tenant = tenantId.value();
        String id = sessionId.value();

        Map<String, Session> sessions = sessionsByTenant.getOrDefault(tenant, new ConcurrentHashMap<>());
        Session session = sessions.get(id);

        // Check if session is valid (not expired)
        if (session != null && !session.isValid()) {
            return Promise.of(Optional.empty());
        }
        if (session != null && session.getExpiresAt().isBefore(Instant.now())) {
            return Promise.of(Optional.empty());
        }

        return Promise.of(Optional.ofNullable(session));
    }

    @Override
    public Promise<Void> invalidate(TenantId tenantId, SessionId sessionId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId must not be null"));
        }
        if (sessionId == null) {
            return Promise.ofException(new IllegalArgumentException("sessionId must not be null"));
        }

        String tenant = tenantId.value();
        String id = sessionId.value();

        Map<String, Session> sessions = sessionsByTenant.get(tenant);
        if (sessions != null) {
            sessions.remove(id);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<Optional<Session>> findByUserId(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId must not be null"));
        }
        if (userId == null) {
            return Promise.ofException(new IllegalArgumentException("userId must not be null"));
        }

        String tenant = tenantId.value();
        Map<String, Session> sessions = sessionsByTenant.getOrDefault(tenant, new ConcurrentHashMap<>());

        for (Session session : sessions.values()) {
            if (userId.equals(session.getUserId()) && session.isValid() && session.getExpiresAt().isAfter(Instant.now())) {
                return Promise.of(Optional.of(session));
            }
        }

        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Void> touch(TenantId tenantId, SessionId sessionId) {
        // For in-memory store, touch is a no-op since we don't implement sliding expiry
        return findById(tenantId, sessionId).then(optSession -> {
            if (optSession.isPresent()) {
                Session session = optSession.get();
                // Update lastAccessedAt
                Session updated = Session.builder()
                        .tenantId(session.getTenantId())
                        .sessionId(session.getSessionId())
                        .userId(session.getUserId())
                        .createdAt(session.getCreatedAt())
                        .expiresAt(session.getExpiresAt())
                        .lastAccessedAt(Instant.now())
                        .ipAddress(session.getIpAddress())
                        .userAgent(session.getUserAgent())
                        .valid(session.isValid())
                        .build();
                return store(updated);
            }
            return Promise.of(null);
        });
    }

    @Override
    public Promise<Integer> invalidateAllForUser(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId must not be null"));
        }
        if (userId == null) {
            return Promise.ofException(new IllegalArgumentException("userId must not be null"));
        }

        String tenant = tenantId.value();
        Map<String, Session> sessions = sessionsByTenant.get(tenant);
        
        if (sessions != null) {
            int count = 0;
            Set<String> toRemove = new java.util.HashSet<>();
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                if (userId.equals(entry.getValue().getUserId())) {
                    toRemove.add(entry.getKey());
                    count++;
                }
            }
            for (String sessionId : toRemove) {
                sessions.remove(sessionId);
            }
            return Promise.of(count);
        }

        return Promise.of(0);
    }

    /**
     * Clears all sessions (for testing between test cases).
     */
    public void clear() {
        sessionsByTenant.clear();
    }
}
