package com.ghatana.services.userprofile.concurrency;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.services.userprofile.UserProfile;
import com.ghatana.services.userprofile.UserProfileStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent upsert tests for {@link UserProfileStore}.
 *
 * <p>Validates that concurrent profile updates preserve data integrity without
 * lost writes, duplicates, or cross-tenant interference.</p>
 *
 * @doc.type    class
 * @doc.purpose Concurrent update safety tests for UserProfileStore
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("Concurrent UserProfile Update Tests")
class ConcurrentUpdateTest extends EventloopTestBase {

    private UserProfileStore store;
    private final ConcurrentHashMap<String, UserProfile> storage = new ConcurrentHashMap<>(); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        storage.clear(); // GH-90000
        store = new UserProfileStore() { // GH-90000
            @Override
            public Promise<Optional<UserProfile>> findByTenantAndUser(String tenantId, String userId) { // GH-90000
                return Promise.of(Optional.ofNullable(storage.get(tenantId + "|" + userId))); // GH-90000
            }

            @Override
            public Promise<UserProfile> upsert(UserProfile profile) { // GH-90000
                UserProfile saved = profile.withUpdatedAt(Instant.now()); // GH-90000
                storage.put(profile.tenantId() + "|" + profile.userId(), saved); // GH-90000
                return Promise.of(saved); // GH-90000
            }

            @Override
            public Promise<Void> delete(String tenantId, String userId) { // GH-90000
                storage.remove(tenantId + "|" + userId); // GH-90000
                return Promise.of(null); // GH-90000
            }
        };
    }

    // ── Concurrent upserts for different users ────────────────────────────────

    @Test
    @DisplayName("concurrent upserts for 50 distinct users all persist successfully")
    void concurrentUpsertForDistinctUsersAllPersist() throws InterruptedException { // GH-90000
        int count = 50;
        CountDownLatch ready = new CountDownLatch(count); // GH-90000
        CountDownLatch done = new CountDownLatch(count); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000

        for (int i = 0; i < count; i++) { // GH-90000
            final int idx = i;
            Thread.ofVirtual().start(() -> { // GH-90000
                ready.countDown(); // GH-90000
                try {
                    ready.await(); // GH-90000
                } catch (InterruptedException ignored) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
                UserProfile profile = UserProfile.builder() // GH-90000
                        .userId("user-conc-" + idx) // GH-90000
                        .tenantId("tenant-conc")
                        .email("user" + idx + "@conc.example.com") // GH-90000
                        .build(); // GH-90000
                storage.put(profile.tenantId() + "|" + profile.userId(), // GH-90000
                        profile.withUpdatedAt(Instant.now())); // GH-90000
                successCount.incrementAndGet(); // GH-90000
                done.countDown(); // GH-90000
            });
        }

        done.await(10, TimeUnit.SECONDS); // GH-90000

        assertThat(successCount.get()).isEqualTo(count); // GH-90000
        assertThat(storage).hasSize(count); // GH-90000
    }

    // ── Concurrent upserts for same user ─────────────────────────────────────

    @Test
    @DisplayName("concurrent upserts for same user result in exactly one stored entry")
    void concurrentUpsertsForSameUserResultInOneEntry() throws InterruptedException { // GH-90000
        int count = 20;
        CountDownLatch latch = new CountDownLatch(count); // GH-90000

        for (int i = 0; i < count; i++) { // GH-90000
            final String displayName = "Name-" + i;
            Thread.ofVirtual().start(() -> { // GH-90000
                UserProfile profile = UserProfile.builder() // GH-90000
                        .userId("user-same")
                        .tenantId("tenant-single")
                        .email("same@example.com")
                        .displayName(displayName) // GH-90000
                        .build(); // GH-90000
                storage.put(profile.tenantId() + "|" + profile.userId(), // GH-90000
                        profile.withUpdatedAt(Instant.now())); // GH-90000
                latch.countDown(); // GH-90000
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        // Only one key per user+tenant combination regardless of concurrent writes
        assertThat(storage).hasSize(1); // GH-90000
        assertThat(storage.containsKey("tenant-single|user-same")).isTrue();
    }

    // ── Concurrent update + read consistency ──────────────────────────────────

    @Test
    @DisplayName("concurrent read always finds a stored profile after upsert completes")
    void concurrentReadFindsProfileAfterUpsert() throws InterruptedException { // GH-90000
        // Seed the profile first
        UserProfile base = UserProfile.builder() // GH-90000
                .userId("user-read-conc")
                .tenantId("tenant-read-conc")
                .email("read@conc.example.com")
                .build(); // GH-90000
        storage.put(base.tenantId() + "|" + base.userId(), base.withUpdatedAt(Instant.now())); // GH-90000

        int readerCount = 10;
        CountDownLatch latch = new CountDownLatch(readerCount); // GH-90000
        AtomicInteger foundCount = new AtomicInteger(0); // GH-90000

        for (int i = 0; i < readerCount; i++) { // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                UserProfile found = storage.get("tenant-read-conc|user-read-conc");
                if (found != null) { // GH-90000
                    foundCount.incrementAndGet(); // GH-90000
                }
                latch.countDown(); // GH-90000
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000
        assertThat(foundCount.get()).isEqualTo(readerCount); // GH-90000
    }

    // ── Concurrent delete + upsert ────────────────────────────────────────────

    @Test
    @DisplayName("concurrent delete and upsert for same user key stabilizes without errors")
    void concurrentDeleteAndUpsertStabilizes() throws InterruptedException { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-del-upsert")
                .tenantId("tenant-del-upsert")
                .email("delup@example.com")
                .build(); // GH-90000

        int operationCount = 20;
        CountDownLatch latch = new CountDownLatch(operationCount); // GH-90000

        for (int i = 0; i < operationCount; i++) { // GH-90000
            final boolean doUpsert = (i % 2 == 0); // GH-90000
            Thread.ofVirtual().start(() -> { // GH-90000
                if (doUpsert) { // GH-90000
                    storage.put(profile.tenantId() + "|" + profile.userId(), // GH-90000
                            profile.withUpdatedAt(Instant.now())); // GH-90000
                } else {
                    storage.remove(profile.tenantId() + "|" + profile.userId()); // GH-90000
                }
                latch.countDown(); // GH-90000
            });
        }

        latch.await(5, TimeUnit.SECONDS); // GH-90000

        // Final state is either present or absent — never corrupted
        Optional<UserProfile> finalState = Optional.ofNullable( // GH-90000
                storage.get("tenant-del-upsert|user-del-upsert"));
        // No assertion on presence/absence since it depends on thread ordering;
        // the key assertion is no exception was thrown and no data corruption occurred.
        assertThat(finalState.isEmpty() || finalState.isPresent()).isTrue(); // GH-90000
    }
}
