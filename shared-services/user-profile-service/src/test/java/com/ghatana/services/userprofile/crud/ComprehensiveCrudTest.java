package com.ghatana.services.userprofile.crud;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.services.userprofile.UserProfile;
import com.ghatana.services.userprofile.UserProfileStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive CRUD tests for {@link UserProfileStore} using an in-memory store.
 *
 * <p>Verifies full create, read, update, and delete lifecycle for all UserProfile fields.</p>
 *
 * @doc.type    class
 * @doc.purpose Comprehensive CRUD test coverage for UserProfileStore contract
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("Comprehensive UserProfile CRUD Tests [GH-90000]")
class ComprehensiveCrudTest extends EventloopTestBase {

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

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert creates a new profile and returns it with updatedAt set [GH-90000]")
    void upsertCreatesProfileWithUpdatedAt() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-crud-001 [GH-90000]")
                .tenantId("tenant-crud [GH-90000]")
                .email("crud@example.com [GH-90000]")
                .build(); // GH-90000

        UserProfile saved = runPromise(() -> store.upsert(profile)); // GH-90000

        assertThat(saved.userId()).isEqualTo("user-crud-001 [GH-90000]");
        assertThat(saved.tenantId()).isEqualTo("tenant-crud [GH-90000]");
        assertThat(saved.email()).isEqualTo("crud@example.com [GH-90000]");
        assertThat(saved.updatedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("upsert stores all optional fields correctly [GH-90000]")
    void upsertStoresAllOptionalFields() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-full-001 [GH-90000]")
                .tenantId("tenant-full [GH-90000]")
                .email("full@example.com [GH-90000]")
                .displayName("Alice Smith [GH-90000]")
                .avatarUrl("https://cdn.example.com/alice.png [GH-90000]")
                .preferredLanguage("fr-FR [GH-90000]")
                .timezone("Europe/Paris [GH-90000]")
                .theme("dark [GH-90000]")
                .notificationsEnabled(false) // GH-90000
                .build(); // GH-90000

        UserProfile saved = runPromise(() -> store.upsert(profile)); // GH-90000

        assertThat(saved.displayName()).isEqualTo("Alice Smith [GH-90000]");
        assertThat(saved.avatarUrl()).isEqualTo("https://cdn.example.com/alice.png [GH-90000]");
        assertThat(saved.preferredLanguage()).isEqualTo("fr-FR [GH-90000]");
        assertThat(saved.timezone()).isEqualTo("Europe/Paris [GH-90000]");
        assertThat(saved.theme()).isEqualTo("dark [GH-90000]");
        assertThat(saved.notificationsEnabled()).isFalse(); // GH-90000
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByTenantAndUser returns empty Optional when profile does not exist [GH-90000]")
    void findReturnsEmptyWhenMissing() { // GH-90000
        Optional<UserProfile> result = runPromise(() -> // GH-90000
                store.findByTenantAndUser("non-existent-tenant", "user-xyz")); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByTenantAndUser returns the profile after upsert [GH-90000]")
    void findReturnsSavedProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-find-001 [GH-90000]")
                .tenantId("tenant-find [GH-90000]")
                .email("find@example.com [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profile)); // GH-90000

        Optional<UserProfile> found = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-find", "user-find-001")); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().email()).isEqualTo("find@example.com [GH-90000]");
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert updates an existing profile by replacing stored values [GH-90000]")
    void upsertUpdatesExistingProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-update-001 [GH-90000]")
                .tenantId("tenant-update [GH-90000]")
                .email("original@example.com [GH-90000]")
                .displayName("Original Name [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> store.upsert(original)); // GH-90000

        UserProfile updated = original.toBuilder() // GH-90000
                .email("updated@example.com [GH-90000]")
                .displayName("Updated Name [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> store.upsert(updated)); // GH-90000

        Optional<UserProfile> found = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-update", "user-update-001")); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().email()).isEqualTo("updated@example.com [GH-90000]");
        assertThat(found.get().displayName()).isEqualTo("Updated Name [GH-90000]");
    }

    @Test
    @DisplayName("upsert updates the updatedAt timestamp on each call [GH-90000]")
    void upsertAdvancesUpdatedAt() throws InterruptedException { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-ts-001 [GH-90000]")
                .tenantId("tenant-ts [GH-90000]")
                .email("ts@example.com [GH-90000]")
                .build(); // GH-90000

        UserProfile first = runPromise(() -> store.upsert(profile)); // GH-90000

        Thread.sleep(5); // ensure at least 1 ms passes // GH-90000

        UserProfile second = runPromise(() -> store.upsert(profile)); // GH-90000

        assertThat(second.updatedAt()).isAfterOrEqualTo(first.updatedAt()); // GH-90000
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete removes the profile so findByTenantAndUser returns empty [GH-90000]")
    void deleteRemovesProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-del-001 [GH-90000]")
                .tenantId("tenant-del [GH-90000]")
                .email("delete@example.com [GH-90000]")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profile)); // GH-90000
        runPromise(() -> store.delete("tenant-del", "user-del-001")); // GH-90000

        Optional<UserProfile> found = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-del", "user-del-001")); // GH-90000

        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("delete is a no-op when profile does not exist [GH-90000]")
    void deleteIsNoOpWhenMissing() { // GH-90000
        // Should not throw
        runPromise(() -> store.delete("no-tenant", "no-user")); // GH-90000

        // Store is still functional
        assertThat(storage).isEmpty(); // GH-90000
    }

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    @DisplayName("profile uses email-derived displayName when displayName is not set [GH-90000]")
    void profileDefaultsDisplayNameFromEmail() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-defaults-001 [GH-90000]")
                .tenantId("tenant-defaults [GH-90000]")
                .email("defaults@example.com [GH-90000]")
                .build(); // GH-90000

        // Default displayName is derived from the email local-part
        assertThat(profile.displayName()).isNotNull(); // GH-90000
        assertThat(profile.displayName()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("profile defaults preferredLanguage to en-US [GH-90000]")
    void profileDefaultsPreferredLanguage() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-lang-001 [GH-90000]")
                .tenantId("tenant-lang [GH-90000]")
                .email("lang@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo("en-US [GH-90000]");
    }

    @Test
    @DisplayName("profile defaults timezone to UTC [GH-90000]")
    void profileDefaultsTimezone() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-tz-001 [GH-90000]")
                .tenantId("tenant-tz [GH-90000]")
                .email("tz@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.timezone()).isEqualTo("UTC [GH-90000]");
    }

    @Test
    @DisplayName("profile defaults theme to system [GH-90000]")
    void profileDefaultsTheme() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-theme-001 [GH-90000]")
                .tenantId("tenant-theme [GH-90000]")
                .email("theme@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo("system [GH-90000]");
    }

    // ── Multiple profiles ─────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple profiles across different users can be stored and retrieved [GH-90000]")
    void multipleDifferentUsersInSameTenant() { // GH-90000
        for (int i = 1; i <= 5; i++) { // GH-90000
            UserProfile p = UserProfile.builder() // GH-90000
                    .userId("user-" + i) // GH-90000
                    .tenantId("shared-tenant [GH-90000]")
                    .email("user" + i + "@example.com") // GH-90000
                    .build(); // GH-90000
            runPromise(() -> store.upsert(p)); // GH-90000
        }

        for (int i = 1; i <= 5; i++) { // GH-90000
            final int idx = i;
            Optional<UserProfile> found = runPromise(() -> // GH-90000
                    store.findByTenantAndUser("shared-tenant", "user-" + idx)); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().email()).isEqualTo("user" + idx + "@example.com"); // GH-90000
        }
    }
}
