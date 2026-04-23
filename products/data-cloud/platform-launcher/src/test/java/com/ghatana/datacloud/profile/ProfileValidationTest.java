/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.profile;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.infrastructure.persistence.storage.InMemoryCollectionStorageProfileRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for collection storage profile validation.
 *
 * <p>Tests validation logic across:
 * <ul>
 *   <li>CollectionStorageProfile constructor validation</li>
 *   <li>CollectionStorageProfile builder validation</li>
 *   <li>Repository validation (InMemoryCollectionStorageProfileRepository)</li> // GH-90000
 *   <li>Edge cases and null safety</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Collection storage profile validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Profile Validation Tests")
class ProfileValidationTest extends EventloopTestBase {

    // =========================================================================
    // CONSTRUCTOR VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("CollectionStorageProfile constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                null,
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("should reject null collectionName")
        void shouldRejectNullCollectionName() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                null,
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("collectionName must not be null");
        }

        @Test
        @DisplayName("should reject null storageProfileId")
        void shouldRejectNullStorageProfileId() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                null,
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("storageProfileId must not be null");
        }

        @Test
        @DisplayName("should reject null primaryBackendId")
        void shouldRejectNullPrimaryBackendId() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                null,
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("primaryBackendId must not be null");
        }

        @Test
        @DisplayName("should reject null createdAt")
        void shouldRejectNullCreatedAt() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                null,
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("createdAt must not be null");
        }

        @Test
        @DisplayName("should reject null updatedAt")
        void shouldRejectNullUpdatedAt() { // GH-90000
            assertThatThrownBy(() -> new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                null
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("updatedAt must not be null");
        }

        @Test
        @DisplayName("should accept null tenantId")
        void shouldAcceptNullTenantId() { // GH-90000
            CollectionStorageProfile profile = new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                null,
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(profile.getTenantId()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should accept null fallbackBackendIds")
        void shouldAcceptNullFallbackBackendIds() { // GH-90000
            CollectionStorageProfile profile = new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                null,
                Map.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(profile.getFallbackBackendIds()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should accept null backendConfig")
        void shouldAcceptNullBackendConfig() { // GH-90000
            CollectionStorageProfile profile = new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                null,
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(profile.getBackendConfig()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should accept null isActive with default true")
        void shouldAcceptNullIsActiveWithDefaultTrue() { // GH-90000
            CollectionStorageProfile profile = new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                null,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(profile.getIsActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should accept null priorityOrder with default 0")
        void shouldAcceptNullPriorityOrderWithDefaultZero() { // GH-90000
            CollectionStorageProfile profile = new CollectionStorageProfile( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(), // GH-90000
                Map.of(), // GH-90000
                true,
                null,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(profile.getPriorityOrder()).isEqualTo(0); // GH-90000
        }
    }

    // =========================================================================
    // BUILDER VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("CollectionStorageProfile builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("should auto-generate id when not provided")
        void shouldAutoGenerateId() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getId()).isNotNull(); // GH-90000
            assertThat(profile.getId()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should auto-generate storageProfileId from collectionName")
        void shouldAutoGenerateStorageProfileId() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getStorageProfileId()).isEqualTo("products-profile");
        }

        @Test
        @DisplayName("should auto-generate createdAt when not provided")
        void shouldAutoGenerateCreatedAt() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(profile.getCreatedAt()).isBeforeOrEqualTo(Instant.now()); // GH-90000
        }

        @Test
        @DisplayName("should auto-generate updatedAt when not provided")
        void shouldAutoGenerateUpdatedAt() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getUpdatedAt()).isNotNull(); // GH-90000
            assertThat(profile.getUpdatedAt()).isBeforeOrEqualTo(Instant.now()); // GH-90000
        }

        @Test
        @DisplayName("should use provided id when specified")
        void shouldUseProvidedId() { // GH-90000
            String customId = "custom-id-123";
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .id(customId) // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getId()).isEqualTo(customId); // GH-90000
        }

        @Test
        @DisplayName("should use provided storageProfileId when specified")
        void shouldUseProvidedStorageProfileId() { // GH-90000
            String customProfileId = "custom-profile";
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId(customProfileId) // GH-90000
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            assertThat(profile.getStorageProfileId()).isEqualTo(customProfileId); // GH-90000
        }

        @Test
        @DisplayName("should build valid profile with all fields")
        void shouldBuildValidProfileWithAllFields() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .id("profile-123")
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "redis-cache")) // GH-90000
                .backendConfig(Map.of("timeout", 5000, "retries", 3)) // GH-90000
                .isActive(true) // GH-90000
                .priorityOrder(10) // GH-90000
                .createdAt(now) // GH-90000
                .updatedAt(now) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getId()).isEqualTo("profile-123");
            assertThat(profile.getTenantId()).isEqualTo("tenant-1");
            assertThat(profile.getCollectionName()).isEqualTo("products");
            assertThat(profile.getStorageProfileId()).isEqualTo("hot-profile");
            assertThat(profile.getPrimaryBackendId()).isEqualTo("postgres-primary");
            assertThat(profile.getFallbackBackendIds()).hasSize(2); // GH-90000
            assertThat(profile.getBackendConfig()).hasSize(2); // GH-90000
            assertThat(profile.getIsActive()).isTrue(); // GH-90000
            assertThat(profile.getPriorityOrder()).isEqualTo(10); // GH-90000
            assertThat(profile.getCreatedAt()).isEqualTo(now); // GH-90000
            assertThat(profile.getUpdatedAt()).isEqualTo(now); // GH-90000
        }
    }

    // =========================================================================
    // REPOSITORY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Repository validation")
    class RepositoryValidation {

        private final InMemoryCollectionStorageProfileRepository repository = 
            new InMemoryCollectionStorageProfileRepository(); // GH-90000

        @Test
        @DisplayName("should reject null profile")
        void shouldRejectNullProfile() { // GH-90000
            Promise<CollectionStorageProfile> result = repository.save(null); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Profile cannot be null");
        }

        @Test
        @DisplayName("should reject blank tenantId")
        void shouldRejectBlankTenantId() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            Promise<CollectionStorageProfile> result = repository.save(profile); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("non-blank tenantId");
        }

        @Test
        @DisplayName("should reject null tenantId")
        void shouldRejectNullTenantId() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId(null) // GH-90000
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            Promise<CollectionStorageProfile> result = repository.save(profile); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("non-blank tenantId");
        }

        @Test
        @DisplayName("should reject blank collectionName")
        void shouldRejectBlankCollectionName() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            Promise<CollectionStorageProfile> result = repository.save(profile); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("non-blank collectionName");
        }

        @Test
        @DisplayName("should reject null collectionName")
        void shouldRejectNullCollectionName() { // GH-90000
            assertThatThrownBy(() -> CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName(null) // GH-90000
                .primaryBackendId("postgres-primary")
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("collectionName must not be null");
        }

        @Test
        @DisplayName("should accept valid profile")
        void shouldAcceptValidProfile() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(profile)); // GH-90000

            assertThat(saved).isNotNull(); // GH-90000
            assertThat(saved.getTenantId()).isEqualTo("tenant-1");
            assertThat(saved.getCollectionName()).isEqualTo("products");
        }

        @Test
        @DisplayName("should reject blank tenantId in findByTenantAndName")
        void shouldRejectBlankTenantIdInFindBy() { // GH-90000
            Promise<?> result = repository.findByTenantAndName("", "products"); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId cannot be null or blank");
        }

        @Test
        @DisplayName("should reject blank collectionName in findByTenantAndName")
        void shouldRejectBlankCollectionNameInFindBy() { // GH-90000
            Promise<?> result = repository.findByTenantAndName("tenant-1", ""); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> result)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("collectionName cannot be null or blank");
        }
    }

    // =========================================================================
    // EDGE CASES AND NULL SAFETY
    // =========================================================================

    @Nested
    @DisplayName("Edge cases and null safety")
    class EdgeCasesAndNullSafety {

        @Test
        @DisplayName("should handle empty fallbackBackendIds")
        void shouldHandleEmptyFallbackBackendIds() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of()) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getFallbackBackendIds()).isEmpty(); // GH-90000
            assertThat(profile.hasFailoverSupport()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should handle empty backendConfig")
        void shouldHandleEmptyBackendConfig() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of()) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getBackendConfig()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return empty backends when inactive")
        void shouldReturnEmptyBackendsWhenInactive() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .isActive(false) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getAllAvailableBackends()).isEmpty(); // GH-90000
            assertThat(profile.supportsBackend("postgres-primary")).isFalse();
        }

        @Test
        @DisplayName("should return all backends when active")
        void shouldReturnAllBackendsWhenActive() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .isActive(true) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getAllAvailableBackends()).hasSize(2); // GH-90000
            assertThat(profile.supportsBackend("postgres-primary")).isTrue();
            assertThat(profile.supportsBackend("redis-cache")).isTrue();
        }

        @Test
        @DisplayName("should handle null fallbackBackendIds in builder")
        void shouldHandleNullFallbackBackendIdsInBuilder() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(null) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getFallbackBackendIds()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null backendConfig in builder")
        void shouldHandleNullBackendConfigInBuilder() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(null) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getBackendConfig()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null isActive in builder")
        void shouldHandleNullIsActiveInBuilder() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .isActive(null) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getIsActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle null priorityOrder in builder")
        void shouldHandleNullPriorityOrderInBuilder() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .priorityOrder(null) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getPriorityOrder()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should make fallbackBackendIds unmodifiable")
        void shouldMakeFallbackBackendIdsUnmodifiable() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .build(); // GH-90000

            List<String> fallbacks = profile.getFallbackBackendIds(); // GH-90000
            assertThatThrownBy(() -> fallbacks.add("another"))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should make backendConfig unmodifiable")
        void shouldMakeBackendConfigUnmodifiable() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("key", "value")) // GH-90000
                .build(); // GH-90000

            Map<String, Object> config = profile.getBackendConfig(); // GH-90000
            assertThatThrownBy(() -> config.put("another", "value")) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
