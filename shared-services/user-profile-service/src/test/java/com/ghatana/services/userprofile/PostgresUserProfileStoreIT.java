/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.services.userprofile;

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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresUserProfileStore} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresUserProfileStore with Testcontainers
 * @doc.layer platform
 * @doc.pattern Test
 */
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("PostgresUserProfileStore — integration tests with PostgreSQL [GH-90000]")
class PostgresUserProfileStoreIT extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("profiles_test [GH-90000]")
                    .withUsername("profiles [GH-90000]")
                    .withPassword("profiles [GH-90000]");

    private HikariDataSource dataSource;
    private PostgresUserProfileStore store;

    @BeforeEach
    void setUp() { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000

        // Constructor calls initSchema() automatically — table is ready after this // GH-90000
        store = new PostgresUserProfileStore(dataSource); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (var conn = dataSource.getConnection(); // GH-90000
             var stmt = conn.createStatement()) { // GH-90000
            stmt.execute("TRUNCATE TABLE user_profiles [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    private static UserProfile sampleProfile(String userId, String tenantId) { // GH-90000
        return UserProfile.builder() // GH-90000
                .userId(userId) // GH-90000
                .tenantId(tenantId) // GH-90000
                .email(userId + "@example.com") // GH-90000
                .displayName("User " + userId) // GH-90000
                .preferredLanguage("en-US [GH-90000]")
                .timezone("UTC [GH-90000]")
                .theme("system [GH-90000]")
                .notificationsEnabled(true) // GH-90000
                .createdAt(Instant.parse("2026-01-01T00:00:00Z [GH-90000]"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z [GH-90000]"))
                .build(); // GH-90000
    }

    @Test
    @DisplayName("findByTenantAndUser returns empty for non-existent profile [GH-90000]")
    void findByTenantAndUser_nonExistent_returnsEmpty() { // GH-90000
        Optional<UserProfile> result =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "ghost")); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("upsert creates a new profile and findByTenantAndUser retrieves it [GH-90000]")
    void upsert_newProfile_canBeFound() { // GH-90000
        UserProfile profile = sampleProfile("user-1", "tenant-1"); // GH-90000

        UserProfile saved = runPromise(() -> store.upsert(profile)); // GH-90000

        assertThat(saved.userId()).isEqualTo("user-1 [GH-90000]");
        assertThat(saved.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        assertThat(saved.email()).isEqualTo("user-1@example.com [GH-90000]");

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-1")); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().displayName()).isEqualTo("User user-1 [GH-90000]");
    }

    @Test
    @DisplayName("upsert updates existing profile on conflict [GH-90000]")
    void upsert_existingProfile_updatesFields() { // GH-90000
        UserProfile original = sampleProfile("user-2", "tenant-1"); // GH-90000
        runPromise(() -> store.upsert(original)); // GH-90000

        UserProfile updated = original.toBuilder() // GH-90000
                .displayName("Updated Name [GH-90000]")
                .theme("dark [GH-90000]")
                .build(); // GH-90000
        UserProfile saved = runPromise(() -> store.upsert(updated)); // GH-90000

        assertThat(saved.displayName()).isEqualTo("Updated Name [GH-90000]");
        assertThat(saved.theme()).isEqualTo("dark [GH-90000]");

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-2")); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().displayName()).isEqualTo("Updated Name [GH-90000]");
    }

    @Test
    @DisplayName("delete removes an existing profile [GH-90000]")
    void delete_existingProfile_isNoLongerFound() { // GH-90000
        UserProfile profile = sampleProfile("user-3", "tenant-1"); // GH-90000
        runPromise(() -> store.upsert(profile)); // GH-90000

        runPromise(() -> store.delete("tenant-1", "user-3")); // GH-90000

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-3")); // GH-90000
        assertThat(found).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("delete on non-existent profile does not throw [GH-90000]")
    void delete_nonExistent_noException() { // GH-90000
        runPromise(() -> store.delete("tenant-1", "nobody")); // GH-90000
        // No assertion needed — must not throw
    }

    @Test
    @DisplayName("profiles are isolated per tenant — same userId in different tenants is independent [GH-90000]")
    void upsert_samUserDifferentTenants_isolatedCorrectly() { // GH-90000
        UserProfile t1 = sampleProfile("user-shared", "tenant-A"); // GH-90000
        UserProfile t2 = sampleProfile("user-shared", "tenant-B") // GH-90000
                .toBuilder().displayName("Tenant B User [GH-90000]").build();

        runPromise(() -> store.upsert(t1)); // GH-90000
        runPromise(() -> store.upsert(t2)); // GH-90000

        Optional<UserProfile> foundInA =
                runPromise(() -> store.findByTenantAndUser("tenant-A", "user-shared")); // GH-90000
        Optional<UserProfile> foundInB =
                runPromise(() -> store.findByTenantAndUser("tenant-B", "user-shared")); // GH-90000

        assertThat(foundInA).isPresent(); // GH-90000
        assertThat(foundInA.get().displayName()).isEqualTo("User user-shared [GH-90000]");
        assertThat(foundInB).isPresent(); // GH-90000
        assertThat(foundInB.get().displayName()).isEqualTo("Tenant B User [GH-90000]");
    }

    @Test
    @DisplayName("upsert preserves optional fields like avatarUrl [GH-90000]")
    void upsert_withAvatarUrl_fieldPreserved() { // GH-90000
        UserProfile profile = sampleProfile("user-4", "tenant-1") // GH-90000
                .toBuilder().avatarUrl("https://cdn.example.com/avatar.png [GH-90000]").build();

        runPromise(() -> store.upsert(profile)); // GH-90000

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-4")); // GH-90000
        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png [GH-90000]");
    }
}
