/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    record StorageProfile(String profileId, String tenantId, StorageType storageType, 
                          Map<String, String> config, boolean encrypted, int replicationFactor) {
        void validate() { 
            Objects.requireNonNull(profileId, "profileId must not be null"); 
            Objects.requireNonNull(tenantId, "tenantId must not be null"); 
            Objects.requireNonNull(storageType, "storageType must not be null"); 
            if (replicationFactor < 1) throw new IllegalArgumentException("replicationFactor must be >= 1");
            if (storageType == StorageType.POSTGRES) { 
                if (!config.containsKey("jdbcUrl")) {
                    throw new IllegalArgumentException("POSTGRES profile requires jdbcUrl");
                }
            }
            if (storageType == StorageType.S3) { 
                if (!config.containsKey("bucket")) {
                    throw new IllegalArgumentException("S3 profile requires bucket");
                }
            }
        }
    }

    private StorageProfileRegistry profileRegistry;

    @BeforeEach
    void setUp() { 
        profileRegistry = new StorageProfileRegistry(); 
    }

    // ── Profile creation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create stores a valid storage profile")
    void createStoresValidProfile() { 
        StorageProfile profile = new StorageProfile( 
                "profile-1", "tenant-A", StorageType.IN_MEMORY,
                Map.of(), false, 1); 

        profileRegistry.create(profile); 

        assertThat(profileRegistry.find("profile-1", "tenant-A")).isPresent(); 
    }

    @Test
    @DisplayName("create a POSTGRES profile with valid jdbc URL succeeds")
    void createPostgresProfileWithValidJdbcUrl() { 
        StorageProfile profile = new StorageProfile( 
                "profile-pg", "tenant-PG", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://localhost:5432/testdb"), true, 3); 

        profileRegistry.create(profile); 
        assertThat(profileRegistry.find("profile-pg", "tenant-PG")).isPresent(); 
    }

    @Test
    @DisplayName("create an S3 profile with bucket config succeeds")
    void createS3ProfileWithBucketConfig() { 
        StorageProfile profile = new StorageProfile( 
                "profile-s3", "tenant-S3", StorageType.S3,
                Map.of("bucket", "my-data-bucket", "region", "us-east-1"), true, 1); 

        profileRegistry.create(profile); 
        assertThat(profileRegistry.find("profile-s3", "tenant-S3")).isPresent(); 
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create fails when profileId is null")
    void createFailsWhenProfileIdIsNull() { 
        StorageProfile invalid = new StorageProfile( 
                null, "tenant-V", StorageType.IN_MEMORY, Map.of(), false, 1); 

        assertThatThrownBy(() -> profileRegistry.create(invalid)) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("create fails when replicationFactor is less than 1")
    void createFailsWhenReplicationFactorLessThanOne() { 
        StorageProfile invalid = new StorageProfile( 
                "profile-bad", "tenant-V", StorageType.IN_MEMORY, Map.of(), false, 0); 

        assertThatThrownBy(() -> profileRegistry.create(invalid)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("replicationFactor");
    }

    @Test
    @DisplayName("POSTGRES profile without jdbcUrl fails validation")
    void postgresProfileWithoutJdbcUrlFailsValidation() { 
        StorageProfile invalid = new StorageProfile( 
                "profile-pgbad", "tenant-V", StorageType.POSTGRES, Map.of(), false, 1); 

        assertThatThrownBy(() -> profileRegistry.create(invalid)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("jdbcUrl");
    }

    // ── Profile application ───────────────────────────────────────────────────

    @Test
    @DisplayName("apply returns the profile for the given tenant")
    void applyReturnsProfileForTenant() { 
        StorageProfile profile = new StorageProfile( 
                "profile-app", "tenant-apply", StorageType.REDIS,
                Map.of("host", "redis://localhost:6379"), false, 1); 
        profileRegistry.create(profile); 

        Optional<StorageProfile> applied = profileRegistry.findByTenant("tenant-apply");
        assertThat(applied).isPresent(); 
        assertThat(applied.get().storageType()).isEqualTo(StorageType.REDIS); 
    }

    @Test
    @DisplayName("apply returns empty for tenant with no profile")
    void applyReturnsEmptyForTenantWithNoProfile() { 
        Optional<StorageProfile> applied = profileRegistry.findByTenant("tenant-ghost");
        assertThat(applied).isEmpty(); 
    }

    // ── Profile migration ─────────────────────────────────────────────────────

    @Test
    @DisplayName("migration replaces old profile with new version")
    void migrationReplacesOldProfileWithNewVersion() { 
        StorageProfile v1 = new StorageProfile( 
                "profile-mig", "tenant-mig", StorageType.IN_MEMORY, Map.of(), false, 1); 
        profileRegistry.create(v1); 

        StorageProfile v2 = new StorageProfile( 
                "profile-mig", "tenant-mig", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://newhost:5432/db"), true, 2); 
        profileRegistry.migrate(v1, v2); 

        StorageProfile current = profileRegistry.find("profile-mig", "tenant-mig").get(); 
        assertThat(current.storageType()).isEqualTo(StorageType.POSTGRES); 
        assertThat(current.replicationFactor()).isEqualTo(2); 
    }

    @Test
    @DisplayName("migration fails if source profile does not match registered version")
    void migrationFailsIfSourceProfileDoesNotMatch() { 
        StorageProfile registered = new StorageProfile( 
                "profile-mis", "tenant-mis", StorageType.IN_MEMORY, Map.of(), false, 1); 
        profileRegistry.create(registered); 

        StorageProfile wrongSource = new StorageProfile( 
                "profile-mis", "tenant-mis", StorageType.REDIS, Map.of("host", "x"), false, 1); 
        StorageProfile target = new StorageProfile( 
                "profile-mis", "tenant-mis", StorageType.POSTGRES,
                Map.of("jdbcUrl", "jdbc:postgresql://x:5432/db"), false, 1); 

        assertThatThrownBy(() -> profileRegistry.migrate(wrongSource, target)) 
                .isInstanceOf(IllegalStateException.class); 
    }

    // ── Storage profile registry (for tests) ────────────────────────────────── 

    static class StorageProfileRegistry {
        private final Map<String, StorageProfile> store = new HashMap<>(); 

        void create(StorageProfile profile) { 
            profile.validate(); 
            store.put(key(profile.profileId(), profile.tenantId()), profile); 
        }

        Optional<StorageProfile> find(String profileId, String tenantId) { 
            return Optional.ofNullable(store.get(key(profileId, tenantId))); 
        }

        Optional<StorageProfile> findByTenant(String tenantId) { 
            return store.values().stream() 
                    .filter(p -> p.tenantId().equals(tenantId)) 
                    .findFirst(); 
        }

        void migrate(StorageProfile source, StorageProfile target) { 
            String k = key(source.profileId(), source.tenantId()); 
            StorageProfile current = store.get(k); 
            if (current == null || current.storageType() != source.storageType()) { 
                throw new IllegalStateException("Source profile does not match current registration");
            }
            target.validate(); 
            store.put(k, target); 
        }

        private String key(String profileId, String tenantId) { 
            return tenantId + "|" + profileId;
        }
    }
}
