package com.ghatana.datacloud.profile.cache;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProfileCacheManager Tests")
class ProfileCacheManagerTest {

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { 
            ProfileCacheManager instance1 = ProfileCacheManager.getInstance(); 
            ProfileCacheManager instance2 = ProfileCacheManager.getInstance(); 

            assertThat(instance1).isSameAs(instance2); 
        }
    }

    @Nested
    @DisplayName("Cache Operations")
    class CacheOperationsTests {

        @Test
        @DisplayName("caches and retrieves profile")
        void cachesAndRetrievesProfile() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 

            var retrieved = cache.getProfile("tenant-1", "products"); 

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getCollectionName()).isEqualTo("products");
        }

        @Test
        @DisplayName("returns empty when profile not cached")
        void returnsEmptyWhenNotCached() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            var retrieved = cache.getProfile("tenant-1", "products"); 

            assertThat(retrieved).isEmpty(); 
        }

        @Test
        @DisplayName("caches active profile in active cache")
        void cachesActiveProfileInActiveCache() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            cache.cacheProfile(profile); 

            var retrieved = cache.getActiveProfile("tenant-1", "products"); 

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getIsActive()).isTrue(); 
        }

        @Test
        @DisplayName("does not cache inactive profile in active cache")
        void doesNotCacheInactiveProfileInActiveCache() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(false) 
                .build(); 

            cache.cacheProfile(profile); 

            var retrieved = cache.getActiveProfile("tenant-1", "products"); 

            assertThat(retrieved).isEmpty(); 
        }

        @Test
        @DisplayName("invalidates cached profile")
        void invalidatesCachedProfile() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.invalidateProfile("tenant-1", "products"); 

            var retrieved = cache.getProfile("tenant-1", "products"); 

            assertThat(retrieved).isEmpty(); 
        }

        @Test
        @DisplayName("invalidates all profiles for tenant")
        void invalidatesAllProfilesForTenant() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("orders")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile1); 
            cache.cacheProfile(profile2); 

            int count = cache.invalidateTenantProfiles("tenant-1");

            assertThat(count).isEqualTo(2); 
            assertThat(cache.getProfile("tenant-1", "products")).isEmpty(); 
            assertThat(cache.getProfile("tenant-1", "orders")).isEmpty(); 
        }

        @Test
        @DisplayName("clears all cached profiles")
        void clearsAllCachedProfiles() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.clearAll(); 

            assertThat(cache.size()).isZero(); 
            assertThat(cache.getProfile("tenant-1", "products")).isEmpty(); 
        }

        @Test
        @DisplayName("checks if profile is cached")
        void checksIfProfileIsCached() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            assertThat(cache.isCached("tenant-1", "products")).isFalse(); 

            cache.cacheProfile(profile); 

            assertThat(cache.isCached("tenant-1", "products")).isTrue(); 
        }
    }

    @Nested
    @DisplayName("Tenant Profile Queries")
    class TenantProfileTests {

        @Test
        @DisplayName("gets all profiles for tenant")
        void getsAllProfilesForTenant() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("orders")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile otherTenant = CollectionStorageProfile.builder() 
                .tenantId("tenant-2")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile1); 
            cache.cacheProfile(profile2); 
            cache.cacheProfile(otherTenant); 

            List<CollectionStorageProfile> profiles = cache.getTenantProfiles("tenant-1");

            assertThat(profiles).hasSize(2); 
            assertThat(profiles).allMatch(p -> p.getTenantId().equals("tenant-1"));
        }

        @Test
        @DisplayName("gets all active profiles for tenant")
        void getsAllActiveProfilesForTenant() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile active1 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            CollectionStorageProfile active2 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("orders")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            CollectionStorageProfile inactive = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("customers")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(false) 
                .build(); 

            cache.cacheProfile(active1); 
            cache.cacheProfile(active2); 
            cache.cacheProfile(inactive); 

            List<CollectionStorageProfile> activeProfiles = cache.getActiveTenantProfiles("tenant-1");

            assertThat(activeProfiles).hasSize(2); 
            assertThat(activeProfiles).allMatch(p -> p.getIsActive()); 
        }
    }

    @Nested
    @DisplayName("Cache Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("tracks cache hits and misses")
        void tracksCacheHitsAndMisses() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 
            cache.resetStatistics(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.getProfile("tenant-1", "products"); // hit 
            cache.getProfile("tenant-1", "orders"); // miss 

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.hits()).isEqualTo(1); 
            assertThat(stats.misses()).isEqualTo(1); 
        }

        @Test
        @DisplayName("calculates hit rate correctly")
        void calculatesHitRateCorrectly() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 
            cache.resetStatistics(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.getProfile("tenant-1", "products"); // hit 
            cache.getProfile("tenant-1", "products"); // hit 
            cache.getProfile("tenant-1", "orders"); // miss 

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0); 
        }

        @Test
        @DisplayName("tracks invalidations")
        void tracksInvalidations() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 
            cache.resetStatistics(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.invalidateProfile("tenant-1", "products"); 

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.invalidations()).isEqualTo(1); 
        }

        @Test
        @DisplayName("resets statistics")
        void resetsStatistics() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 
            cache.resetStatistics(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 
            cache.getProfile("tenant-1", "products"); 
            cache.resetStatistics(); 

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.hits()).isZero(); 
            assertThat(stats.misses()).isZero(); 
        }

        @Test
        @DisplayName("returns cache size in statistics")
        void returnsCacheSizeInStatistics() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            cache.cacheProfile(profile); 

            ProfileCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.cacheSize()).isEqualTo(1); 
        }
    }

    @Nested
    @DisplayName("Profile Updates")
    class ProfileUpdateTests {

        @Test
        @DisplayName("updates profile cache on profile change")
        void updatesProfileCacheOnProfileChange() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile original = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            cache.cacheProfile(original); 

            CollectionStorageProfile updated = CollectionStorageProfile.builder() 
                .id(original.getId()) 
                .tenantId(original.getTenantId()) 
                .collectionName(original.getCollectionName()) 
                .storageProfileId(original.getStorageProfileId()) 
                .primaryBackendId("clickhouse-primary")
                .fallbackBackendIds(original.getFallbackBackendIds()) 
                .backendConfig(original.getBackendConfig()) 
                .isActive(false) 
                .priorityOrder(original.getPriorityOrder()) 
                .createdAt(original.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            cache.cacheProfile(updated); 

            var retrieved = cache.getProfile("tenant-1", "products"); 

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getPrimaryBackendId()).isEqualTo("clickhouse-primary");
            assertThat(retrieved.get().getIsActive()).isFalse(); 
        }

        @Test
        @DisplayName("removes from active cache when profile deactivated")
        void removesFromActiveCacheWhenDeactivated() { 
            ProfileCacheManager cache = ProfileCacheManager.getInstance(); 
            cache.clearAll(); 

            CollectionStorageProfile active = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("hot-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            cache.cacheProfile(active); 

            CollectionStorageProfile deactivated = CollectionStorageProfile.builder() 
                .id(active.getId()) 
                .tenantId(active.getTenantId()) 
                .collectionName(active.getCollectionName()) 
                .storageProfileId(active.getStorageProfileId()) 
                .primaryBackendId(active.getPrimaryBackendId()) 
                .fallbackBackendIds(active.getFallbackBackendIds()) 
                .backendConfig(active.getBackendConfig()) 
                .isActive(false) 
                .priorityOrder(active.getPriorityOrder()) 
                .createdAt(active.getCreatedAt()) 
                .updatedAt(Instant.now()) 
                .build(); 

            cache.cacheProfile(deactivated); 

            var activeRetrieved = cache.getActiveProfile("tenant-1", "products"); 
            var allRetrieved = cache.getProfile("tenant-1", "products"); 

            assertThat(activeRetrieved).isEmpty(); 
            assertThat(allRetrieved).isPresent(); 
        }
    }
}
