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
        void returnsSameInstanceOnMultipleCalls() { // GH-90000
            OptimizedProfileComposer composer1 = OptimizedProfileComposer.getInstance(); // GH-90000
            OptimizedProfileComposer composer2 = OptimizedProfileComposer.getInstance(); // GH-90000

            assertThat(composer1).isSameAs(composer2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Profile Composition")
    class CompositionTests {

        @Test
        @DisplayName("returns override profile when base is null")
        void returnsOverrideWhenBaseIsNull() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(null, override); // GH-90000

            assertThat(result).isSameAs(override); // GH-90000
        }

        @Test
        @DisplayName("returns base profile when override is null")
        void returnsBaseWhenOverrideIsNull() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, null); // GH-90000

            assertThat(result).isSameAs(base); // GH-90000
        }

        @Test
        @DisplayName("merges backend configurations")
        void mergesBackendConfigurations() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("max_connections", 10, "timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .backendConfig(Map.of("timeout_ms", 10000, "enable_ssl", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 10000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("enable_ssl", true); // GH-90000
        }

        @Test
        @DisplayName("merges fallback backends")
        void mergesFallbackBackends() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .fallbackBackendIds(List.of("opensearch-secondary"))
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getFallbackBackendIds()).hasSize(2); // GH-90000
            assertThat(result.getFallbackBackendIds()).contains("postgres-secondary", "opensearch-secondary"); // GH-90000
        }

        @Test
        @DisplayName("override takes precedence for primary backend")
        void overrideTakesPrecedenceForPrimary() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }

        @Test
        @DisplayName("override takes precedence for isActive")
        void overrideTakesPrecedenceForIsActive() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .isActive(false) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getIsActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("override takes precedence for priority")
        void overrideTakesPrecedenceForPriority() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .priorityOrder(10) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .priorityOrder(1) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getPriorityOrder()).isEqualTo(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Multiple Profile Composition")
    class MultipleCompositionTests {

        @Test
        @DisplayName("composes multiple profiles by priority")
        void composesMultipleByPriority() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-1")
                .primaryBackendId("postgres-primary")
                .fallbackBackendIds(List.of("postgres-secondary"))
                .priorityOrder(1) // GH-90000
                .backendConfig(Map.of("max_connections", 10)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-2")
                .primaryBackendId("clickhouse-primary")
                .fallbackBackendIds(List.of("opensearch-secondary"))
                .priorityOrder(2) // GH-90000
                .backendConfig(Map.of("timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile profile3 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("profile-3")
                .primaryBackendId("s3-primary")
                .fallbackBackendIds(List.of("s3-backup"))
                .priorityOrder(3) // GH-90000
                .backendConfig(Map.of("compression", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile1, profile2, profile3)); // GH-90000

            // Higher priority numbers take precedence: priority 3 (highest) overrides 2 and 1 // GH-90000
            assertThat(result.getPrimaryBackendId()).isEqualTo("s3-primary");
            assertThat(result.getFallbackBackendIds()).hasSize(3); // Duplicates removed during merge // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("compression", true); // GH-90000
        }

        @Test
        @DisplayName("throws exception for empty profile list")
        void throwsForEmptyList() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            assertThatThrownBy(() -> composer.composeMultiple(List.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("returns single profile when list has one element")
        void returnsSingleProfile() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("single-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile)); // GH-90000

            assertThat(result).isSameAs(profile); // GH-90000
        }
    }

    @Nested
    @DisplayName("Override Composition")
    class OverrideCompositionTests {

        @Test
        @DisplayName("composes with tenant and collection overrides")
        void composesWithOverrides() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .backendConfig(Map.of("max_connections", 10)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("tenant-profile")
                .primaryBackendId("postgres-secondary")
                .backendConfig(Map.of("timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile collection = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("collection-profile")
                .primaryBackendId("clickhouse-primary")
                .backendConfig(Map.of("compression", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, collection); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("compression", true); // GH-90000
        }

        @Test
        @DisplayName("skips null tenant override")
        void skipsNullTenantOverride() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile collection = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("collection-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, null, collection); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }

        @Test
        @DisplayName("skips null collection override")
        void skipsNullCollectionOverride() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("tenant-profile")
                .primaryBackendId("clickhouse-primary")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, null); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary");
        }
    }

    @Nested
    @DisplayName("Cache Operations")
    class CacheTests {

        @Test
        @DisplayName("caches composition results")
        void cachesCompositionResults() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.compose(base, override); // Second call should hit cache // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.cacheHits()).isEqualTo(1); // GH-90000
            assertThat(stats.cacheMisses()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("invalidates cache for specific profiles")
        void invalidatesCacheForProfiles() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.clearAllCaches(); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.mergeCacheSize()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("clears all caches")
        void clearsAllCaches() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.clearAllCaches(); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.compositionCacheSize()).isZero(); // GH-90000
            assertThat(stats.mergeCacheSize()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("tracks composition count")
        void tracksCompositionCount() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.totalCompositions()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("calculates hit rate correctly")
        void calculatesHitRateCorrectly() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("base-profile")
                .primaryBackendId("postgres-primary")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .storageProfileId("override-profile")
                .primaryBackendId("postgres-secondary")
                .build(); // GH-90000

            composer.compose(base, override); // miss // GH-90000
            composer.compose(base, override); // hit // GH-90000
            composer.compose(base, override); // hit // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0); // GH-90000
        }

        @Test
        @DisplayName("resets statistics")
        void resetsStatistics() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.resetStatistics(); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.totalCompositions()).isZero(); // GH-90000
            assertThat(stats.cacheHits()).isZero(); // GH-90000
            assertThat(stats.cacheMisses()).isZero(); // GH-90000
        }
    }
}
