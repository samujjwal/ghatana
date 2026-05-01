/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@Tag("integration")
@Testcontainers
@DisplayName("PostgresUserProfileStore — integration tests with PostgreSQL")
class PostgresUserProfileStoreIT extends EventloopTestBase {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("profiles_test")
                    .withUsername("profiles")
                    .withPassword("profiles");

    private HikariDataSource dataSource;
    private PostgresUserProfileStore store;

    @BeforeEach
    void setUp() { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(config); 

        // Constructor calls initSchema() automatically — table is ready after this 
        store = new PostgresUserProfileStore(dataSource); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (var conn = dataSource.getConnection(); 
             var stmt = conn.createStatement()) { 
            stmt.execute("TRUNCATE TABLE user_profiles");
        }
        dataSource.close(); 
    }

    private static UserProfile sampleProfile(String userId, String tenantId) { 
        return UserProfile.builder() 
                .userId(userId) 
                .tenantId(tenantId) 
                .email(userId + "@example.com") 
                .displayName("User " + userId) 
                .preferredLanguage("en-US")
                .timezone("UTC")
                .theme("system")
                .notificationsEnabled(true) 
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build(); 
    }

    @Test
    @DisplayName("findByTenantAndUser returns empty for non-existent profile")
    void findByTenantAndUser_nonExistent_returnsEmpty() { 
        Optional<UserProfile> result =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "ghost")); 
        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("upsert creates a new profile and findByTenantAndUser retrieves it")
    void upsert_newProfile_canBeFound() { 
        UserProfile profile = sampleProfile("user-1", "tenant-1"); 

        UserProfile saved = runPromise(() -> store.upsert(profile)); 

        assertThat(saved.userId()).isEqualTo("user-1");
        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.email()).isEqualTo("user-1@example.com");

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-1")); 
        assertThat(found).isPresent(); 
        assertThat(found.get().displayName()).isEqualTo("User user-1");
    }

    @Test
    @DisplayName("upsert updates existing profile on conflict")
    void upsert_existingProfile_updatesFields() { 
        UserProfile original = sampleProfile("user-2", "tenant-1"); 
        runPromise(() -> store.upsert(original)); 

        UserProfile updated = original.toBuilder() 
                .displayName("Updated Name")
                .theme("dark")
                .build(); 
        UserProfile saved = runPromise(() -> store.upsert(updated)); 

        assertThat(saved.displayName()).isEqualTo("Updated Name");
        assertThat(saved.theme()).isEqualTo("dark");

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-2")); 
        assertThat(found).isPresent(); 
        assertThat(found.get().displayName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("delete removes an existing profile")
    void delete_existingProfile_isNoLongerFound() { 
        UserProfile profile = sampleProfile("user-3", "tenant-1"); 
        runPromise(() -> store.upsert(profile)); 

        runPromise(() -> store.delete("tenant-1", "user-3")); 

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-3")); 
        assertThat(found).isEmpty(); 
    }

    @Test
    @DisplayName("delete on non-existent profile does not throw")
    void delete_nonExistent_noException() { 
        runPromise(() -> store.delete("tenant-1", "nobody")); 
        // No assertion needed — must not throw
    }

    @Test
    @DisplayName("profiles are isolated per tenant — same userId in different tenants is independent")
    void upsert_samUserDifferentTenants_isolatedCorrectly() { 
        UserProfile t1 = sampleProfile("user-shared", "tenant-A"); 
        UserProfile t2 = sampleProfile("user-shared", "tenant-B") 
                .toBuilder().displayName("Tenant B User").build();

        runPromise(() -> store.upsert(t1)); 
        runPromise(() -> store.upsert(t2)); 

        Optional<UserProfile> foundInA =
                runPromise(() -> store.findByTenantAndUser("tenant-A", "user-shared")); 
        Optional<UserProfile> foundInB =
                runPromise(() -> store.findByTenantAndUser("tenant-B", "user-shared")); 

        assertThat(foundInA).isPresent(); 
        assertThat(foundInA.get().displayName()).isEqualTo("User user-shared");
        assertThat(foundInB).isPresent(); 
        assertThat(foundInB.get().displayName()).isEqualTo("Tenant B User");
    }

    @Test
    @DisplayName("upsert preserves optional fields like avatarUrl")
    void upsert_withAvatarUrl_fieldPreserved() { 
        UserProfile profile = sampleProfile("user-4", "tenant-1") 
                .toBuilder().avatarUrl("https://cdn.example.com/avatar.png").build();

        runPromise(() -> store.upsert(profile)); 

        Optional<UserProfile> found =
                runPromise(() -> store.findByTenantAndUser("tenant-1", "user-4")); 
        assertThat(found).isPresent(); 
        assertThat(found.get().avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
    }
}
