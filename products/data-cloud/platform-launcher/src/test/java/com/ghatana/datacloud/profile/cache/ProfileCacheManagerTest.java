package com.ghatana.datacloud.profile.cache;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProfileCacheManager Tests [GH-90000]")
class ProfileCacheManagerTest {

    @Nested
    @DisplayName("Singleton Pattern [GH-90000]")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls [GH-90000]")
        void returnsSameInstanceOnMultipleCalls() { // GH-90000
            ProfileCacheManager instance1 = ProfileCacheManager.getInstance(); // GH-90000
            ProfileCacheManager instance2 = ProfileCacheManager.getInstance(); // GH-90000

            assertThat(instance1).isSameAs(instance2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cache Operations [GH-90000]")
    class CacheOperationsTests {

        @Test
        @DisplayName("caches and retrieves profile [GH-90000]")
        void cachesAndRetrievesProfile() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000

            var retrieved = cache.getProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getCollectionName()).isEqualTo("products [GH-90000]");
        }

        @Test
        @DisplayName("returns empty when profile not cached [GH-90000]")
        void returnsEmptyWhenNotCached() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            var retrieved = cache.getProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("caches active profile in active cache [GH-90000]")
        void cachesActiveProfileInActiveCache() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000

            var retrieved = cache.getActiveProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getIsActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("does not cache inactive profile in active cache [GH-90000]")
        void doesNotCacheInactiveProfileInActiveCache() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(false) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000

            var retrieved = cache.getActiveProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("invalidates cached profile [GH-90000]")
        void invalidatesCachedProfile() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.invalidateProfile("tenant-1", "products"); // GH-90000

            var retrieved = cache.getProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("invalidates all profiles for tenant [GH-90000]")
        void invalidatesAllProfilesForTenant() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("orders [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile1); // GH-90000
            cache.cacheProfile(profile2); // GH-90000

            int count = cache.invalidateTenantProfiles("tenant-1 [GH-90000]");

            assertThat(count).isEqualTo(2); // GH-90000
            assertThat(cache.getProfile("tenant-1", "products")).isEmpty(); // GH-90000
            assertThat(cache.getProfile("tenant-1", "orders")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("clears all cached profiles [GH-90000]")
        void clearsAllCachedProfiles() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.clearAll(); // GH-90000

            assertThat(cache.size()).isZero(); // GH-90000
            assertThat(cache.getProfile("tenant-1", "products")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("checks if profile is cached [GH-90000]")
        void checksIfProfileIsCached() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            assertThat(cache.isCached("tenant-1", "products")).isFalse(); // GH-90000

            cache.cacheProfile(profile); // GH-90000

            assertThat(cache.isCached("tenant-1", "products")).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tenant Profile Queries [GH-90000]")
    class TenantProfileTests {

        @Test
        @DisplayName("gets all profiles for tenant [GH-90000]")
        void getsAllProfilesForTenant() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("orders [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile otherTenant = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-2 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile1); // GH-90000
            cache.cacheProfile(profile2); // GH-90000
            cache.cacheProfile(otherTenant); // GH-90000

            List<CollectionStorageProfile> profiles = cache.getTenantProfiles("tenant-1 [GH-90000]");

            assertThat(profiles).hasSize(2); // GH-90000
            assertThat(profiles).allMatch(p -> p.getTenantId().equals("tenant-1 [GH-90000]"));
        }

        @Test
        @DisplayName("gets all active profiles for tenant [GH-90000]")
        void getsAllActiveProfilesForTenant() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile active1 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile active2 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("orders [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile inactive = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("customers [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(false) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(active1); // GH-90000
            cache.cacheProfile(active2); // GH-90000
            cache.cacheProfile(inactive); // GH-90000

            List<CollectionStorageProfile> activeProfiles = cache.getActiveTenantProfiles("tenant-1 [GH-90000]");

            assertThat(activeProfiles).hasSize(2); // GH-90000
            assertThat(activeProfiles).allMatch(p -> p.getIsActive()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cache Statistics [GH-90000]")
    class StatisticsTests {

        @Test
        @DisplayName("tracks cache hits and misses [GH-90000]")
        void tracksCacheHitsAndMisses() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000
            cache.resetStatistics(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.getProfile("tenant-1", "products"); // hit // GH-90000
            cache.getProfile("tenant-1", "orders"); // miss // GH-90000

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.hits()).isEqualTo(1); // GH-90000
            assertThat(stats.misses()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("calculates hit rate correctly [GH-90000]")
        void calculatesHitRateCorrectly() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000
            cache.resetStatistics(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.getProfile("tenant-1", "products"); // hit // GH-90000
            cache.getProfile("tenant-1", "products"); // hit // GH-90000
            cache.getProfile("tenant-1", "orders"); // miss // GH-90000

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0); // GH-90000
        }

        @Test
        @DisplayName("tracks invalidations [GH-90000]")
        void tracksInvalidations() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000
            cache.resetStatistics(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.invalidateProfile("tenant-1", "products"); // GH-90000

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.invalidations()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("resets statistics [GH-90000]")
        void resetsStatistics() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000
            cache.resetStatistics(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000
            cache.getProfile("tenant-1", "products"); // GH-90000
            cache.resetStatistics(); // GH-90000

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.hits()).isZero(); // GH-90000
            assertThat(stats.misses()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("returns cache size in statistics [GH-90000]")
        void returnsCacheSizeInStatistics() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            cache.cacheProfile(profile); // GH-90000

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.cacheSize()).isEqualTo(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Profile Updates [GH-90000]")
    class ProfileUpdateTests {

        @Test
        @DisplayName("updates profile cache on profile change [GH-90000]")
        void updatesProfileCacheOnProfileChange() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile original = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(original); // GH-90000

            CollectionStorageProfile updated = CollectionStorageProfile.builder() // GH-90000
                .id(original.getId()) // GH-90000
                .tenantId(original.getTenantId()) // GH-90000
                .collectionName(original.getCollectionName()) // GH-90000
                .storageProfileId(original.getStorageProfileId()) // GH-90000
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .fallbackBackendIds(original.getFallbackBackendIds()) // GH-90000
                .backendConfig(original.getBackendConfig()) // GH-90000
                .isActive(false) // GH-90000
                .priorityOrder(original.getPriorityOrder()) // GH-90000
                .createdAt(original.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(updated); // GH-90000

            var retrieved = cache.getProfile("tenant-1", "products"); // GH-90000

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getPrimaryBackendId()).isEqualTo("clickhouse-primary [GH-90000]");
            assertThat(retrieved.get().getIsActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("removes from active cache when profile deactivated [GH-90000]")
        void removesFromActiveCacheWhenDeactivated() { // GH-90000
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); // GH-90000
            cache.clearAll(); // GH-90000

            CollectionStorageProfile active = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("hot-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(active); // GH-90000

            CollectionStorageProfile deactivated = CollectionStorageProfile.builder() // GH-90000
                .id(active.getId()) // GH-90000
                .tenantId(active.getTenantId()) // GH-90000
                .collectionName(active.getCollectionName()) // GH-90000
                .storageProfileId(active.getStorageProfileId()) // GH-90000
                .primaryBackendId(active.getPrimaryBackendId()) // GH-90000
                .fallbackBackendIds(active.getFallbackBackendIds()) // GH-90000
                .backendConfig(active.getBackendConfig()) // GH-90000
                .isActive(false) // GH-90000
                .priorityOrder(active.getPriorityOrder()) // GH-90000
                .createdAt(active.getCreatedAt()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .build(); // GH-90000

            cache.cacheProfile(deactivated); // GH-90000

            var activeRetrieved = cache.getActiveProfile("tenant-1", "products"); // GH-90000
            var allRetrieved = cache.getProfile("tenant-1", "products"); // GH-90000

            assertThat(activeRetrieved).isEmpty(); // GH-90000
            assertThat(allRetrieved).isPresent(); // GH-90000
        }
    }
}
