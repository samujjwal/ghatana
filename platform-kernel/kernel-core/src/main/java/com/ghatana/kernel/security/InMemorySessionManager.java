package com.ghatana.kernel.security;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * In-memory implementation of {@link SessionManager} for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory session manager for testing (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class InMemorySessionManager implements SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Executor executor;

    public InMemorySessionManager(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<String> createSession(String userId, String tenantId, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sessionId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            Session session = Session.builder()
                .id(sessionId)
                .userId(userId)
                .tenantId(tenantId)
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .lastAccessedAt(now)
                .build();

            sessions.put(sessionId, session);
            LOG.info("[SESSION-MANAGER] Created session sessionId={} userId={} tenantId={} ttl={}", sessionId, userId, tenantId, ttl);
            return sessionId;
        });
    }

    @Override
    public Promise<Optional<Session>> getSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Session session = sessions.get(sessionId);
            if (session == null) {
                return Optional.empty();
            }
            if (session.isExpired()) {
                sessions.remove(sessionId);
                return Optional.empty();
            }
            return Optional.of(session);
        });
    }

    @Override
    public Promise<Boolean> validateSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Session session = sessions.get(sessionId);
            if (session == null) {
                return false;
            }
            if (session.isExpired()) {
                sessions.remove(sessionId);
                return false;
            }
            return true;
        });
    }

    @Override
    public Promise<Void> refreshSession(String sessionId, Duration ttl) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        return Promise.ofBlocking(executor, () -> {
            Session session = sessions.get(sessionId);
            if (session == null) {
                throw new IllegalArgumentException("Session not found: " + sessionId);
            }
            if (session.isExpired()) {
                throw new IllegalArgumentException("Session expired: " + sessionId);
            }

            Session refreshed = session.toBuilder()
                .expiresAt(Instant.now().plus(ttl))
                .lastAccessedAt(Instant.now())
                .build();

            sessions.put(sessionId, refreshed);
            LOG.info("[SESSION-MANAGER] Refreshed session sessionId={}", sessionId);
            return null;
        });
    }

    @Override
    public Promise<Void> invalidateSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Session removed = sessions.remove(sessionId);
            if (removed != null) {
                LOG.info("[SESSION-MANAGER] Invalidated session sessionId={}", sessionId);
            }
            return null;
        });
    }

    @Override
    public Promise<Void> invalidateUserSessions(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                if (entry.getValue().getUserId().equals(userId)) {
                    sessions.remove(entry.getKey());
                    count++;
                }
            }
            LOG.info("[SESSION-MANAGER] Invalidated {} sessions for userId={}", count, userId);
            return null;
        });
    }

    @Override
    public Promise<Integer> cleanupExpiredSessions() {
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            Instant now = Instant.now();
            for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                if (entry.getValue().getExpiresAt().isBefore(now)) {
                    sessions.remove(entry.getKey());
                    count++;
                }
            }
            if (count > 0) {
                LOG.info("[SESSION-MANAGER] Cleaned up {} expired sessions", count);
            }
            return count;
        });
    }
}
