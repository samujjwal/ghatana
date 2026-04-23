/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.storage.profile;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for storage profile creation, validation, application, and migration.
 *
 * @doc.type    class
 * @doc.purpose Tests for storage profile management correctness
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Storage Profile Tests")
class StorageProfileTest extends EventloopTestBase {

    // ── Storage profile model ─────────────────────────────────────────────────

    enum StorageType { IN_MEMORY, POSTGRES, S3, REDIS }

    record StorageProfile(String profileId, String tenantId, StorageType storageType, // GH-90000
                          Map<String, String> config, boolean encrypted, int replicationFactor) {
        void validate() { // GH-90000
            Objects.requireNonNull(profileId, "profileId must not be null"); // GH-90000
            Objects.requireNonNull(tenantId, "tenantId must not be null"); // GH-90000
            Objects.requireNonNull(storageType, "storageType must not be null"); // GH-90000
            if (replicationFactor < 1) throw new IllegalArgumentException("replicationFactor must be >= 1");
            if (storageType == StorageType.POSTGRES) { // GH-90000
                if (!config.containsKey("jdbcUrl")) {
                    throw new IllegalArgumentException("POSTGRES profile requires jdbcUrl");
                }
            }
            if (storageType == StorageType.S3) { // GH-90000
                if (!config.containsKey("bucket")) {
                    throw new IllegalArgumentException("S3 profile requires bucket");
                }
            }
        }
    }

    private StorageProfileRegistry profileRegistry;

    @BeforeEach
    void setUp() { // GH-90000
        profileRegistry = new StorageProfileRegistry(); // GH-90000
    }

