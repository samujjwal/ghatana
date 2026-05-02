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
        void switchProfileToInactive() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile activeProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .priorityOrder(1) 
                .build(); 

            runPromise(() -> repository.save(activeProfile)); 

            // Switch to inactive
            CollectionStorageProfile inactiveProfile = CollectionStorageProfile.builder() 
                .id(activeProfile.getId()) 
                .tenantId(activeProfile.getTenantId()) 
                .collectionName(activeProfile.getCollectionName()) 
                .storageProfileId(activeProfile.getStorageProfileId()) 
                .primaryBackendId(activeProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(activeProfile.getFallbackBackendIds()) 
                .backendConfig(activeProfile.getBackendConfig()) 
                .isActive(false) 
                .priorityOrder(activeProfile.getPriorityOrder()) 
                .createdAt(activeProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(inactiveProfile)); 

            assertThat(saved.getIsActive()).isFalse(); 
            assertThat(saved.getUpdatedAt()).isNotEqualTo(activeProfile.getUpdatedAt()); 
        }

        @Test
        @DisplayName("switch profile from inactive to active")
        void switchProfileToActive() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile inactiveProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(false) 
                .priorityOrder(1) 
                .build(); 

            runPromise(() -> repository.save(inactiveProfile)); 

            // Switch to active
            CollectionStorageProfile activeProfile = CollectionStorageProfile.builder() 
                .id(inactiveProfile.getId()) 
                .tenantId(inactiveProfile.getTenantId()) 
                .collectionName(inactiveProfile.getCollectionName()) 
                .storageProfileId(inactiveProfile.getStorageProfileId()) 
                .primaryBackendId(inactiveProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(inactiveProfile.getFallbackBackendIds()) 
                .backendConfig(inactiveProfile.getBackendConfig()) 
                .isActive(true) 
                .priorityOrder(inactiveProfile.getPriorityOrder()) 
                .createdAt(inactiveProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(activeProfile)); 

            assertThat(saved.getIsActive()).isTrue(); 
        }

        @Test
        @DisplayName("inactive profile returns no available backends")
        void inactiveProfileReturnsNoBackends() { 
            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .isActive(false) 
                .build(); 

            assertThat(profile.getAllAvailableBackends()).isEmpty(); 
            assertThat(profile.supportsBackend("postgres-primary")).isFalse();
        }
    }

    @Nested
    @DisplayName("Primary Backend Switching")
    class PrimaryBackendTests {

        @Test
        @DisplayName("switch primary backend within profile")
        void switchPrimaryBackend() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            // Switch primary backend
            CollectionStorageProfile switchedProfile = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId("postgres-secondary")
                .fallbackBackendIds(List.of("postgres-primary"))
                .backendConfig(originalProfile.getBackendConfig()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(switchedProfile)); 

            assertThat(saved.getPrimaryBackendId()).isEqualTo("postgres-secondary");
            assertThat(saved.getFallbackBackendIds()).containsExactly("postgres-primary");
        }

        @Test
        @DisplayName("switch to new primary backend without fallbacks")
        void switchToNewPrimaryWithoutFallbacks() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            CollectionStorageProfile switchedProfile = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId("clickhouse-primary")
                .fallbackBackendIds(List.of()) 
                .backendConfig(originalProfile.getBackendConfig()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(switchedProfile)); 

            assertThat(saved.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
            assertThat(saved.getFallbackBackendIds()).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Fallback Backend Switching")
    class FallbackBackendTests {

        @Test
        @DisplayName("add fallback backend to profile")
        void addFallbackBackend() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of()) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            CollectionStorageProfile withFallback = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId(originalProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) 
                .backendConfig(originalProfile.getBackendConfig()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(withFallback)); 

            assertThat(saved.getFallbackBackendIds()).hasSize(2); 
            assertThat(saved.hasFailoverSupport()).isTrue(); 
        }

        @Test
        @DisplayName("remove fallback backend from profile")
        void removeFallbackBackend() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            CollectionStorageProfile withoutFallback = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId(originalProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(List.of("postgres-secondary"))
                .backendConfig(originalProfile.getBackendConfig()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(withoutFallback)); 

            assertThat(saved.getFallbackBackendIds()).hasSize(1); 
            assertThat(saved.getFallbackBackendIds()).containsExactly("postgres-secondary");
        }

        @Test
        @DisplayName("clear all fallback backends")
        void clearAllFallbackBackends() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            CollectionStorageProfile noFallback = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId(originalProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(List.of()) 
                .backendConfig(originalProfile.getBackendConfig()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(noFallback)); 

            assertThat(saved.getFallbackBackendIds()).isEmpty(); 
            assertThat(saved.hasFailoverSupport()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("Priority Order Switching")
    class PriorityOrderTests {

        @Test
        @DisplayName("increase profile priority")
        void increaseProfilePriority() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile lowPriorityProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(10) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(lowPriorityProfile)); 

            CollectionStorageProfile highPriorityProfile = CollectionStorageProfile.builder() 
                .id(lowPriorityProfile.getId()) 
                .tenantId(lowPriorityProfile.getTenantId()) 
                .collectionName(lowPriorityProfile.getCollectionName()) 
                .storageProfileId(lowPriorityProfile.getStorageProfileId()) 
                .primaryBackendId(lowPriorityProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(lowPriorityProfile.getFallbackBackendIds()) 
                .backendConfig(lowPriorityProfile.getBackendConfig()) 
                .isActive(lowPriorityProfile.getIsActive()) 
                .priorityOrder(1) 
                .createdAt(lowPriorityProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(highPriorityProfile)); 

            assertThat(saved.getPriorityOrder()).isEqualTo(1); 
        }

        @Test
        @DisplayName("decrease profile priority")
        void decreaseProfilePriority() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile highPriorityProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(1) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(highPriorityProfile)); 

            CollectionStorageProfile lowPriorityProfile = CollectionStorageProfile.builder() 
                .id(highPriorityProfile.getId()) 
                .tenantId(highPriorityProfile.getTenantId()) 
                .collectionName(highPriorityProfile.getCollectionName()) 
                .storageProfileId(highPriorityProfile.getStorageProfileId()) 
                .primaryBackendId(highPriorityProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(highPriorityProfile.getFallbackBackendIds()) 
                .backendConfig(highPriorityProfile.getBackendConfig()) 
                .isActive(highPriorityProfile.getIsActive()) 
                .priorityOrder(10) 
                .createdAt(highPriorityProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(lowPriorityProfile)); 

            assertThat(saved.getPriorityOrder()).isEqualTo(10); 
        }
    }

    @Nested
    @DisplayName("Configuration Switching")
    class ConfigurationTests {

        @Test
        @DisplayName("switch backend configuration")
        void switchBackendConfiguration() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            Map<String, Object> originalConfig = Map.of( 
                "max_connections", 10,
                "timeout_ms", 5000
            );

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(originalConfig) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            Map<String, Object> newConfig = Map.of( 
                "max_connections", 20,
                "timeout_ms", 10000,
                "enable_ssl", true
            );

            CollectionStorageProfile newProfile = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId(originalProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(originalProfile.getFallbackBackendIds()) 
                .backendConfig(newConfig) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(newProfile)); 

            assertThat(saved.getBackendConfig()).containsEntry("max_connections", 20); 
            assertThat(saved.getBackendConfig()).containsEntry("enable_ssl", true); 
            assertThat(saved.getBackendConfig()).doesNotContainValue(5000); 
        }

        @Test
        @DisplayName("clear backend configuration")
        void clearBackendConfiguration() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            Map<String, Object> originalConfig = Map.of( 
                "max_connections", 10,
                "timeout_ms", 5000
            );

            CollectionStorageProfile originalProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(originalConfig) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(originalProfile)); 

            CollectionStorageProfile clearedProfile = CollectionStorageProfile.builder() 
                .id(originalProfile.getId()) 
                .tenantId(originalProfile.getTenantId()) 
                .collectionName(originalProfile.getCollectionName()) 
                .storageProfileId(originalProfile.getStorageProfileId()) 
                .primaryBackendId(originalProfile.getPrimaryBackendId()) 
                .fallbackBackendIds(originalProfile.getFallbackBackendIds()) 
                .backendConfig(Map.of()) 
                .isActive(originalProfile.getIsActive()) 
                .priorityOrder(originalProfile.getPriorityOrder()) 
                .createdAt(originalProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(clearedProfile)); 

            assertThat(saved.getBackendConfig()).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Switching Scenarios")
    class SwitchingScenarios {

        @Test
        @DisplayName("switch from hot to cold profile")
        void switchFromHotToColdProfile() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile hotProfile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("redis-hot")
                .fallbackBackendIds(List.of()) 
                .backendConfig(Map.of("latency_target_ms", 10)) 
                .isActive(true) 
                .priorityOrder(1) 
                .build(); 

            runPromise(() -> repository.save(hotProfile)); 

            CollectionStorageProfile coldProfile = CollectionStorageProfile.builder() 
                .id(hotProfile.getId()) 
                .tenantId(hotProfile.getTenantId()) 
                .collectionName(hotProfile.getCollectionName()) 
                .storageProfileId("cold-profile")
                .primaryBackendId("s3-cold")
                .fallbackBackendIds(List.of("s3-backup"))
                .backendConfig(Map.of("latency_target_ms", 1000, "compression", true)) 
                .isActive(true) 
                .priorityOrder(2) 
                .createdAt(hotProfile.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(coldProfile)); 

            assertThat(saved.getStorageProfileId()).isEqualTo("cold-profile");
            assertThat(saved.getPrimaryBackendId()).isEqualTo("s3-cold");
            assertThat(saved.getBackendConfig()).containsEntry("compression", true); 
        }

        @Test
        @DisplayName("switch with failover support enabled")
        void switchWithFailoverEnabled() { 
            InMemoryCollectionStorageProfileRepository repository = new InMemoryCollectionStorageProfileRepository(); 

            CollectionStorageProfile noFailover = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of()) 
                .isActive(true) 
                .build(); 

            runPromise(() -> repository.save(noFailover)); 

            CollectionStorageProfile withFailover = CollectionStorageProfile.builder() 
                .id(noFailover.getId()) 
                .tenantId(noFailover.getTenantId()) 
                .collectionName(noFailover.getCollectionName()) 
                .storageProfileId(noFailover.getStorageProfileId()) 
                .primaryBackendId(noFailover.getPrimaryBackendId()) 
                .fallbackBackendIds(List.of("postgres-secondary", "opensearch-secondary")) 
                .backendConfig(noFailover.getBackendConfig()) 
                .isActive(noFailover.getIsActive()) 
                .priorityOrder(noFailover.getPriorityOrder()) 
                .createdAt(noFailover.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            CollectionStorageProfile saved = runPromise(() -> repository.save(withFailover)); 

            assertThat(saved.hasFailoverSupport()).isTrue(); 
            assertThat(saved.getAllAvailableBackends()).hasSize(3); 
        }
    }
}
