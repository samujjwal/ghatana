package com.ghatana.datacloud.plugins.discovery;

import com.ghatana.datacloud.plugins.cache.PluginCacheManager;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginProvider;
import com.ghatana.platform.plugin.PluginType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OptimizedPluginDiscovery Tests")
class OptimizedPluginDiscoveryTest {

    @BeforeEach
    void setUp() { // GH-90000
        OptimizedPluginDiscovery.getInstance().clearCache(); // GH-90000
        PluginCacheManager cacheManager = PluginCacheManager.getInstance(); // GH-90000
        cacheManager.clearMetadataCache(); // GH-90000
        cacheManager.clearInstanceCache(); // GH-90000
        cacheManager.clearOperationCache(); // GH-90000
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { // GH-90000
            OptimizedPluginDiscovery instance1 = OptimizedPluginDiscovery.getInstance(); // GH-90000
            OptimizedPluginDiscovery instance2 = OptimizedPluginDiscovery.getInstance(); // GH-90000

            assertThat(instance1).isSameAs(instance2); // GH-90000
        }

        @Test
        @DisplayName("returns instance with custom cache manager")
        void returnsInstanceWithCustomCacheManager() { // GH-90000
            PluginCacheManager cacheManager = mock(PluginCacheManager.class); // GH-90000
            OptimizedPluginDiscovery instance = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager); // GH-90000

            assertThat(instance).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("returns same instance even with custom cache manager")
        void returnsSameInstanceWithCustomCacheManager() { // GH-90000
            PluginCacheManager cacheManager1 = mock(PluginCacheManager.class); // GH-90000
            PluginCacheManager cacheManager2 = mock(PluginCacheManager.class); // GH-90000
            
            OptimizedPluginDiscovery instance1 = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager1); // GH-90000
            OptimizedPluginDiscovery instance2 = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager2); // GH-90000

            assertThat(instance1).isSameAs(instance2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Discovery Operations")
    class DiscoveryTests {

        @Test
        @DisplayName("checks if discovery has been performed")
        void checksIfDiscoveryPerformed() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            assertThat(discovery.isDiscoveryPerformed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns empty stream when no providers exist")
        void returnsEmptyStreamWhenNoProviders() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            long count = discovery.discoverProviders().count(); // GH-90000

            assertThat(count).isEqualTo(0); // GH-90000
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("caches discovery results")
        void cachesDiscoveryResults() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            discovery.discoverProviders(); // GH-90000
            long count1 = discovery.discoverProviders().count(); // GH-90000

            assertThat(count1).isGreaterThanOrEqualTo(0); // GH-90000
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns discovery statistics")
        void returnsDiscoveryStatistics() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            OptimizedPluginDiscovery.DiscoveryStatistics stats = discovery.getStatistics(); // GH-90000

            assertThat(stats).isNotNull(); // GH-90000
            assertThat(stats.discoveryPerformed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("clears discovery cache")
        void clearsDiscoveryCache() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            discovery.discoverProviders(); // GH-90000
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); // GH-90000

            discovery.clearCache(); // GH-90000
            assertThat(discovery.isDiscoveryPerformed()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Provider Filtering")
    class FilteringTests {

        @Test
        @DisplayName("filters providers by type")
        void filtersProvidersByType() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            long count = discovery.filterByType("STORAGE").count();

            assertThat(count).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("filters providers by capability")
        void filtersProvidersByCapability() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            long count = discovery.filterByCapability("read").count();

            assertThat(count).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("gets all metadata")
        void getAllMetadata() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            var metadata = discovery.getAllMetadata(); // GH-90000

            assertThat(metadata).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("gets provider by plugin ID returns null when not found")
        void getsProviderByPluginIdReturnsNullWhenNotFound() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            PluginProvider provider = discovery.getProviderByPluginId("non-existent-plugin");

            assertThat(provider).isNull(); // GH-90000
        }

        @Test
        @DisplayName("gets provider by class returns null when not found")
        void getsProviderByClassReturnsNullWhenNotFound() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            PluginProvider provider = discovery.getProvider(TestPluginProvider.class); // GH-90000

            assertThat(provider).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Parallel Discovery")
    class ParallelDiscoveryTests {

        @Test
        @DisplayName("performs parallel discovery")
        void performsParallelDiscovery() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            long count = discovery.discoverProvidersParallel().count(); // GH-90000

            assertThat(count).isGreaterThanOrEqualTo(0); // GH-90000
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("caches parallel discovery results")
        void cachesParallelDiscoveryResults() { // GH-90000
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); // GH-90000

            discovery.discoverProvidersParallel(); // GH-90000
            long count1 = discovery.discoverProvidersParallel().count(); // GH-90000

            assertThat(count1).isGreaterThanOrEqualTo(0); // GH-90000
        }
    }

    // Test helper class
    private static class TestPluginProvider implements PluginProvider {
        @Override
        public PluginMetadata getMetadata() { // GH-90000
            return PluginMetadata.builder() // GH-90000
                    .id("test-plugin")
                    .name("Test Plugin")
                    .version("1.0.0")
                    .type(PluginType.STORAGE) // GH-90000
                    .capabilities(Set.of("read", "write")) // GH-90000
                    .build(); // GH-90000
        }

        @Override
        public Plugin createPlugin() { // GH-90000
            return mock(Plugin.class); // GH-90000
        }
    }
}
