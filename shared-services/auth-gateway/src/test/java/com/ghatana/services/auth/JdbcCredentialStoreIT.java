/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.services.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JdbcCredentialStore} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for JdbcCredentialStore with Testcontainers
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("JdbcCredentialStore — integration tests with PostgreSQL")
class JdbcCredentialStoreIT extends EventloopTestBase {

    @Override
    protected boolean breakOnFatalError() {
        return false;
    }

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("auth_test")
                    .withUsername("auth")
                    .withPassword("auth");

    private HikariDataSource dataSource;
    private JdbcCredentialStore store;

    @BeforeEach
    void setUp() { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        store = new JdbcCredentialStore(dataSource); // GH-90000
        store.ensureSchema(); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (var conn = dataSource.getConnection(); // GH-90000
             var stmt = conn.createStatement()) { // GH-90000
            stmt.execute("TRUNCATE TABLE auth_users");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("findByUsername returns empty for non-existent user")
    void findByUsername_nonExistent_returnsEmpty() { // GH-90000
        Optional<CredentialStore.StoredUser> result =
                runPromise(() -> store.findByUsername("ghost"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("createUser persists user and findByUsername retrieves it")
    void createUser_thenFind_returnsStoredUser() { // GH-90000
        CredentialStore.StoredUser created = runPromise(() -> // GH-90000
                store.createUser("alice", "hash$1", "alice@example.com", // GH-90000
                        List.of("USER"), "tenant-1"));

        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.email()).isEqualTo("alice@example.com");
        assertThat(created.tenantId()).isEqualTo("tenant-1");
        assertThat(created.enabled()).isTrue(); // GH-90000
        assertThat(created.roles()).containsExactly("USER");

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("alice"));
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("createUser with multiple roles preserves all roles")
    void createUser_multipleRoles_allRolesStored() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("bob", "hash$2", "bob@example.com", // GH-90000
                        List.of("USER", "ADMIN", "MODERATOR"), "tenant-2")); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("bob"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN", "MODERATOR"); // GH-90000
    }

    @Test
    @DisplayName("createUser for duplicate username throws IllegalStateException")
    void createUser_duplicate_throwsIllegalStateException() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("carol", "hash$3", "carol@example.com", // GH-90000
                        List.of("USER"), "tenant-1"));

        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.createUser("carol", "hash$4", "carol2@example.com", // GH-90000
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("carol");

        clearFatalError(); // Clear the expected fatal error from the eventloop
    }

    @Test
    @DisplayName("findByUsername returns empty roles correctly for empty roles list")
    void createUser_emptyRoles_storedAndRetrieved() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("dave", "hash$5", "dave@example.com", // GH-90000
                        List.of(), "tenant-3")); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("dave"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().roles()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("findByUsername returns user with correct tenantId")
    void createUser_tenantIsolation_tenantIdPreserved() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("eve", "hash$6", "eve@example.com", // GH-90000
                        List.of("USER"), "tenant-xyz"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("eve"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().tenantId()).isEqualTo("tenant-xyz");
    }

    @Test
    @DisplayName("ensureSchema is idempotent — safe to call multiple times")
    void ensureSchema_idempotent_noException() { // GH-90000
        store.ensureSchema(); // GH-90000
        store.ensureSchema(); // third call total — must not throw // GH-90000
    }
}
