package com.ghatana.datacloud.profile.composition;

import com.ghatana.datacloud.entity.storage.CollectionStorageProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OptimizedProfileComposer Tests [GH-90000]")
class OptimizedProfileComposerTest {

    @Nested
    @DisplayName("Singleton Pattern [GH-90000]")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls [GH-90000]")
        void returnsSameInstanceOnMultipleCalls() { // GH-90000
            OptimizedProfileComposer composer1 = OptimizedProfileComposer.getInstance(); // GH-90000
            OptimizedProfileComposer composer2 = OptimizedProfileComposer.getInstance(); // GH-90000

            assertThat(composer1).isSameAs(composer2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Profile Composition [GH-90000]")
    class CompositionTests {

        @Test
        @DisplayName("returns override profile when base is null [GH-90000]")
        void returnsOverrideWhenBaseIsNull() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(null, override); // GH-90000

            assertThat(result).isSameAs(override); // GH-90000
        }

        @Test
        @DisplayName("returns base profile when override is null [GH-90000]")
        void returnsBaseWhenOverrideIsNull() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, null); // GH-90000

            assertThat(result).isSameAs(base); // GH-90000
        }

        @Test
        @DisplayName("merges backend configurations [GH-90000]")
        void mergesBackendConfigurations() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .backendConfig(Map.of("max_connections", 10, "timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .backendConfig(Map.of("timeout_ms", 10000, "enable_ssl", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 10000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("enable_ssl", true); // GH-90000
        }

        @Test
        @DisplayName("merges fallback backends [GH-90000]")
        void mergesFallbackBackends() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .fallbackBackendIds(List.of("postgres-secondary [GH-90000]"))
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .fallbackBackendIds(List.of("opensearch-secondary [GH-90000]"))
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getFallbackBackendIds()).hasSize(2); // GH-90000
            assertThat(result.getFallbackBackendIds()).contains("postgres-secondary", "opensearch-secondary"); // GH-90000
        }

        @Test
        @DisplayName("override takes precedence for primary backend [GH-90000]")
        void overrideTakesPrecedenceForPrimary() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary [GH-90000]");
        }

        @Test
        @DisplayName("override takes precedence for isActive [GH-90000]")
        void overrideTakesPrecedenceForIsActive() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .isActive(true) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .isActive(false) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getIsActive()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("override takes precedence for priority [GH-90000]")
        void overrideTakesPrecedenceForPriority() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .priorityOrder(10) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .priorityOrder(1) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.compose(base, override); // GH-90000

            assertThat(result.getPriorityOrder()).isEqualTo(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Multiple Profile Composition [GH-90000]")
    class MultipleCompositionTests {

        @Test
        @DisplayName("composes multiple profiles by priority [GH-90000]")
        void composesMultipleByPriority() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile profile1 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("profile-1 [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .fallbackBackendIds(List.of("postgres-secondary [GH-90000]"))
                .priorityOrder(1) // GH-90000
                .backendConfig(Map.of("max_connections", 10)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile profile2 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("profile-2 [GH-90000]")
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .fallbackBackendIds(List.of("opensearch-secondary [GH-90000]"))
                .priorityOrder(2) // GH-90000
                .backendConfig(Map.of("timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile profile3 = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("profile-3 [GH-90000]")
                .primaryBackendId("s3-primary [GH-90000]")
                .fallbackBackendIds(List.of("s3-backup [GH-90000]"))
                .priorityOrder(3) // GH-90000
                .backendConfig(Map.of("compression", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile1, profile2, profile3)); // GH-90000

            // Higher priority numbers take precedence: priority 3 (highest) overrides 2 and 1 // GH-90000
            assertThat(result.getPrimaryBackendId()).isEqualTo("s3-primary [GH-90000]");
            assertThat(result.getFallbackBackendIds()).hasSize(3); // Duplicates removed during merge // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("compression", true); // GH-90000
        }

        @Test
        @DisplayName("throws exception for empty profile list [GH-90000]")
        void throwsForEmptyList() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            assertThatThrownBy(() -> composer.composeMultiple(List.of())) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("cannot be null or empty [GH-90000]");
        }

        @Test
        @DisplayName("returns single profile when list has one element [GH-90000]")
        void returnsSingleProfile() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile profile = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("single-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeMultiple(List.of(profile)); // GH-90000

            assertThat(result).isSameAs(profile); // GH-90000
        }
    }

    @Nested
    @DisplayName("Override Composition [GH-90000]")
    class OverrideCompositionTests {

        @Test
        @DisplayName("composes with tenant and collection overrides [GH-90000]")
        void composesWithOverrides() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .backendConfig(Map.of("max_connections", 10)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("tenant-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .backendConfig(Map.of("timeout_ms", 5000)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile collection = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("collection-profile [GH-90000]")
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .backendConfig(Map.of("compression", true)) // GH-90000
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, collection); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary [GH-90000]");
            assertThat(result.getBackendConfig()).containsEntry("max_connections", 10); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("timeout_ms", 5000); // GH-90000
            assertThat(result.getBackendConfig()).containsEntry("compression", true); // GH-90000
        }

        @Test
        @DisplayName("skips null tenant override [GH-90000]")
        void skipsNullTenantOverride() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile collection = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("collection-profile [GH-90000]")
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, null, collection); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary [GH-90000]");
        }

        @Test
        @DisplayName("skips null collection override [GH-90000]")
        void skipsNullCollectionOverride() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile tenant = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("tenant-profile [GH-90000]")
                .primaryBackendId("clickhouse-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile result = composer.composeWithOverrides(base, tenant, null); // GH-90000

            assertThat(result.getPrimaryBackendId()).isEqualTo("clickhouse-primary [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Cache Operations [GH-90000]")
    class CacheTests {

        @Test
        @DisplayName("caches composition results [GH-90000]")
        void cachesCompositionResults() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.compose(base, override); // Second call should hit cache // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.cacheHits()).isEqualTo(1); // GH-90000
            assertThat(stats.cacheMisses()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("invalidates cache for specific profiles [GH-90000]")
        void invalidatesCacheForProfiles() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.clearAllCaches(); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.mergeCacheSize()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("clears all caches [GH-90000]")
        void clearsAllCaches() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000
            composer.clearAllCaches(); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.compositionCacheSize()).isZero(); // GH-90000
            assertThat(stats.mergeCacheSize()).isZero(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Statistics [GH-90000]")
    class StatisticsTests {

        @Test
        @DisplayName("tracks composition count [GH-90000]")
        void tracksCompositionCount() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            composer.compose(base, override); // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.totalCompositions()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("calculates hit rate correctly [GH-90000]")
        void calculatesHitRateCorrectly() { // GH-90000
            OptimizedProfileComposer composer = OptimizedProfileComposer.getInstance(); // GH-90000
            composer.clearAllCaches(); // GH-90000
            composer.resetStatistics(); // GH-90000

            CollectionStorageProfile base = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("base-profile [GH-90000]")
                .primaryBackendId("postgres-primary [GH-90000]")
                .build(); // GH-90000

            CollectionStorageProfile override = CollectionStorageProfile.builder() // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .storageProfileId("override-profile [GH-90000]")
                .primaryBackendId("postgres-secondary [GH-90000]")
                .build(); // GH-90000

            composer.compose(base, override); // miss // GH-90000
            composer.compose(base, override); // hit // GH-90000
            composer.compose(base, override); // hit // GH-90000

            OptimizedProfileComposer.CompositionStatistics stats = composer.getStatistics(); // GH-90000

            assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0); // GH-90000
        }

        @Test
        @DisplayName("resets statistics [GH-90000]")
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
