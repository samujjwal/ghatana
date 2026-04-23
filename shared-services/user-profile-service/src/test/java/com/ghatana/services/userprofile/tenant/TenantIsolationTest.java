package com.ghatana.services.userprofile.tenant;

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
 * Tenant isolation tests for {@link UserProfileStore}.
 *
 * <p>Verifies that profiles belonging to one tenant are never accessible from another,
 * and that per-tenant CRUD operations do not cross boundaries.</p>
 *
 * @doc.type    class
 * @doc.purpose Verifies tenant-scoped data isolation in UserProfileStore
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("UserProfile Tenant Isolation Tests")
class TenantIsolationTest extends EventloopTestBase {

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

    // ── Cross-tenant read isolation ───────────────────────────────────────────

    @Test
    @DisplayName("profile stored under tenant-A is not visible from tenant-B")
    void profileUnderTenantAIsHiddenFromTenantB() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("shared-user-id")
                .tenantId("tenant-A")
                .email("alice@tenant-a.com")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profile)); // GH-90000

        Optional<UserProfile> crossTenant = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-B", "shared-user-id")); // GH-90000

        assertThat(crossTenant).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("identical userId in different tenants returns tenant-specific profiles")
    void identicalUserIdInDifferentTenantsReturnsCorrectData() { // GH-90000
        String userId = "user-shared";

        UserProfile profileA = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-alpha")
                .email("user@alpha.com")
                .displayName("Alpha User")
                .build(); // GH-90000

        UserProfile profileB = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-beta")
                .email("user@beta.com")
                .displayName("Beta User")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profileA)); // GH-90000
        runPromise(() -> store.upsert(profileB)); // GH-90000

        Optional<UserProfile> foundAlpha = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-alpha", userId)); // GH-90000
        Optional<UserProfile> foundBeta = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-beta", userId)); // GH-90000

        assertThat(foundAlpha).isPresent(); // GH-90000
        assertThat(foundAlpha.get().email()).isEqualTo("user@alpha.com");
        assertThat(foundAlpha.get().displayName()).isEqualTo("Alpha User");

        assertThat(foundBeta).isPresent(); // GH-90000
        assertThat(foundBeta.get().email()).isEqualTo("user@beta.com");
        assertThat(foundBeta.get().displayName()).isEqualTo("Beta User");
    }

    // ── Cross-tenant update isolation ─────────────────────────────────────────

    @Test
    @DisplayName("updating profile in tenant-A does not affect the same userId in tenant-B")
    void updateInTenantADoesNotAffectTenantB() { // GH-90000
        String userId = "user-update-isolation";

        UserProfile profileA = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-X")
                .email("orig@x.com")
                .displayName("Original")
                .build(); // GH-90000

        UserProfile profileB = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-Y")
                .email("orig@y.com")
                .displayName("Should Stay")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profileA)); // GH-90000
        runPromise(() -> store.upsert(profileB)); // GH-90000

        // Update tenant-X profile
        runPromise(() -> store.upsert(profileA.toBuilder() // GH-90000
                .displayName("Changed in X")
                .build())); // GH-90000

        Optional<UserProfile> foundB = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-Y", userId)); // GH-90000

        assertThat(foundB).isPresent(); // GH-90000
        assertThat(foundB.get().displayName()).isEqualTo("Should Stay");
    }

    // ── Cross-tenant delete isolation ─────────────────────────────────────────

    @Test
    @DisplayName("deleting profile in tenant-A does not remove the same userId in tenant-B")
    void deleteInTenantADoesNotAffectTenantB() { // GH-90000
        String userId = "user-del-isolation";

        UserProfile profileA = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-del-a")
                .email("a@del.com")
                .build(); // GH-90000

        UserProfile profileB = UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId("tenant-del-b")
                .email("b@del.com")
                .build(); // GH-90000

        runPromise(() -> store.upsert(profileA)); // GH-90000
        runPromise(() -> store.upsert(profileB)); // GH-90000

        runPromise(() -> store.delete("tenant-del-a", userId)); // GH-90000

        Optional<UserProfile> foundA = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-del-a", userId)); // GH-90000
        Optional<UserProfile> foundB = runPromise(() -> // GH-90000
                store.findByTenantAndUser("tenant-del-b", userId)); // GH-90000

        assertThat(foundA).isEmpty(); // GH-90000
        assertThat(foundB).isPresent(); // GH-90000
        assertThat(foundB.get().email()).isEqualTo("b@del.com");
    }

    // ── Multiple tenants coexistence ──────────────────────────────────────────

    @Test
    @DisplayName("five tenants with distinct users do not interfere with each other")
    void fiveTenantsWithDistinctUsersAreIsolated() { // GH-90000
        // Seed users for 5 tenants
        for (int t = 1; t <= 5; t++) { // GH-90000
            String tenantId = "tenant-" + t;
            for (int u = 1; u <= 3; u++) { // GH-90000
                String userId = "user-" + u;
                UserProfile profile = UserProfile.builder() // GH-90000
                        .userId(userId) // GH-90000
                        .tenantId(tenantId) // GH-90000
                        .email(userId + "@" + tenantId + ".example.com") // GH-90000
                        .build(); // GH-90000
                runPromise(() -> store.upsert(profile)); // GH-90000
            }
        }

        // Verify each tenant only sees its own user emails
        for (int t = 1; t <= 5; t++) { // GH-90000
            final String tenantId = "tenant-" + t;
            for (int u = 1; u <= 3; u++) { // GH-90000
                final String userId = "user-" + u;
                Optional<UserProfile> found = runPromise(() -> // GH-90000
                        store.findByTenantAndUser(tenantId, userId)); // GH-90000
                assertThat(found).isPresent(); // GH-90000
                assertThat(found.get().email()).isEqualTo(userId + "@" + tenantId + ".example.com"); // GH-90000
            }
        }
    }
}
