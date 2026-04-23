package com.ghatana.datacloud.profile;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import com.ghatana.datacloud.infrastructure.persistence.storage.InMemoryCollectionStorageProfileRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for profile switching operations.
 *
 * <p>Tests profile switching scenarios including:
 * <ul>
 *   <li>Activating and deactivating profiles</li>
 *   <li>Switching primary backend within a profile</li>
 *   <li>Updating profile configuration</li>
 *   <li>Profile priority ordering</li>
 *   <li>Fallback backend switching</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Profile switching operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Profile Switching Tests")
class ProfileSwitchingTest extends EventloopTestBase {

    @Nested
    @DisplayName("Profile Activation/Deactivation")
    class ActivationTests {

        @Test
        @DisplayName("switch profile from active to inactive")
        void switchProfileToInactive() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile activeProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) // GH-90000
                .priorityOrder(1) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(activeProfile)); // GH-90000

            // Switch to inactive
            CollectionStorageProfile inactiveProfile = CollectionStorageProfile.builder() // GH-90000
                .id(activeProfile.getId()) // GH-90000
                .tenantId(activeProfile.getTenantId()) // GH-90000
                .collectionName(activeProfile.getCollectionName()) // GH-90000
                .storageProfileId(activeProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(activeProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(activeProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(activeProfile.getBackendConfig()) // GH-90000
                .isActive(false) // GH-90000
                .priorityOrder(activeProfile.getPriorityOrder()) // GH-90000
                .createdAt(activeProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(inactiveProfile)); // GH-90000

            assertThat(saved.getIsActive()).isFalse(); // GH-90000
            assertThat(saved.getUpdatedAt()).isNotEqualTo(activeProfile.getUpdatedAt()); // GH-90000
        }

        @Test
        @DisplayName("switch profile from inactive to active")
        void switchProfileToActive() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile inactiveProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(false) // GH-90000
                .priorityOrder(1) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(inactiveProfile)); // GH-90000

            // Switch to active
            CollectionStorageProfile activeProfile = CollectionStorageProfile.builder() // GH-90000
                .id(inactiveProfile.getId()) // GH-90000
                .tenantId(inactiveProfile.getTenantId()) // GH-90000
                .collectionName(inactiveProfile.getCollectionName()) // GH-90000
                .storageProfileId(inactiveProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(inactiveProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(inactiveProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(inactiveProfile.getBackendConfig()) // GH-90000
                .isActive(true) // GH-90000
                .priorityOrder(inactiveProfile.getPriorityOrder()) // GH-90000
                .createdAt(inactiveProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(activeProfile)); // GH-90000

            assertThat(saved.getIsActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("inactive profile returns no available backends")
        void inactiveProfileReturnsNoBackends() { // GH-90000
            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .isActive(false) // GH-90000
                .build(); // GH-90000

            assertThat(profile.getAllAvailableBackends()).isEmpty(); // GH-90000
            assertThat(profile.supportsBackend("postgres-primary")).isFalse();
        }
    }

    @Nested
    @DisplayName("Primary Backend Switching")
    class PrimaryBackendTests {

        @Test
        @DisplayName("switch primary backend within profile")
        void switchPrimaryBackend() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            // Switch primary backend
            CollectionStorageProfile switchedProfile = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId("postgres-secondary")
                .fallbackBackendIds(List.of("postgres-primary"))
                .backendConfig(originalProfile.getBackendConfig()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(switchedProfile)); // GH-90000

            assertThat(saved.getPrimaryBackendId()).isEqualTo("postgres-secondary");
            assertThat(saved.getFallbackBackendIds()).containsExactly("postgres-primary");
        }

        @Test
        @DisplayName("switch to new primary backend without fallbacks")
        void switchToNewPrimaryWithoutFallbacks() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            CollectionStorageProfile switchedProfile = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId("clickhouse-primary")
                .fallbackBackendIds(List.of()) // GH-90000
                .backendConfig(originalProfile.getBackendConfig()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(switchedProfile)); // GH-90000

            assertThat(saved.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
            assertThat(saved.getFallbackBackendIds()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Fallback Backend Switching")
    class FallbackBackendTests {

        @Test
        @DisplayName("add fallback backend to profile")
        void addFallbackBackend() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of()) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            CollectionStorageProfile withFallback = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(originalProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) // GH-90000
                .backendConfig(originalProfile.getBackendConfig()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(withFallback)); // GH-90000

            assertThat(saved.getFallbackBackendIds()).hasSize(2); // GH-90000
            assertThat(saved.hasFailoverSupport()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("remove fallback backend from profile")
        void removeFallbackBackend() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            CollectionStorageProfile withoutFallback = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(originalProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(List.of("postgres-secondary"))
                .backendConfig(originalProfile.getBackendConfig()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(withoutFallback)); // GH-90000

            assertThat(saved.getFallbackBackendIds()).hasSize(1); // GH-90000
            assertThat(saved.getFallbackBackendIds()).containsExactly("postgres-secondary");
        }

        @Test
        @DisplayName("clear all fallback backends")
        void clearAllFallbackBackends() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            CollectionStorageProfile noFallback = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(originalProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(List.of()) // GH-90000
                .backendConfig(originalProfile.getBackendConfig()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(noFallback)); // GH-90000

            assertThat(saved.getFallbackBackendIds()).isEmpty(); // GH-90000
            assertThat(saved.hasFailoverSupport()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Priority Order Switching")
    class PriorityOrderTests {

        @Test
        @DisplayName("increase profile priority")
        void increaseProfilePriority() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile lowPriorityProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(10) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(lowPriorityProfile)); // GH-90000

            CollectionStorageProfile highPriorityProfile = CollectionStorageProfile.builder() // GH-90000
                .id(lowPriorityProfile.getId()) // GH-90000
                .tenantId(lowPriorityProfile.getTenantId()) // GH-90000
                .collectionName(lowPriorityProfile.getCollectionName()) // GH-90000
                .storageProfileId(lowPriorityProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(lowPriorityProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(lowPriorityProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(lowPriorityProfile.getBackendConfig()) // GH-90000
                .isActive(lowPriorityProfile.getIsActive()) // GH-90000
                .priorityOrder(1) // GH-90000
                .createdAt(lowPriorityProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(highPriorityProfile)); // GH-90000

            assertThat(saved.getPriorityOrder()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("decrease profile priority")
        void decreaseProfilePriority() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile highPriorityProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(1) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(highPriorityProfile)); // GH-90000

            CollectionStorageProfile lowPriorityProfile = CollectionStorageProfile.builder() // GH-90000
                .id(highPriorityProfile.getId()) // GH-90000
                .tenantId(highPriorityProfile.getTenantId()) // GH-90000
                .collectionName(highPriorityProfile.getCollectionName()) // GH-90000
                .storageProfileId(highPriorityProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(highPriorityProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(highPriorityProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(highPriorityProfile.getBackendConfig()) // GH-90000
                .isActive(highPriorityProfile.getIsActive()) // GH-90000
                .priorityOrder(10) // GH-90000
                .createdAt(highPriorityProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(lowPriorityProfile)); // GH-90000

            assertThat(saved.getPriorityOrder()).isEqualTo(10); // GH-90000
        }
    }

    @Nested
    @DisplayName("Configuration Switching")
    class ConfigurationTests {

        @Test
        @DisplayName("switch backend configuration")
        void switchBackendConfiguration() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            Map<String, Object> originalConfig = Map.of( // GH-90000
                "max_connections", 10,
                "timeout_ms", 5000
            );

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(originalConfig) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            Map<String, Object> newConfig = Map.of( // GH-90000
                "max_connections", 20,
                "timeout_ms", 10000,
                "enable_ssl", true
            );

            CollectionStorageProfile newProfile = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(originalProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(originalProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(newConfig) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(newProfile)); // GH-90000

            assertThat(saved.getBackendConfig()).containsEntry("max_connections", 20); // GH-90000
            assertThat(saved.getBackendConfig()).containsEntry("enable_ssl", true); // GH-90000
            assertThat(saved.getBackendConfig()).doesNotContainValue(5000); // GH-90000
        }

        @Test
        @DisplayName("clear backend configuration")
        void clearBackendConfiguration() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            Map<String, Object> originalConfig = Map.of( // GH-90000
                "max_connections", 10,
                "timeout_ms", 5000
            );

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(originalConfig) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(originalProfile)); // GH-90000

            CollectionStorageProfile clearedProfile = CollectionStorageProfile.builder() // GH-90000
                .id(originalProfile.getId()) // GH-90000
                .tenantId(originalProfile.getTenantId()) // GH-90000
                .collectionName(originalProfile.getCollectionName()) // GH-90000
                .storageProfileId(originalProfile.getStorageProfileId()) // GH-90000
                .primaryBackendId(originalProfile.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(originalProfile.getFallbackBackendIds()) // GH-90000
                .backendConfig(Map.of()) // GH-90000
                .isActive(originalProfile.getIsActive()) // GH-90000
                .priorityOrder(originalProfile.getPriorityOrder()) // GH-90000
                .createdAt(originalProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(clearedProfile)); // GH-90000

            assertThat(saved.getBackendConfig()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Switching Scenarios")
    class SwitchingScenarios {

        @Test
        @DisplayName("switch from hot to cold profile")
        void switchFromHotToColdProfile() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile hotProfile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("redis-hot")
                .fallbackBackendIds(List.of()) // GH-90000
                .backendConfig(Map.of("latency_target_ms", 10)) // GH-90000
                .isActive(true) // GH-90000
                .priorityOrder(1) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(hotProfile)); // GH-90000

            CollectionStorageProfile coldProfile = CollectionStorageProfile.builder() // GH-90000
                .id(hotProfile.getId()) // GH-90000
                .tenantId(hotProfile.getTenantId()) // GH-90000
                .collectionName(hotProfile.getCollectionName()) // GH-90000
                .storageProfileId("cold-profile")
                .primaryBackendId("s3-cold")
                .fallbackBackendIds(List.of("s3-backup"))
                .backendConfig(Map.of("latency_target_ms", 1000, "compression", true)) // GH-90000
                .isActive(true) // GH-90000
                .priorityOrder(2) // GH-90000
                .createdAt(hotProfile.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(coldProfile)); // GH-90000

            assertThat(saved.getStorageProfileId()).isEqualTo("cold-profile");
            assertThat(saved.getPrimaryBackendId()).isEqualTo("s3-cold");
            assertThat(saved.getBackendConfig()).containsEntry("compression", true); // GH-90000
        }

        @Test
        @DisplayName("switch with failover support enabled")
        void switchWithFailoverEnabled() { // GH-90000
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); // GH-90000

            CollectionStorageProfile noFailover = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of()) // GH-90000
                .isActive(true) // GH-90000
                .build(); // GH-90000

            runPromise(() -> repository.save(noFailover)); // GH-90000

            CollectionStorageProfile withFailover = CollectionStorageProfile.builder() // GH-90000
                .id(noFailover.getId()) // GH-90000
                .tenantId(noFailover.getTenantId()) // GH-90000
                .collectionName(noFailover.getCollectionName()) // GH-90000
                .storageProfileId(noFailover.getStorageProfileId()) // GH-90000
                .primaryBackendId(noFailover.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) // GH-90000
                .backendConfig(noFailover.getBackendConfig()) // GH-90000
                .isActive(noFailover.getIsActive()) // GH-90000
                .priorityOrder(noFailover.getPriorityOrder()) // GH-90000
                .createdAt(noFailover.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile saved = runPromise(() -> repository.save(withFailover)); // GH-90000

            assertThat(saved.hasFailoverSupport()).isTrue(); // GH-90000
            assertThat(saved.getAllAvailableBackends()).hasSize(3); // GH-90000
        }
    }
}
