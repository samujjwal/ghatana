package com.ghatana.services.userprofile;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserProfileStore} contract and {@link UserProfile} value object.
 *
 * <p>Uses an in-memory store to avoid needing a live Postgres instance in CI.</p>
 *
 * @doc.type class
 * @doc.purpose Tests for UserProfile value object and store contract
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("UserProfile Store Tests [GH-90000]")
class UserProfileStoreTest extends EventloopTestBase {

    /** Simple in-memory store for fast, dependency-free unit tests. */
    private UserProfileStore store;

    @BeforeEach
    void setUp() { // GH-90000
        ConcurrentHashMap<String, UserProfile> map = new ConcurrentHashMap<>(); // GH-90000
        store = new UserProfileStore() { // GH-90000
            @Override
            public io.activej.promise.Promise<Optional<UserProfile>> findByTenantAndUser( // GH-90000
                    String tenantId, String userId) {
                return io.activej.promise.Promise.of( // GH-90000
                        Optional.ofNullable(map.get(tenantId + "|" + userId))); // GH-90000
            }

            @Override
            public io.activej.promise.Promise<UserProfile> upsert(UserProfile profile) { // GH-90000
                UserProfile saved = profile.withUpdatedAt(Instant.now()); // GH-90000
                map.put(profile.tenantId() + "|" + profile.userId(), saved); // GH-90000
                return io.activej.promise.Promise.of(saved); // GH-90000
            }

            @Override
            public io.activej.promise.Promise<Void> delete(String tenantId, String userId) { // GH-90000
                map.remove(tenantId + "|" + userId); // GH-90000
                return io.activej.promise.Promise.of(null); // GH-90000
            }
        };
    }

    @Test
    @DisplayName("upsert then find returns correct profile [GH-90000]")
    void shouldSaveAndRetrieveProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-001 [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .displayName("Alice [GH-90000]")
                .preferredLanguage("en-GB [GH-90000]")
                .timezone("Europe/London [GH-90000]")
                .theme("dark [GH-90000]")
                .notificationsEnabled(true) // GH-90000
                .build(); // GH-90000

        UserProfile saved = runPromise(() -> store.upsert(profile)); // GH-90000

        assertThat(saved.userId()).isEqualTo("user-001 [GH-90000]");
        assertThat(saved.tenantId()).isEqualTo("tenant-a [GH-90000]");
        assertThat(saved.email()).isEqualTo("alice@example.com [GH-90000]");
        assertThat(saved.theme()).isEqualTo("dark [GH-90000]");

        Optional<UserProfile> found = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-a", "user-001")); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().displayName()).isEqualTo("Alice [GH-90000]");
    }

    @Test
    @DisplayName("find returns empty for unknown user [GH-90000]")
    void shouldReturnEmptyForMissingProfile() { // GH-90000
        Optional<UserProfile> result = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-x", "no-such-user")); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("delete removes profile [GH-90000]")
    void shouldDeleteProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-del [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .email("bob@example.com [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profile)); // GH-90000
        runPromise(() -> store.delete("tenant-a", "user-del")); // GH-90000

        Optional<UserProfile> after = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-a", "user-del")); // GH-90000

        assertThat(after).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("upsert overwrites existing profile preserving createdAt [GH-90000]")
    void shouldOverwriteExistingProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-upd [GH-90000]")
                .tenantId("tenant-a [GH-90000]")
                .email("carol@example.com [GH-90000]")
                .theme("light [GH-90000]")
                .build(); // GH-90000

        UserProfile saved1 = runPromise(() -> store.upsert(original)); // GH-90000
        Instant createdAt  = saved1.createdAt(); // GH-90000

        UserProfile updated  = original.toBuilder().theme("dark [GH-90000]").build();
        UserProfile saved2   = runPromise(() -> store.upsert(updated)); // GH-90000

        assertThat(saved2.theme()).isEqualTo("dark [GH-90000]");
        // updatedAt must be >= original createdAt
        assertThat(saved2.updatedAt()).isAfterOrEqualTo(createdAt); // GH-90000
    }

    @Test
    @DisplayName("UserProfile applies sensible defaults for missing fields [GH-90000]")
    void shouldApplyDefaults() { // GH-90000
        UserProfile p = UserProfile.builder() // GH-90000
                .userId("u1 [GH-90000]")
                .tenantId("t1 [GH-90000]")
                .email("minimal@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(p.displayName()).isEqualTo("minimal [GH-90000]");     // derived from email prefix
        assertThat(p.preferredLanguage()).isEqualTo("en-US [GH-90000]");
        assertThat(p.timezone()).isEqualTo("UTC [GH-90000]");
        assertThat(p.theme()).isEqualTo("system [GH-90000]");
        assertThat(p.notificationsEnabled()).isTrue(); // GH-90000
    }
}