    // ── Profile creation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create stores a valid storage profile")
    void createStoresValidProfile() { // GH-90000
        StorageProfile profile = new StorageProfile( // GH-90000
                "profile-1", "tenant-A", StorageType.IN_MEMORY,
                Map.of(), false, 1); // GH-90000

        profileRegistry.create(profile); // GH-90000

        assertThat(profileRegistry.find("profile-1", "tenant-A")).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("create a POSTGRES profile with valid jdbc URL succeeds")
    void createPostgresProfileWithValidJdbcUrl() { // GH-90000
        StorageProfile profile = new StorageProfile( // GH-90000
                "profile-pg", "tenant-PG", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://localhost:5432/testdb"), true, 3); // GH-90000

        profileRegistry.create(profile); // GH-90000
        assertThat(profileRegistry.find("profile-pg", "tenant-PG")).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("create an S3 profile with bucket config succeeds")
    void createS3ProfileWithBucketConfig() { // GH-90000
        StorageProfile profile = new StorageProfile( // GH-90000
                "profile-s3", "tenant-S3", StorageType.S3,
                Map.of("bucket", "my-data-bucket", "region", "us-east-1"), true, 1); // GH-90000

        profileRegistry.create(profile); // GH-90000
        assertThat(profileRegistry.find("profile-s3", "tenant-S3")).isPresent(); // GH-90000
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create fails when profileId is null")
    void createFailsWhenProfileIdIsNull() { // GH-90000
        StorageProfile invalid = new StorageProfile( // GH-90000
                null, "tenant-V", StorageType.IN_MEMORY, Map.of(), false, 1); // GH-90000

        assertThatThrownBy(() -> profileRegistry.create(invalid)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("create fails when replicationFactor is less than 1")
    void createFailsWhenReplicationFactorLessThanOne() { // GH-90000
        StorageProfile invalid = new StorageProfile( // GH-90000
                "profile-bad", "tenant-V", StorageType.IN_MEMORY, Map.of(), false, 0); // GH-90000

        assertThatThrownBy(() -> profileRegistry.create(invalid)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("replicationFactor");
    }

    @Test
    @DisplayName("POSTGRES profile without jdbcUrl fails validation")
    void postgresProfileWithoutJdbcUrlFailsValidation() { // GH-90000
        StorageProfile invalid = new StorageProfile( // GH-90000
                "profile-pgbad", "tenant-V", StorageType.POSTGRES, Map.of(), false, 1); // GH-90000

        assertThatThrownBy(() -> profileRegistry.create(invalid)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("jdbcUrl");
    }

    // ── Profile application ───────────────────────────────────────────────────

    @Test
    @DisplayName("apply returns the profile for the given tenant")
    void applyReturnsProfileForTenant() { // GH-90000
        StorageProfile profile = new StorageProfile( // GH-90000
                "profile-app", "tenant-apply", StorageType.REDIS,
                Map.of("host", "redis://localhost:6379"), false, 1); // GH-90000
        profileRegistry.create(profile); // GH-90000

        Optional<StorageProfile> applied = profileRegistry.findByTenant("tenant-apply");
        assertThat(applied).isPresent(); // GH-90000
        assertThat(applied.get().storageType()).isEqualTo(StorageType.REDIS); // GH-90000
    }

    @Test
    @DisplayName("apply returns empty for tenant with no profile")
    void applyReturnsEmptyForTenantWithNoProfile() { // GH-90000
        Optional<StorageProfile> applied = profileRegistry.findByTenant("tenant-ghost");
        assertThat(applied).isEmpty(); // GH-90000
    }

    // ── Profile migration ─────────────────────────────────────────────────────

    @Test
    @DisplayName("migration replaces old profile with new version")
    void migrationReplacesOldProfileWithNewVersion() { // GH-90000
        StorageProfile v1 = new StorageProfile( // GH-90000
                "profile-mig", "tenant-mig", StorageType.IN_MEMORY, Map.of(), false, 1); // GH-90000
        profileRegistry.create(v1); // GH-90000

        StorageProfile v2 = new StorageProfile( // GH-90000
                "profile-mig", "tenant-mig", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://newhost:5432/db"), true, 2); // GH-90000
        profileRegistry.migrate(v1, v2); // GH-90000

        StorageProfile current = profileRegistry.find("profile-mig", "tenant-mig").get(); // GH-90000
        assertThat(current.storageType()).isEqualTo(StorageType.POSTGRES); // GH-90000
        assertThat(current.replicationFactor()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("migration fails if source profile does not match registered version")
    void migrationFailsIfSourceProfileDoesNotMatch() { // GH-90000
        StorageProfile registered = new StorageProfile( // GH-90000
                "profile-mis", "tenant-mis", StorageType.IN_MEMORY, Map.of(), false, 1); // GH-90000
        profileRegistry.create(registered); // GH-90000

        StorageProfile wrongSource = new StorageProfile( // GH-90000
                "profile-mis", "tenant-mis", StorageType.REDIS, Map.of("host", "x"), false, 1); // GH-90000
        StorageProfile target = new StorageProfile( // GH-90000
                "profile-mis", "tenant-mis", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://x:5432/db"), false, 1); // GH-90000

        assertThatThrownBy(() -> profileRegistry.migrate(wrongSource, target)) // GH-90000
                .isInstanceOf(IllegalStateException.class); // GH-90000
    }

    // ── Storage profile registry (for tests) ────────────────────────────────── // GH-90000

    static class StorageProfileRegistry {
        private final Map<String, StorageProfile> store = new HashMap<>(); // GH-90000

        void create(StorageProfile profile) { // GH-90000
            profile.validate(); // GH-90000
            store.put(key(profile.profileId(), profile.tenantId()), profile); // GH-90000
        }

        Optional<StorageProfile> find(String profileId, String tenantId) { // GH-90000
            return Optional.ofNullable(store.get(key(profileId, tenantId))); // GH-90000
        }

        Optional<StorageProfile> findByTenant(String tenantId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(p -> p.tenantId().equals(tenantId)) // GH-90000
                    .findFirst(); // GH-90000
        }

        void migrate(StorageProfile source, StorageProfile target) { // GH-90000
            String k = key(source.profileId(), source.tenantId()); // GH-90000
            StorageProfile current = store.get(k); // GH-90000
            if (current == null || current.storageType() != source.storageType()) { // GH-90000
                throw new IllegalStateException("Source profile does not match current registration");
            }
            target.validate(); // GH-90000
            store.put(k, target); // GH-90000
        }

        private String key(String profileId, String tenantId) { // GH-90000
            return tenantId + "|" + profileId;
        }
    }
}
