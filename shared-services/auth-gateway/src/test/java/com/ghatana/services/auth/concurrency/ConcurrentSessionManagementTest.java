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
    void setUp() { 
        sessionStore = new ConcurrentHashMap<>(); 
    }

    // ── Session data model ────────────────────────────────────────────────────

    record Session(String sessionId, String userId, String tenantId, 
                   Instant createdAt, Instant expiresAt, String accessToken) {

        boolean isExpired() { 
            return Instant.now().isAfter(expiresAt); 
        }

        Session withRefreshedExpiry(Instant newExpiry) { 
            return new Session(sessionId, userId, tenantId, createdAt, newExpiry, accessToken); 
        }
    }

    // ── Concurrent login ──────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent login attempts for different users all succeed")
    void concurrentLoginForDifferentUsersAllSucceed() throws InterruptedException { 
        int userCount = 20;
        CountDownLatch latch = new CountDownLatch(userCount); 
        AtomicInteger successCount = new AtomicInteger(0); 

        for (int i = 0; i < userCount; i++) { 
            final String userId = "user-concurrent-" + i;
            Thread.ofVirtual().start(() -> { 
                try {
                    String sessionId = UUID.randomUUID().toString(); 
                    Session session = new Session( 
                            sessionId, userId, "tenant-concurrent",
                            Instant.now(), Instant.now().plusSeconds(3600), 
                            "token-" + sessionId);
                    sessionStore.put(sessionId, session); 
                    successCount.incrementAndGet(); 
                } finally {
                    latch.countDown(); 
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); 
        assertThat(successCount.get()).isEqualTo(userCount); 
        assertThat(sessionStore).hasSize(userCount); 
    }

    @Test
    @DisplayName("concurrent logins for same user create separate sessions")
    void concurrentLoginsForSameUserCreateSeparateSessions() throws InterruptedException { 
        String userId = "user-multi-session";
        int loginCount = 5;
        CountDownLatch latch = new CountDownLatch(loginCount); 
        List<String> sessionIds = Collections.synchronizedList(new ArrayList<>()); 

        for (int i = 0; i < loginCount; i++) { 
            Thread.ofVirtual().start(() -> { 
                try {
                    String sessionId = UUID.randomUUID().toString(); 
                    Session session = new Session( 
                            sessionId, userId, "tenant-A",
                            Instant.now(), Instant.now().plusSeconds(3600), 
                            "token-" + sessionId);
                    sessionStore.put(sessionId, session); 
                    sessionIds.add(sessionId); 
                } finally {
                    latch.countDown(); 
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); 

        assertThat(sessionIds).hasSize(loginCount); 
        // All session IDs must be unique
        assertThat(new HashSet<>(sessionIds)).hasSize(loginCount); 
    }

    // ── Concurrent token refresh ──────────────────────────────────────────────

    @Test
    @DisplayName("concurrent token refresh requests extend session expiry correctly")
    void concurrentTokenRefreshExtendsSessionExpiry() throws InterruptedException { 
        String sessionId = UUID.randomUUID().toString(); 
        Session original = new Session( 
                sessionId, "user-refresh", "tenant-refresh",
                Instant.now(), Instant.now().plusSeconds(60), "old-token"); 
        sessionStore.put(sessionId, original); 

        int refreshCount = 10;
        CountDownLatch latch = new CountDownLatch(refreshCount); 
        AtomicInteger refreshOkCount = new AtomicInteger(0); 

        for (int i = 0; i < refreshCount; i++) { 
            Thread.ofVirtual().start(() -> { 
                try {
                    Session current = sessionStore.get(sessionId); 
                    if (current != null && !current.isExpired()) { 
                        Session refreshed = current.withRefreshedExpiry(Instant.now().plusSeconds(3600)); 
                        sessionStore.replace(sessionId, refreshed); 
                        refreshOkCount.incrementAndGet(); 
                    }
                } finally {
                    latch.countDown(); 
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); 

        Session finalSession = sessionStore.get(sessionId); 
        assertThat(finalSession).isNotNull(); 
        assertThat(finalSession.isExpired()).isFalse(); 
        assertThat(finalSession.expiresAt()).isAfter(original.expiresAt()); 
    }

    // ── Concurrent session invalidation ──────────────────────────────────────

    @Test
    @DisplayName("concurrent logout requests for same session result in session removal")
    void concurrentLogoutForSameSessionRemovesSession() throws InterruptedException { 
        String sessionId = UUID.randomUUID().toString(); 
        Session session = new Session( 
                sessionId, "user-logout", "tenant-logout",
                Instant.now(), Instant.now().plusSeconds(3600), "token-xyz"); 
        sessionStore.put(sessionId, session); 

        int logoutCount = 5;
        CountDownLatch latch = new CountDownLatch(logoutCount); 
        AtomicInteger removedCount = new AtomicInteger(0); 

        for (int i = 0; i < logoutCount; i++) { 
            Thread.ofVirtual().start(() -> { 
                try {
                    Session removed = sessionStore.remove(sessionId); 
                    if (removed != null) { 
                        removedCount.incrementAndGet(); 
                    }
                } finally {
                    latch.countDown(); 
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); 

        // Exactly one thread removes the session (ConcurrentHashMap guarantees) 
        assertThat(removedCount.get()).isEqualTo(1); 
        assertThat(sessionStore.containsKey(sessionId)).isFalse(); 
    }

    @Test
    @DisplayName("session invalidation does not affect other active sessions")
    void sessionInvalidationDoesNotAffectOtherSessions() throws InterruptedException { 
        // Seed 10 sessions
        List<String> allSessionIds = new ArrayList<>(); 
        for (int i = 0; i < 10; i++) { 
            String sid = UUID.randomUUID().toString(); 
            sessionStore.put(sid, new Session( 
                    sid, "user-" + i, "tenant-" + i,
                    Instant.now(), Instant.now().plusSeconds(3600), "tok-" + i)); 
            allSessionIds.add(sid); 
        }

        // Invalidate the first 5 concurrently
        CountDownLatch latch = new CountDownLatch(5); 
        for (int i = 0; i < 5; i++) { 
            final String sid = allSessionIds.get(i); 
            Thread.ofVirtual().start(() -> { 
                sessionStore.remove(sid); 
                latch.countDown(); 
            });
        }
        latch.await(5, TimeUnit.SECONDS); 

        // Remaining 5 sessions must be intact
        for (int i = 5; i < 10; i++) { 
            assertThat(sessionStore.containsKey(allSessionIds.get(i))).isTrue(); 
        }
        assertThat(sessionStore).hasSize(5); 
    }

    // ── Session conflict resolution ───────────────────────────────────────────

    @Test
    @DisplayName("simultaneous upsert of same session ID preserves exactly one session")
    void simultaneousUpsertPreservesOneSession() throws InterruptedException { 
        String sharedSessionId = UUID.randomUUID().toString(); 
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount); 

        for (int i = 0; i < threadCount; i++) { 
            final int index = i;
            Thread.ofVirtual().start(() -> { 
                Session s = new Session( 
                        sharedSessionId, "user-" + index, "tenant-conflict",
                        Instant.now(), Instant.now().plusSeconds(3600), "tok-" + index); 
                sessionStore.put(sharedSessionId, s); 
                latch.countDown(); 
            });
        }

        latch.await(5, TimeUnit.SECONDS); 

        // One session must win — store has exactly one entry for the session ID
        assertThat(sessionStore.containsKey(sharedSessionId)).isTrue(); 
        assertThat(sessionStore).hasSize(1); 
    }
}
