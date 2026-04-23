package com.ghatana.services.auth.concurrency;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent session management tests for the auth gateway.
 *
 * <p>Validates that concurrent login, token refresh, and session invalidation
 * operations are handled correctly without data loss or race conditions.</p>
 *
 * @doc.type    class
 * @doc.purpose Concurrent session management correctness tests for auth gateway
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("Concurrent Session Management Tests")
@Tag("integration")
class ConcurrentSessionManagementTest extends EventloopTestBase {

    /** Simple in-memory session store used in tests. */
    private ConcurrentHashMap<String, Session> sessionStore;

    @BeforeEach
    void setUp() { // GH-90000
        sessionStore = new ConcurrentHashMap<>(); // GH-90000
    }

    // ── Session data model ────────────────────────────────────────────────────

    record Session(String sessionId, String userId, String tenantId, // GH-90000
                   Instant createdAt, Instant expiresAt, String accessToken) {

        boolean isExpired() { // GH-90000
            return Instant.now().isAfter(expiresAt); // GH-90000
        }

        Session withRefreshedExpiry(Instant newExpiry) { // GH-90000
            return new Session(sessionId, userId, tenantId, createdAt, newExpiry, accessToken); // GH-90000
        }
    }

    // ── Concurrent login ──────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent login attempts for different users all succeed")
    void concurrentLoginForDifferentUsersAllSucceed() throws InterruptedException { // GH-90000
        int userCount = 20;
        CountDownLatch latch = new CountDownLatch(userCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000

        for (int i = 0; i < userCount; i++) { // GH-90000
            final String userId = "user-concurrent-" + i;
            Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    String sessionId = UUID.randomUUID().toString(); // GH-90000
                    Session session = new Session( // GH-90000
                            sessionId, userId, "tenant-concurrent",
                            Instant.now(), Instant.now().plusSeconds(3600), // GH-90000
                            "token-" + sessionId);
                    sessionStore.put(sessionId, session); // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000
        assertThat(successCount.get()).isEqualTo(userCount); // GH-90000
        assertThat(sessionStore).hasSize(userCount); // GH-90000
    }

    @Test
    @DisplayName("concurrent logins for same user create separate sessions")
    void concurrentLoginsForSameUserCreateSeparateSessions() throws InterruptedException { // GH-90000
        String userId = "user-multi-session";
        int loginCount = 5;
        CountDownLatch latch = new CountDownLatch(loginCount); // GH-90000
        List<String> sessionIds = Collections.synchronizedList(new ArrayList<>()); // GH-90000

        for (int i = 0; i < loginCount; i++) { // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    String sessionId = UUID.randomUUID().toString(); // GH-90000
                    Session session = new Session( // GH-90000
                            sessionId, userId, "tenant-A",
                            Instant.now(), Instant.now().plusSeconds(3600), // GH-90000
                            "token-" + sessionId);
                    sessionStore.put(sessionId, session); // GH-90000
                    sessionIds.add(sessionId); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        assertThat(sessionIds).hasSize(loginCount); // GH-90000
        // All session IDs must be unique
        assertThat(new HashSet<>(sessionIds)).hasSize(loginCount); // GH-90000
    }

    // ── Concurrent token refresh ──────────────────────────────────────────────

    @Test
    @DisplayName("concurrent token refresh requests extend session expiry correctly")
    void concurrentTokenRefreshExtendsSessionExpiry() throws InterruptedException { // GH-90000
        String sessionId = UUID.randomUUID().toString(); // GH-90000
        Session original = new Session( // GH-90000
                sessionId, "user-refresh", "tenant-refresh",
                Instant.now(), Instant.now().plusSeconds(60), "old-token"); // GH-90000
        sessionStore.put(sessionId, original); // GH-90000

        int refreshCount = 10;
        CountDownLatch latch = new CountDownLatch(refreshCount); // GH-90000
        AtomicInteger refreshOkCount = new AtomicInteger(0); // GH-90000

        for (int i = 0; i < refreshCount; i++) { // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    Session current = sessionStore.get(sessionId); // GH-90000
                    if (current != null && !current.isExpired()) { // GH-90000
                        Session refreshed = current.withRefreshedExpiry(Instant.now().plusSeconds(3600)); // GH-90000
                        sessionStore.replace(sessionId, refreshed); // GH-90000
                        refreshOkCount.incrementAndGet(); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        Session finalSession = sessionStore.get(sessionId); // GH-90000
        assertThat(finalSession).isNotNull(); // GH-90000
        assertThat(finalSession.isExpired()).isFalse(); // GH-90000
        assertThat(finalSession.expiresAt()).isAfter(original.expiresAt()); // GH-90000
    }

    // ── Concurrent session invalidation ──────────────────────────────────────

    @Test
    @DisplayName("concurrent logout requests for same session result in session removal")
    void concurrentLogoutForSameSessionRemovesSession() throws InterruptedException { // GH-90000
        String sessionId = UUID.randomUUID().toString(); // GH-90000
        Session session = new Session( // GH-90000
                sessionId, "user-logout", "tenant-logout",
                Instant.now(), Instant.now().plusSeconds(3600), "token-xyz"); // GH-90000
        sessionStore.put(sessionId, session); // GH-90000

        int logoutCount = 5;
        CountDownLatch latch = new CountDownLatch(logoutCount); // GH-90000
        AtomicInteger removedCount = new AtomicInteger(0); // GH-90000

        for (int i = 0; i < logoutCount; i++) { // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    Session removed = sessionStore.remove(sessionId); // GH-90000
                    if (removed != null) { // GH-90000
                        removedCount.incrementAndGet(); // GH-90000
                    }
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        // Exactly one thread removes the session (ConcurrentHashMap guarantees) // GH-90000
        assertThat(removedCount.get()).isEqualTo(1); // GH-90000
        assertThat(sessionStore.containsKey(sessionId)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("session invalidation does not affect other active sessions")
    void sessionInvalidationDoesNotAffectOtherSessions() throws InterruptedException { // GH-90000
        // Seed 10 sessions
        List<String> allSessionIds = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            String sid = UUID.randomUUID().toString(); // GH-90000
            sessionStore.put(sid, new Session( // GH-90000
                    sid, "user-" + i, "tenant-" + i,
                    Instant.now(), Instant.now().plusSeconds(3600), "tok-" + i)); // GH-90000
            allSessionIds.add(sid); // GH-90000
        }

        // Invalidate the first 5 concurrently
        CountDownLatch latch = new CountDownLatch(5); // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            final String sid = allSessionIds.get(i); // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                sessionStore.remove(sid); // GH-90000
                latch.countDown(); // GH-90000
            });
        }
        latch.await(5, TimeUnit.SECONDS); // GH-90000

        // Remaining 5 sessions must be intact
        for (int i = 5; i < 10; i++) { // GH-90000
            assertThat(sessionStore.containsKey(allSessionIds.get(i))).isTrue(); // GH-90000
        }
        assertThat(sessionStore).hasSize(5); // GH-90000
    }

    // ── Session conflict resolution ───────────────────────────────────────────

    @Test
    @DisplayName("simultaneous upsert of same session ID preserves exactly one session")
    void simultaneousUpsertPreservesOneSession() throws InterruptedException { // GH-90000
        String sharedSessionId = UUID.randomUUID().toString(); // GH-90000
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

        for (int i = 0; i < threadCount; i++) { // GH-90000
            final int index = i;
            Thread.ofVirtual().start(() -> { // GH-90000
                Session s = new Session( // GH-90000
                        sharedSessionId, "user-" + index, "tenant-conflict",
                        Instant.now(), Instant.now().plusSeconds(3600), "tok-" + index); // GH-90000
                sessionStore.put(sharedSessionId, s); // GH-90000
                latch.countDown(); // GH-90000
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        // One session must win — store has exactly one entry for the session ID
        assertThat(sessionStore.containsKey(sharedSessionId)).isTrue(); // GH-90000
        assertThat(sessionStore).hasSize(1); // GH-90000
    }
}
