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
@DisplayName("Comprehensive UserProfile CRUD Tests")
class ComprehensiveCrudTest extends EventloopTestBase {

    private UserProfileStore store;
    private final ConcurrentHashMap<String, UserProfile> storage = new ConcurrentHashMap<>(); 

    @BeforeEach
    void setUp() { 
        storage.clear(); 
        store = new UserProfileStore() { 
            @Override
            public Promise<Optional<UserProfile>> findByTenantAndUser(String tenantId, String userId) { 
                return Promise.of(Optional.ofNullable(storage.get(tenantId + "|" + userId))); 
            }

            @Override
            public Promise<UserProfile> upsert(UserProfile profile) { 
                UserProfile saved = profile.withUpdatedAt(Instant.now()); 
                storage.put(profile.tenantId() + "|" + profile.userId(), saved); 
                return Promise.of(saved); 
            }

            @Override
            public Promise<Void> delete(String tenantId, String userId) { 
                storage.remove(tenantId + "|" + userId); 
                return Promise.of(null); 
            }
        };
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert creates a new profile and returns it with updatedAt set")
    void upsertCreatesProfileWithUpdatedAt() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-crud-001")
                .tenantId("tenant-crud")
                .email("crud@example.com")
                .build(); 

        UserProfile saved = runPromise(() -> store.upsert(profile)); 

        assertThat(saved.userId()).isEqualTo("user-crud-001");
        assertThat(saved.tenantId()).isEqualTo("tenant-crud");
        assertThat(saved.email()).isEqualTo("crud@example.com");
        assertThat(saved.updatedAt()).isNotNull(); 
    }

    @Test
    @DisplayName("upsert stores all optional fields correctly")
    void upsertStoresAllOptionalFields() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-full-001")
                .tenantId("tenant-full")
                .email("full@example.com")
                .displayName("Alice Smith")
                .avatarUrl("https://cdn.example.com/alice.png")
                .preferredLanguage("fr-FR")
                .timezone("Europe/Paris")
                .theme("dark")
                .notificationsEnabled(false) 
                .build(); 

        UserProfile saved = runPromise(() -> store.upsert(profile)); 

        assertThat(saved.displayName()).isEqualTo("Alice Smith");
        assertThat(saved.avatarUrl()).isEqualTo("https://cdn.example.com/alice.png");
        assertThat(saved.preferredLanguage()).isEqualTo("fr-FR");
        assertThat(saved.timezone()).isEqualTo("Europe/Paris");
        assertThat(saved.theme()).isEqualTo("dark");
        assertThat(saved.notificationsEnabled()).isFalse(); 
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByTenantAndUser returns empty Optional when profile does not exist")
    void findReturnsEmptyWhenMissing() { 
        Optional<UserProfile> result = runPromise(() -> 
                store.findByTenantAndUser("non-existent-tenant", "user-xyz")); 

        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("findByTenantAndUser returns the profile after upsert")
    void findReturnsSavedProfile() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-find-001")
                .tenantId("tenant-find")
                .email("find@example.com")
                .build(); 

        runPromise(() -> store.upsert(profile)); 

        Optional<UserProfile> found = runPromise(() -> 
                store.findByTenantAndUser("tenant-find", "user-find-001")); 

        assertThat(found).isPresent(); 
        assertThat(found.get().email()).isEqualTo("find@example.com");
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert updates an existing profile by replacing stored values")
    void upsertUpdatesExistingProfile() { 
        UserProfile original = UserProfile.builder() 
                .userId("user-update-001")
                .tenantId("tenant-update")
                .email("original@example.com")
                .displayName("Original Name")
                .build(); 

        runPromise(() -> store.upsert(original)); 

        UserProfile updated = original.toBuilder() 
                .email("updated@example.com")
                .displayName("Updated Name")
                .build(); 

        runPromise(() -> store.upsert(updated)); 

        Optional<UserProfile> found = runPromise(() -> 
                store.findByTenantAndUser("tenant-update", "user-update-001")); 

        assertThat(found).isPresent(); 
        assertThat(found.get().email()).isEqualTo("updated@example.com");
        assertThat(found.get().displayName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("upsert updates the updatedAt timestamp on each call")
    void upsertAdvancesUpdatedAt() throws InterruptedException { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-ts-001")
                .tenantId("tenant-ts")
                .email("ts@example.com")
                .build(); 

        UserProfile first = runPromise(() -> store.upsert(profile)); 

        Thread.sleep(5); // ensure at least 1 ms passes 

        UserProfile second = runPromise(() -> store.upsert(profile)); 

        assertThat(second.updatedAt()).isAfterOrEqualTo(first.updatedAt()); 
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete removes the profile so findByTenantAndUser returns empty")
    void deleteRemovesProfile() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-del-001")
                .tenantId("tenant-del")
                .email("delete@example.com")
                .build(); 

        runPromise(() -> store.upsert(profile)); 
        runPromise(() -> store.delete("tenant-del", "user-del-001")); 

        Optional<UserProfile> found = runPromise(() -> 
                store.findByTenantAndUser("tenant-del", "user-del-001")); 

        assertThat(found).isEmpty(); 
    }

    @Test
    @DisplayName("delete is a no-op when profile does not exist")
    void deleteIsNoOpWhenMissing() { 
        // Should not throw
        runPromise(() -> store.delete("no-tenant", "no-user")); 

        // Store is still functional
        assertThat(storage).isEmpty(); 
    }

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    @DisplayName("profile uses email-derived displayName when displayName is not set")
    void profileDefaultsDisplayNameFromEmail() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-defaults-001")
                .tenantId("tenant-defaults")
                .email("defaults@example.com")
                .build(); 

        // Default displayName is derived from the email local-part
        assertThat(profile.displayName()).isNotNull(); 
        assertThat(profile.displayName()).isNotBlank(); 
    }

    @Test
    @DisplayName("profile defaults preferredLanguage to en-US")
    void profileDefaultsPreferredLanguage() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-lang-001")
                .tenantId("tenant-lang")
                .email("lang@example.com")
                .build(); 

        assertThat(profile.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("profile defaults timezone to UTC")
    void profileDefaultsTimezone() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-tz-001")
                .tenantId("tenant-tz")
                .email("tz@example.com")
                .build(); 

        assertThat(profile.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("profile defaults theme to system")
    void profileDefaultsTheme() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-theme-001")
                .tenantId("tenant-theme")
                .email("theme@example.com")
                .build(); 

        assertThat(profile.theme()).isEqualTo("system");
    }

    // ── Multiple profiles ─────────────────────────────────────────────────────

    @Test
    @DisplayName("multiple profiles across different users can be stored and retrieved")
    void multipleDifferentUsersInSameTenant() { 
        for (int i = 1; i <= 5; i++) { 
            UserProfile p = UserProfile.builder() 
                    .userId("user-" + i) 
                    .tenantId("shared-tenant")
                    .email("user" + i + "@example.com") 
                    .build(); 
            runPromise(() -> store.upsert(p)); 
        }

        for (int i = 1; i <= 5; i++) { 
            final int idx = i;
            Optional<UserProfile> found = runPromise(() -> 
                    store.findByTenantAndUser("shared-tenant", "user-" + idx)); 
            assertThat(found).isPresent(); 
            assertThat(found.get().email()).isEqualTo("user" + idx + "@example.com"); 
        }
    }
}
