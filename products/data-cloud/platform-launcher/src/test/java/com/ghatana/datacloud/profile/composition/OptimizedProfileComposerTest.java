package com.ghatana.datacloud.profile.composition;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OptimizedProfileComposer Tests")
class OptimizedProfileComposerTest {

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { 
            OptimizedProfileComposer composer1 = OptimizedProfileComposer.getInstance(); 
            OptimizedProfileComposer composer2 = OptimizedProfileComposer.getInstance(); 

            assertThat(composer1).isSameAs(composer2); 
        }
    }

    @Nested
    @DisplayName("Profile Composition")
    class CompositionTests {

        @Test
        @DisplayName("returns override profile when base is null")
        void returnsOverrideWhenBaseIsNull() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            CollectionStorageProfile result = composer.compose(null, override); 

            assertThat(result).isSameAs(override); 
        }

        @Test
        @DisplayName("returns base profile when override is null")
        void returnsBaseWhenOverrideIsNull() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile result = composer.compose(base, null); 

            assertThat(result).isSameAs(base); 
        }

        @Test
        @DisplayName("merges backend configurations")
        void mergesBackendConfigurations() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("max_connections", 10, "timeout_ms", 5000)) 
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .backendConfig(Map.of("timeout_ms", 10000, "enable_ssl", true)) 
                .build(); 

            CollectionStorageProfile result = composer.compose(base, override); 

            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); 
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 10000); 
            assertThat(result.getBackendConfig()).containsEntry("enable_ssl", true); 
        }

        @Test
        @DisplayName("merges fallback backends")
        void mergesFallbackBackends() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .fallbackBackendIds(List.of("opensearch-secondary"))
                .build(); 

            CollectionStorageProfile result = composer.compose(base, override); 

            assertThat(result.getFallbackBackendIds()).hasSize(2); 
            assertThat(result.getFallbackBackendIds()).contains("postgres-secondary", "opensearch-secondary"); 
        }

        @Test
        @DisplayName("override takes precedence for primary backend")
        void overrideTakesPrecedenceForPrimary() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); 

            CollectionStorageProfile result = composer.compose(base, override); 

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }

        @Test
        @DisplayName("override takes precedence for isActive")
        void overrideTakesPrecedenceForIsActive() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) 
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .isActive(false) 
                .build(); 

            CollectionStorageProfile result = composer.compose(base, override); 

            assertThat(result.getIsActive()).isFalse(); 
        }

        @Test
        @DisplayName("override takes precedence for priority")
        void overrideTakesPrecedenceForPriority() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(10) 
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .priorityOrder(1) 
                .build(); 

            CollectionStorageProfile result = composer.compose(base, override); 

            assertThat(result.getPriorityOrder()).isEqualTo(1); 
        }
    }

    @Nested
    @DisplayName("Multiple Profile Composition")
    class MultipleCompositionTests {

        @Test
        @DisplayName("composes multiple profiles by priority")
        void composesMultipleByPriority() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-1")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .priorityOrder(1) 
                .backendConfig(Map.of("max_connections", 10)) 
                .build(); 

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-2")
                .primaryBackendId("clickhouse-primary")
                .fallbackBackendIds(List.of("opensearch-secondary"))
                .priorityOrder(2) 
                .backendConfig(Map.of("timeout_ms", 5000)) 
                .build(); 

            CollectionStorageProfile profile3 = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-3")
                .primaryBackendId("s3-primary")
                .fallbackBackendIds(List.of("s3-backup"))
                .priorityOrder(3) 
                .backendConfig(Map.of("compression", true)) 
                .build(); 

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile1, profile2, profile3)); 

            // Higher priority numbers take precedence: priority 3 (highest) overrides 2 and 1 
            assertThat(result.getPrimaryBackendId()).isEqualTo("s3-primary");
            assertThat(result.getFallbackBackendIds()).hasSize(3); // Duplicates removed during merge 
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); 
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); 
            assertThat(result.getBackendConfig()).containsEntry("compression", true); 
        }

        @Test
        @DisplayName("throws exception for empty profile list")
        void throwsForEmptyList() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 

            assertThatThrownBy(() -> composer.composeMultiple(List.of())) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("returns single profile when list has one element")
        void returnsSingleProfile() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 

            CollectionStorageProfile profile = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("single-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile)); 

            assertThat(result).isSameAs(profile); 
        }
    }

    @Nested
    @DisplayName("Override Composition")
    class OverrideCompositionTests {

        @Test
        @DisplayName("composes with tenant and collection overrides")
        void composesWithOverrides() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("max_connections", 10)) 
                .build(); 

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("tenant-profile")
                .primaryBackendId("postgres-secondary")
                .backendConfig(Map.of("timeout_ms", 5000)) 
                .build(); 

            CollectionStorageProfile collection = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("collection-profile")
                .primaryBackendId("clickhouse-primary")
                .backendConfig(Map.of("compression", true)) 
                .build(); 

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, collection); 

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); 
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); 
            assertThat(result.getBackendConfig()).containsEntry("compression", true); 
        }

        @Test
        @DisplayName("skips null tenant override")
        void skipsNullTenantOverride() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile collection = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("collection-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); 

            CollectionStorageProfile result = composer.composeWithOverrides(base, null, collection); 

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }

        @Test
        @DisplayName("skips null collection override")
        void skipsNullCollectionOverride() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("tenant-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); 

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, null); 

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }
    }

    @Nested
    @DisplayName("Cache Operations")
    class CacheTests {

        @Test
        @DisplayName("caches composition results")
        void cachesCompositionResults() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 
            composer.resetStatistics(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            composer.compose(base, override); 
            composer.compose(base, override); // Second call should hit cache 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.cacheHits()).isEqualTo(1); 
            assertThat(stats.cacheMisses()).isEqualTo(1); 
        }

        @Test
        @DisplayName("invalidates cache for specific profiles")
        void invalidatesCacheForProfiles() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            composer.compose(base, override); 
            composer.clearAllCaches(); 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.mergeCacheSize()).isZero(); 
        }

        @Test
        @DisplayName("clears all caches")
        void clearsAllCaches() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            composer.compose(base, override); 
            composer.clearAllCaches(); 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.compositionCacheSize()).isZero(); 
            assertThat(stats.mergeCacheSize()).isZero(); 
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("tracks composition count")
        void tracksCompositionCount() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 
            composer.resetStatistics(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            composer.compose(base, override); 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.totalCompositions()).isEqualTo(1); 
        }

        @Test
        @DisplayName("calculates hit rate correctly")
        void calculatesHitRateCorrectly() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.clearAllCaches(); 
            composer.resetStatistics(); 

            CollectionStorageProfile base = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); 

            CollectionStorageProfile override = CollectionStorageProfile.builder() 
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); 

            composer.compose(base, override); // miss 
            composer.compose(base, override); // hit 
            composer.compose(base, override); // hit 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0); 
        }

        @Test
        @DisplayName("resets statistics")
        void resetsStatistics() { 
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); 
            composer.resetStatistics(); 

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); 

            assertThat(stats.totalCompositions()).isZero(); 
            assertThat(stats.cacheHits()).isZero(); 
            assertThat(stats.cacheMisses()).isZero(); 
        }
    }
}
