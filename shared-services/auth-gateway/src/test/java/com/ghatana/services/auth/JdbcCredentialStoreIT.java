/*
 * Copyright (c) 2026 Ghatana Inc.
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
@Testcontainers
@DisplayName("JdbcCredentialStore — integration tests with PostgreSQL")
class JdbcCredentialStoreIT extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("auth_test")
                    .withUsername("auth")
                    .withPassword("auth");

    private HikariDataSource dataSource;
    private JdbcCredentialStore store;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        store = new JdbcCredentialStore(dataSource);
        store.ensureSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE auth_users");
        }
        dataSource.close();
    }

    @Test
    @DisplayName("findByUsername returns empty for non-existent user")
    void findByUsername_nonExistent_returnsEmpty() {
        Optional<CredentialStore.StoredUser> result =
                runPromise(() -> store.findByUsername("ghost"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createUser persists user and findByUsername retrieves it")
    void createUser_thenFind_returnsStoredUser() {
        CredentialStore.StoredUser created = runPromise(() ->
                store.createUser("alice", "hash$1", "alice@example.com",
                        List.of("USER"), "tenant-1"));

        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.email()).isEqualTo("alice@example.com");
        assertThat(created.tenantId()).isEqualTo("tenant-1");
        assertThat(created.enabled()).isTrue();
        assertThat(created.roles()).containsExactly("USER");

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("alice"));
        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("createUser with multiple roles preserves all roles")
    void createUser_multipleRoles_allRolesStored() {
        runPromise(() ->
                store.createUser("bob", "hash$2", "bob@example.com",
                        List.of("USER", "ADMIN", "MODERATOR"), "tenant-2"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("bob"));

        assertThat(found).isPresent();
        assertThat(found.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN", "MODERATOR");
    }

    @Test
    @DisplayName("createUser for duplicate username throws IllegalStateException")
    void createUser_duplicate_throwsIllegalStateException() {
        runPromise(() ->
                store.createUser("carol", "hash$3", "carol@example.com",
                        List.of("USER"), "tenant-1"));

        assertThatThrownBy(() ->
                runPromise(() -> store.createUser("carol", "hash$4", "carol2@example.com",
                        List.of("USER"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("carol");
    }

    @Test
    @DisplayName("findByUsername returns empty roles correctly for empty roles list")
    void createUser_emptyRoles_storedAndRetrieved() {
        runPromise(() ->
                store.createUser("dave", "hash$5", "dave@example.com",
                        List.of(), "tenant-3"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("dave"));

        assertThat(found).isPresent();
        assertThat(found.get().roles()).isEmpty();
    }

    @Test
    @DisplayName("findByUsername returns user with correct tenantId")
    void createUser_tenantIsolation_tenantIdPreserved() {
        runPromise(() ->
                store.createUser("eve", "hash$6", "eve@example.com",
                        List.of("USER"), "tenant-xyz"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("eve"));

        assertThat(found).isPresent();
        assertThat(found.get().tenantId()).isEqualTo("tenant-xyz");
    }

    @Test
    @DisplayName("ensureSchema is idempotent — safe to call multiple times")
    void ensureSchema_idempotent_noException() {
        store.ensureSchema();
        store.ensureSchema(); // third call total — must not throw
    }
}
