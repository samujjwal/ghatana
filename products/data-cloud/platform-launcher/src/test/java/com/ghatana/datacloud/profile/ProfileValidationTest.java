/*
 * Copyright (c) 2026 Ghatana Inc.
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
 *   <li>Repository validation (InMemoryCollectionStorageProfileRepository)</li>
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
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                null,
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("id must not be null");
        }

        @Test
        @DisplayName("should reject null collectionName")
        void shouldRejectNullCollectionName() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                null,
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("collectionName must not be null");
        }

        @Test
        @DisplayName("should reject null storageProfileId")
        void shouldRejectNullStorageProfileId() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                null,
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("storageProfileId must not be null");
        }

        @Test
        @DisplayName("should reject null primaryBackendId")
        void shouldRejectNullPrimaryBackendId() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                null,
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("primaryBackendId must not be null");
        }

        @Test
        @DisplayName("should reject null createdAt")
        void shouldRejectNullCreatedAt() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                null,
                Instant.now()
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("createdAt must not be null");
        }

        @Test
        @DisplayName("should reject null updatedAt")
        void shouldRejectNullUpdatedAt() {
            assertThatThrownBy(() -> new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                null
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("updatedAt must not be null");
        }

        @Test
        @DisplayName("should accept null tenantId")
        void shouldAcceptNullTenantId() {
            CollectionStorageProfile profile = new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                null,
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            );

            assertThat(profile.getTenantId()).isNull();
        }

        @Test
        @DisplayName("should accept null fallbackBackendIds")
        void shouldAcceptNullFallbackBackendIds() {
            CollectionStorageProfile profile = new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                null,
                Map.of(),
                true,
                0,
                Instant.now(),
                Instant.now()
            );

            assertThat(profile.getFallbackBackendIds()).isEmpty();
        }

        @Test
        @DisplayName("should accept null backendConfig")
        void shouldAcceptNullBackendConfig() {
            CollectionStorageProfile profile = new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                null,
                true,
                0,
                Instant.now(),
                Instant.now()
            );

            assertThat(profile.getBackendConfig()).isEmpty();
        }

        @Test
        @DisplayName("should accept null isActive with default true")
        void shouldAcceptNullIsActiveWithDefaultTrue() {
            CollectionStorageProfile profile = new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                null,
                0,
                Instant.now(),
                Instant.now()
            );

            assertThat(profile.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should accept null priorityOrder with default 0")
        void shouldAcceptNullPriorityOrderWithDefaultZero() {
            CollectionStorageProfile profile = new CollectionStorageProfile(
                UUID.randomUUID().toString(),
                "tenant-1",
                "products",
                "profile-1",
                "postgres-primary",
                List.of(),
                Map.of(),
                true,
                null,
                Instant.now(),
                Instant.now()
            );

            assertThat(profile.getPriorityOrder()).isEqualTo(0);
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
        void shouldAutoGenerateId() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getId()).isNotNull();
            assertThat(profile.getId()).isNotEmpty();
        }

        @Test
        @DisplayName("should auto-generate storageProfileId from collectionName")
        void shouldAutoGenerateStorageProfileId() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getStorageProfileId()).isEqualTo("products-profile");
        }

        @Test
        @DisplayName("should auto-generate createdAt when not provided")
        void shouldAutoGenerateCreatedAt() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getCreatedAt()).isNotNull();
            assertThat(profile.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("should auto-generate updatedAt when not provided")
        void shouldAutoGenerateUpdatedAt() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getUpdatedAt()).isNotNull();
            assertThat(profile.getUpdatedAt()).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("should use provided id when specified")
        void shouldUseProvidedId() {
            String customId = "custom-id-123";
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .id(customId)
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getId()).isEqualTo(customId);
        }

        @Test
        @DisplayName("should use provided storageProfileId when specified")
        void shouldUseProvidedStorageProfileId() {
            String customProfileId = "custom-profile";
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId(customProfileId)
                .primaryBackendId("postgres-primary")
                .build();

            assertThat(profile.getStorageProfileId()).isEqualTo(customProfileId);
        }

        @Test
        @DisplayName("should build valid profile with all fields")
        void shouldBuildValidProfileWithAllFields() {
            Instant now = Instant.now();
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .id("profile-123")
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "redis-cache"))
                .backendConfig(Map.of("timeout", 5000, "retries", 3))
                .isActive(true)
                .priorityOrder(10)
                .createdAt(now)
                .updatedAt(now)
                .build();

            assertThat(profile.getId()).isEqualTo("profile-123");
            assertThat(profile.getTenantId()).isEqualTo("tenant-1");
            assertThat(profile.getCollectionName()).isEqualTo("products");
            assertThat(profile.getStorageProfileId()).isEqualTo("hot-profile");
            assertThat(profile.getPrimaryBackendId()).isEqualTo("postgres-primary");
            assertThat(profile.getFallbackBackendIds()).hasSize(2);
            assertThat(profile.getBackendConfig()).hasSize(2);
            assertThat(profile.getIsActive()).isTrue();
            assertThat(profile.getPriorityOrder()).isEqualTo(10);
            assertThat(profile.getCreatedAt()).isEqualTo(now);
            assertThat(profile.getUpdatedAt()).isEqualTo(now);
        }
    }

    // =========================================================================
    // REPOSITORY VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Repository validation")
    class RepositoryValidation {

        private final InMemoryCollectionStorageProfileRepository repository = 
            new InMemoryCollectionStorageProfileRepository();

        @Test
        @DisplayName("should reject null profile")
        void shouldRejectNullProfile() {
            Promise<CollectionStorageProfile> result = repository.save(null);

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Profile cannot be null");
        }

        @Test
        @DisplayName("should reject blank tenantId")
        void shouldRejectBlankTenantId() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            Promise<CollectionStorageProfile> result = repository.save(profile);

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank tenantId");
        }

        @Test
        @DisplayName("should reject null tenantId")
        void shouldRejectNullTenantId() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId(null)
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            Promise<CollectionStorageProfile> result = repository.save(profile);

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank tenantId");
        }

        @Test
        @DisplayName("should reject blank collectionName")
        void shouldRejectBlankCollectionName() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("")
                .primaryBackendId("postgres-primary")
                .build();

            Promise<CollectionStorageProfile> result = repository.save(profile);

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank collectionName");
        }

        @Test
        @DisplayName("should reject null collectionName")
        void shouldRejectNullCollectionName() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName(null)
                .primaryBackendId("postgres-primary")
                .build();

            Promise<CollectionStorageProfile> result = repository.save(profile);

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank collectionName");
        }

        @Test
        @DisplayName("should accept valid profile")
        void shouldAcceptValidProfile() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .build();

            CollectionStorageProfile saved = runPromise(() -> repository.save(profile));

            assertThat(saved).isNotNull();
            assertThat(saved.getTenantId()).isEqualTo("tenant-1");
            assertThat(saved.getCollectionName()).isEqualTo("products");
        }

        @Test
        @DisplayName("should reject blank tenantId in findByTenantAndName")
        void shouldRejectBlankTenantIdInFindBy() {
            Promise<?> result = repository.findByTenantAndName("", "products");

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId cannot be null or blank");
        }

        @Test
        @DisplayName("should reject blank collectionName in findByTenantAndName")
        void shouldRejectBlankCollectionNameInFindBy() {
            Promise<?> result = repository.findByTenantAndName("tenant-1", "");

            assertThatThrownBy(() -> runPromise(() -> result))
                .isInstanceOf(IllegalArgumentException.class)
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
        void shouldHandleEmptyFallbackBackendIds() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of())
                .build();

            assertThat(profile.getFallbackBackendIds()).isEmpty();
            assertThat(profile.hasFailoverSupport()).isFalse();
        }

        @Test
        @DisplayName("should handle empty backendConfig")
        void shouldHandleEmptyBackendConfig() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of())
                .build();

            assertThat(profile.getBackendConfig()).isEmpty();
        }

        @Test
        @DisplayName("should return empty backends when inactive")
        void shouldReturnEmptyBackendsWhenInactive() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .isActive(false)
                .build();

            assertThat(profile.getAllAvailableBackends()).isEmpty();
            assertThat(profile.supportsBackend("postgres-primary")).isFalse();
        }

        @Test
        @DisplayName("should return all backends when active")
        void shouldReturnAllBackendsWhenActive() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .isActive(true)
                .build();

            assertThat(profile.getAllAvailableBackends()).hasSize(2);
            assertThat(profile.supportsBackend("postgres-primary")).isTrue();
            assertThat(profile.supportsBackend("redis-cache")).isTrue();
        }

        @Test
        @DisplayName("should handle null fallbackBackendIds in builder")
        void shouldHandleNullFallbackBackendIdsInBuilder() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(null)
                .build();

            assertThat(profile.getFallbackBackendIds()).isEmpty();
        }

        @Test
        @DisplayName("should handle null backendConfig in builder")
        void shouldHandleNullBackendConfigInBuilder() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(null)
                .build();

            assertThat(profile.getBackendConfig()).isEmpty();
        }

        @Test
        @DisplayName("should handle null isActive in builder")
        void shouldHandleNullIsActiveInBuilder() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .isActive(null)
                .build();

            assertThat(profile.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("should handle null priorityOrder in builder")
        void shouldHandleNullPriorityOrderInBuilder() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .priorityOrder(null)
                .build();

            assertThat(profile.getPriorityOrder()).isEqualTo(0);
        }

        @Test
        @DisplayName("should make fallbackBackendIds unmodifiable")
        void shouldMakeFallbackBackendIdsUnmodifiable() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("redis-cache"))
                .build();

            List<String> fallbacks = profile.getFallbackBackendIds();
            assertThatThrownBy(() -> fallbacks.add("another"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should make backendConfig unmodifiable")
        void shouldMakeBackendConfigUnmodifiable() {
            CollectionStorageProfile profile = CollectionStorageProfile.builder()
                .tenantId("tenant-1")
                .collectionName("products")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("key", "value"))
                .build();

            Map<String, Object> config = profile.getBackendConfig();
            assertThatThrownBy(() -> config.put("another", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
