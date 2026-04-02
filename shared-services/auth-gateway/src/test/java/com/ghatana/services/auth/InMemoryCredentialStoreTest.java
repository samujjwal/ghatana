package com.ghatana.services.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryCredentialStore user CRUD and lookup behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryCredentialStore — user creation, lookup, and duplicate detection")
class InMemoryCredentialStoreTest extends EventloopTestBase {

    private InMemoryCredentialStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCredentialStore();
    }

    @Test
    @DisplayName("findByUsername returns empty Optional for unknown user")
    void findByUsernameReturnsEmptyForUnknownUser() {
        Optional<CredentialStore.StoredUser> result =
                runPromise(() -> store.findByUsername("unknown-user"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createUser stores a user and findByUsername retrieves it")
    void createAndFindUser() {
        String hash = PasswordHasher.hash("secret123");

        CredentialStore.StoredUser created = runPromise(() ->
                store.createUser("alice", hash, "alice@example.com",
                        List.of("USER"), "tenant-1"));

        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.email()).isEqualTo("alice@example.com");
        assertThat(created.roles()).containsExactly("USER");
        assertThat(created.tenantId()).isEqualTo("tenant-1");
        assertThat(created.enabled()).isTrue();

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("alice"));

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findByUsername is case-insensitive")
    void findByUsernameIsCaseInsensitive() {
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("BOB", hash, "bob@example.com",
                List.of("USER"), "tenant-1"));

        Optional<CredentialStore.StoredUser> foundLower =
                runPromise(() -> store.findByUsername("bob"));
        Optional<CredentialStore.StoredUser> foundUpper =
                runPromise(() -> store.findByUsername("BOB"));

        assertThat(foundLower).isPresent();
        assertThat(foundUpper).isPresent();
    }

    @Test
    @DisplayName("createUser throws when user already exists")
    void createUserThrowsForDuplicate() {
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("charlie", hash, "charlie@example.com",
                List.of("USER"), "tenant-1"));

        assertThatThrownBy(() -> runPromise(() ->
                store.createUser("charlie", hash, "charlie2@example.com",
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("charlie");
    }

    @Test
    @DisplayName("createUser is case-insensitive for duplicate detection")
    void createUserIsCaseInsensitiveForDuplicateDetection() {
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("dave", hash, "dave@example.com",
                List.of("USER"), "tenant-1"));

        assertThatThrownBy(() -> runPromise(() ->
                store.createUser("DAVE", hash, "dave2@example.com",
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("seedAdmin stores admin user accessible via findByUsername")
    void seedAdminStoresAdminUser() {
        String hash = PasswordHasher.hash("adminPassword");
        store.seedAdmin("admin", hash, "tenant-root");

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("admin"));

        assertThat(found).isPresent();
        assertThat(found.get().roles()).contains("ADMIN", "USER");
        assertThat(found.get().tenantId()).isEqualTo("tenant-root");
    }

    @Test
    @DisplayName("seedAdmin does not overwrite existing admin")
    void seedAdminDoesNotOverwriteExisting() {
        String firstHash = PasswordHasher.hash("first");
        String secondHash = PasswordHasher.hash("second");

        store.seedAdmin("admin", firstHash, "tenant-root");
        store.seedAdmin("admin", secondHash, "tenant-root"); // Should be ignored

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("admin"));

        assertThat(found.get().passwordHash()).isEqualTo(firstHash);
    }

    @Test
    @DisplayName("findByUsername throws NullPointerException for null username")
    void findByUsernameThrowsForNull() {
        assertThatThrownBy(() -> runPromise(() -> store.findByUsername(null)))
                .isInstanceOf(NullPointerException.class);
    }
}
