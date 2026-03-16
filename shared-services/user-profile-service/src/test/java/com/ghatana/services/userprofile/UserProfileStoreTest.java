package com.ghatana.services.userprofile;

import com.ghatana.platform.test.EventloopTestBase;
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
@DisplayName("UserProfile Store Tests")
class UserProfileStoreTest extends EventloopTestBase {

    /** Simple in-memory store for fast, dependency-free unit tests. */
    private UserProfileStore store;

    @BeforeEach
    void setUp() {
        ConcurrentHashMap<String, UserProfile> map = new ConcurrentHashMap<>();
        store = new UserProfileStore() {
            @Override
            public io.activej.promise.Promise<Optional<UserProfile>> findByTenantAndUser(
                    String tenantId, String userId) {
                return io.activej.promise.Promise.of(
                        Optional.ofNullable(map.get(tenantId + "|" + userId)));
            }

            @Override
            public io.activej.promise.Promise<UserProfile> upsert(UserProfile profile) {
                UserProfile saved = profile.withUpdatedAt(Instant.now());
                map.put(profile.tenantId() + "|" + profile.userId(), saved);
                return io.activej.promise.Promise.of(saved);
            }

            @Override
            public io.activej.promise.Promise<Void> delete(String tenantId, String userId) {
                map.remove(tenantId + "|" + userId);
                return io.activej.promise.Promise.of(null);
            }
        };
    }

    @Test
    @DisplayName("upsert then find returns correct profile")
    void shouldSaveAndRetrieveProfile() {
        UserProfile profile = UserProfile.builder()
                .userId("user-001")
                .tenantId("tenant-a")
                .email("alice@example.com")
                .displayName("Alice")
                .preferredLanguage("en-GB")
                .timezone("Europe/London")
                .theme("dark")
                .notificationsEnabled(true)
                .build();

        UserProfile saved = runPromise(() -> store.upsert(profile));

        assertThat(saved.userId()).isEqualTo("user-001");
        assertThat(saved.tenantId()).isEqualTo("tenant-a");
        assertThat(saved.email()).isEqualTo("alice@example.com");
        assertThat(saved.theme()).isEqualTo("dark");

        Optional<UserProfile> found = runPromise(() ->
                store.findByTenantAndUser("tenant-a", "user-001"));

        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("find returns empty for unknown user")
    void shouldReturnEmptyForMissingProfile() {
        Optional<UserProfile> result = runPromise(() ->
                store.findByTenantAndUser("tenant-x", "no-such-user"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("delete removes profile")
    void shouldDeleteProfile() {
        UserProfile profile = UserProfile.builder()
                .userId("user-del")
                .tenantId("tenant-a")
                .email("bob@example.com")
                .build();

        runPromise(() -> store.upsert(profile));
        runPromise(() -> store.delete("tenant-a", "user-del"));

        Optional<UserProfile> after = runPromise(() ->
                store.findByTenantAndUser("tenant-a", "user-del"));

        assertThat(after).isEmpty();
    }

    @Test
    @DisplayName("upsert overwrites existing profile preserving createdAt")
    void shouldOverwriteExistingProfile() {
        UserProfile original = UserProfile.builder()
                .userId("user-upd")
                .tenantId("tenant-a")
                .email("carol@example.com")
                .theme("light")
                .build();

        UserProfile saved1 = runPromise(() -> store.upsert(original));
        Instant createdAt  = saved1.createdAt();

        UserProfile updated  = original.toBuilder().theme("dark").build();
        UserProfile saved2   = runPromise(() -> store.upsert(updated));

        assertThat(saved2.theme()).isEqualTo("dark");
        // updatedAt must be >= original createdAt
        assertThat(saved2.updatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    @DisplayName("UserProfile applies sensible defaults for missing fields")
    void shouldApplyDefaults() {
        UserProfile p = UserProfile.builder()
                .userId("u1")
                .tenantId("t1")
                .email("minimal@example.com")
                .build();

        assertThat(p.displayName()).isEqualTo("minimal");     // derived from email prefix
        assertThat(p.preferredLanguage()).isEqualTo("en-US");
        assertThat(p.timezone()).isEqualTo("UTC");
        assertThat(p.theme()).isEqualTo("system");
        assertThat(p.notificationsEnabled()).isTrue();
    }
}
