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
    void setUp() { // GH-90000
        store = new InMemoryCredentialStore(); // GH-90000
    }

    @Test
    @DisplayName("findByUsername returns empty Optional for unknown user")
    void findByUsernameReturnsEmptyForUnknownUser() { // GH-90000
        Optional<CredentialStore.StoredUser> result =
                runPromise(() -> store.findByUsername("unknown-user"));

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("createUser stores a user and findByUsername retrieves it")
    void createAndFindUser() { // GH-90000
        String hash = PasswordHasher.hash("secret123");

        CredentialStore.StoredUser created = runPromise(() -> // GH-90000
                store.createUser("alice", hash, "alice@example.com", // GH-90000
                        List.of("USER"), "tenant-1"));

        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.email()).isEqualTo("alice@example.com");
        assertThat(created.roles()).containsExactly("USER");
        assertThat(created.tenantId()).isEqualTo("tenant-1");
        assertThat(created.enabled()).isTrue(); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("alice"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findByUsername is case-insensitive")
    void findByUsernameIsCaseInsensitive() { // GH-90000
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("BOB", hash, "bob@example.com", // GH-90000
                List.of("USER"), "tenant-1"));

        Optional<CredentialStore.StoredUser> foundLower =
                runPromise(() -> store.findByUsername("bob"));
        Optional<CredentialStore.StoredUser> foundUpper =
                runPromise(() -> store.findByUsername("BOB"));

        assertThat(foundLower).isPresent(); // GH-90000
        assertThat(foundUpper).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("createUser throws when user already exists")
    void createUserThrowsForDuplicate() { // GH-90000
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("charlie", hash, "charlie@example.com", // GH-90000
                List.of("USER"), "tenant-1"));

        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                store.createUser("charlie", hash, "charlie2@example.com", // GH-90000
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("charlie");
    }

    @Test
    @DisplayName("createUser is case-insensitive for duplicate detection")
    void createUserIsCaseInsensitiveForDuplicateDetection() { // GH-90000
        String hash = PasswordHasher.hash("secret");
        runPromise(() -> store.createUser("dave", hash, "dave@example.com", // GH-90000
                List.of("USER"), "tenant-1"));

        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                store.createUser("DAVE", hash, "dave2@example.com", // GH-90000
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    @Test
    @DisplayName("seedAdmin stores admin user accessible via findByUsername")
    void seedAdminStoresAdminUser() { // GH-90000
        String hash = PasswordHasher.hash("adminPassword");
        store.seedAdmin("admin", hash, "tenant-root"); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("admin"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().roles()).contains("ADMIN", "USER"); // GH-90000
        assertThat(found.get().tenantId()).isEqualTo("tenant-root");
    }

    @Test
    @DisplayName("seedAdmin does not overwrite existing admin")
    void seedAdminDoesNotOverwriteExisting() { // GH-90000
        String firstHash = PasswordHasher.hash("first");
        String secondHash = PasswordHasher.hash("second");

        store.seedAdmin("admin", firstHash, "tenant-root"); // GH-90000
        store.seedAdmin("admin", secondHash, "tenant-root"); // Should be ignored // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("admin"));

        assertThat(found.get().passwordHash()).isEqualTo(firstHash); // GH-90000
    }

    @Test
    @DisplayName("findByUsername throws NullPointerException for null username")
    void findByUsernameThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> runPromise(() -> store.findByUsername(null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
