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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link JdbcCredentialStore} using in-memory H2 database.
 *
 * <p><b>Purpose</b><br>
 * Provides fast integration tests using H2 in-memory database for local development
 * and CI environments where Testcontainers overhead is undesirable.
 *
 * @doc.type class
 * @doc.purpose Integration tests for JdbcCredentialStore with H2 in-memory database
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("integration [GH-90000]")
@DisplayName("JdbcCredentialStore — integration tests with H2 in-memory [GH-90000]")
class JdbcCredentialStoreH2IT extends EventloopTestBase {

    private HikariDataSource dataSource;
    private JdbcCredentialStore store;

    @BeforeEach
    void setUp() { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl("jdbc:h2:mem:auth_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL [GH-90000]");
        config.setUsername("sa [GH-90000]");
        config.setPassword(" [GH-90000]");
        config.setMaximumPoolSize(5); // GH-90000
        config.setDriverClassName("org.h2.Driver [GH-90000]");
        dataSource = new HikariDataSource(config); // GH-90000

        store = new JdbcCredentialStore(dataSource); // GH-90000
        store.ensureSchema(); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (var conn = dataSource.getConnection(); // GH-90000
             var stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS auth_users [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    @Test
    @DisplayName("findByUsername returns empty for non-existent user [GH-90000]")
    void findByUsername_nonExistent_returnsEmpty() { // GH-90000
        Optional<CredentialStore.StoredUser> result =
                runPromise(() -> store.findByUsername("ghost [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("createUser persists user and findByUsername retrieves it [GH-90000]")
    void createUser_thenFind_returnsStoredUser() { // GH-90000
        CredentialStore.StoredUser created = runPromise(() -> // GH-90000
                store.createUser("alice", "hash$1", "alice@example.com", // GH-90000
                        List.of("USER [GH-90000]"), "tenant-1"));

        assertThat(created.username()).isEqualTo("alice [GH-90000]");
        assertThat(created.email()).isEqualTo("alice@example.com [GH-90000]");
        assertThat(created.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        assertThat(created.enabled()).isTrue(); // GH-90000
        assertThat(created.roles()).containsExactly("USER [GH-90000]");

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("alice [GH-90000]"));
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().username()).isEqualTo("alice [GH-90000]");
    }

    @Test
    @DisplayName("createUser with multiple roles preserves all roles [GH-90000]")
    void createUser_multipleRoles_allRolesStored() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("bob", "hash$2", "bob@example.com", // GH-90000
                        List.of("USER", "ADMIN", "MODERATOR"), "tenant-2")); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("bob [GH-90000]"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN", "MODERATOR"); // GH-90000
    }

    @Test
    @DisplayName("createUser for duplicate username throws IllegalStateException [GH-90000]")
    void createUser_duplicate_throwsIllegalStateException() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("carol", "hash$3", "carol@example.com", // GH-90000
                        List.of("USER [GH-90000]"), "tenant-1"));

        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> store.createUser("carol", "hash$4", "carol2@example.com", // GH-90000
                        List.of("USER [GH-90000]"), "tenant-1")))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("carol [GH-90000]");
    }

    @Test
    @DisplayName("createUser with empty roles stores and retrieves correctly [GH-90000]")
    void createUser_emptyRoles_storedAndRetrieved() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("dave", "hash$5", "dave@example.com", // GH-90000
                        List.of(), "tenant-3")); // GH-90000

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("dave [GH-90000]"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().roles()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("createUser preserves tenantId for tenant isolation [GH-90000]")
    void createUser_tenantIsolation_tenantIdPreserved() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("eve", "hash$6", "eve@example.com", // GH-90000
                        List.of("USER [GH-90000]"), "tenant-xyz"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("eve [GH-90000]"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().tenantId()).isEqualTo("tenant-xyz [GH-90000]");
    }

    @Test
    @DisplayName("ensureSchema is idempotent — safe to call multiple times [GH-90000]")
    void ensureSchema_idempotent_noException() { // GH-90000
        store.ensureSchema(); // GH-90000
        store.ensureSchema(); // second call — must not throw // GH-90000
    }

    @Test
    @DisplayName("H2 PostgreSQL compatibility mode works correctly [GH-90000]")
    void h2PostgresCompatibility_worksCorrectly() { // GH-90000
        runPromise(() -> // GH-90000
                store.createUser("test_user", "hash$test", "test@example.com", // GH-90000
                        List.of("USER [GH-90000]"), "tenant-test"));

        Optional<CredentialStore.StoredUser> found =
                runPromise(() -> store.findByUsername("test_user [GH-90000]"));

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().username()).isEqualTo("test_user [GH-90000]");
        assertThat(found.get().enabled()).isTrue(); // GH-90000
    }
}
