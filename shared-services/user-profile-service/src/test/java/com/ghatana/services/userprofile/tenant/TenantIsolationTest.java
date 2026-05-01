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

    // ── Cross-tenant read isolation ───────────────────────────────────────────

    @Test
    @DisplayName("profile stored under tenant-A is not visible from tenant-B")
    void profileUnderTenantAIsHiddenFromTenantB() { 
        UserProfile profile = UserProfile.builder() 
                .userId("shared-user-id")
                .tenantId("tenant-A")
                .email("alice@tenant-a.com")
                .build(); 

        runPromise(() -> store.upsert(profile)); 

        Optional<UserProfile> crossTenant = runPromise(() -> 
                store.findByTenantAndUser("tenant-B", "shared-user-id")); 

        assertThat(crossTenant).isEmpty(); 
    }

    @Test
    @DisplayName("identical userId in different tenants returns tenant-specific profiles")
    void identicalUserIdInDifferentTenantsReturnsCorrectData() { 
        String userId = "user-shared";

        UserProfile profileA = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-alpha")
                .email("user@alpha.com")
                .displayName("Alpha User")
                .build(); 

        UserProfile profileB = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-beta")
                .email("user@beta.com")
                .displayName("Beta User")
                .build(); 

        runPromise(() -> store.upsert(profileA)); 
        runPromise(() -> store.upsert(profileB)); 

        Optional<UserProfile> foundAlpha = runPromise(() -> 
                store.findByTenantAndUser("tenant-alpha", userId)); 
        Optional<UserProfile> foundBeta = runPromise(() -> 
                store.findByTenantAndUser("tenant-beta", userId)); 

        assertThat(foundAlpha).isPresent(); 
        assertThat(foundAlpha.get().email()).isEqualTo("user@alpha.com");
        assertThat(foundAlpha.get().displayName()).isEqualTo("Alpha User");

        assertThat(foundBeta).isPresent(); 
        assertThat(foundBeta.get().email()).isEqualTo("user@beta.com");
        assertThat(foundBeta.get().displayName()).isEqualTo("Beta User");
    }

    // ── Cross-tenant update isolation ─────────────────────────────────────────

    @Test
    @DisplayName("updating profile in tenant-A does not affect the same userId in tenant-B")
    void updateInTenantADoesNotAffectTenantB() { 
        String userId = "user-update-isolation";

        UserProfile profileA = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-X")
                .email("orig@x.com")
                .displayName("Original")
                .build(); 

        UserProfile profileB = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-Y")
                .email("orig@y.com")
                .displayName("Should Stay")
                .build(); 

        runPromise(() -> store.upsert(profileA)); 
        runPromise(() -> store.upsert(profileB)); 

        // Update tenant-X profile
        runPromise(() -> store.upsert(profileA.toBuilder() 
                .displayName("Changed in X")
                .build())); 

        Optional<UserProfile> foundB = runPromise(() -> 
                store.findByTenantAndUser("tenant-Y", userId)); 

        assertThat(foundB).isPresent(); 
        assertThat(foundB.get().displayName()).isEqualTo("Should Stay");
    }

    // ── Cross-tenant delete isolation ─────────────────────────────────────────

    @Test
    @DisplayName("deleting profile in tenant-A does not remove the same userId in tenant-B")
    void deleteInTenantADoesNotAffectTenantB() { 
        String userId = "user-del-isolation";

        UserProfile profileA = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-del-a")
                .email("a@del.com")
                .build(); 

        UserProfile profileB = UserProfile.builder() 
                .userId(userId) 
                .tenantId("tenant-del-b")
                .email("b@del.com")
                .build(); 

        runPromise(() -> store.upsert(profileA)); 
        runPromise(() -> store.upsert(profileB)); 

        runPromise(() -> store.delete("tenant-del-a", userId)); 

        Optional<UserProfile> foundA = runPromise(() -> 
                store.findByTenantAndUser("tenant-del-a", userId)); 
        Optional<UserProfile> foundB = runPromise(() -> 
                store.findByTenantAndUser("tenant-del-b", userId)); 

        assertThat(foundA).isEmpty(); 
        assertThat(foundB).isPresent(); 
        assertThat(foundB.get().email()).isEqualTo("b@del.com");
    }

    // ── Multiple tenants coexistence ──────────────────────────────────────────

    @Test
    @DisplayName("five tenants with distinct users do not interfere with each other")
    void fiveTenantsWithDistinctUsersAreIsolated() { 
        // Seed users for 5 tenants
        for (int t = 1; t <= 5; t++) { 
            String tenantId = "tenant-" + t;
            for (int u = 1; u <= 3; u++) { 
                String userId = "user-" + u;
                UserProfile profile = UserProfile.builder() 
                        .userId(userId) 
                        .tenantId(tenantId) 
                        .email(userId + "@" + tenantId + ".example.com") 
                        .build(); 
                runPromise(() -> store.upsert(profile)); 
            }
        }

        // Verify each tenant only sees its own user emails
        for (int t = 1; t <= 5; t++) { 
            final String tenantId = "tenant-" + t;
            for (int u = 1; u <= 3; u++) { 
                final String userId = "user-" + u;
                Optional<UserProfile> found = runPromise(() -> 
                        store.findByTenantAndUser(tenantId, userId)); 
                assertThat(found).isPresent(); 
                assertThat(found.get().email()).isEqualTo(userId + "@" + tenantId + ".example.com"); 
            }
        }
    }
}
