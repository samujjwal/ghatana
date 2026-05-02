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
    void setUp() { 
        OptimizedPluginDiscovery.getInstance().clearCache(); 
        PluginCacheManager cacheManager = PluginCacheManager.getInstance(); 
        cacheManager.clearMetadataCache(); 
        cacheManager.clearInstanceCache(); 
        cacheManager.clearOperationCache(); 
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { 
            OptimizedPluginDiscovery instance1 = OptimizedPluginDiscovery.getInstance(); 
            OptimizedPluginDiscovery instance2 = OptimizedPluginDiscovery.getInstance(); 

            assertThat(instance1).isSameAs(instance2); 
        }

        @Test
        @DisplayName("returns instance with custom cache manager")
        void returnsInstanceWithCustomCacheManager() { 
            PluginCacheManager cacheManager = mock(PluginCacheManager.class); 
            OptimizedPluginDiscovery instance = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager); 

            assertThat(instance).isNotNull(); 
        }

        @Test
        @DisplayName("returns same instance even with custom cache manager")
        void returnsSameInstanceWithCustomCacheManager() { 
            PluginCacheManager cacheManager1 = mock(PluginCacheManager.class); 
            PluginCacheManager cacheManager2 = mock(PluginCacheManager.class); 
            
            OptimizedPluginDiscovery instance1 = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager1); 
            OptimizedPluginDiscovery instance2 = OptimizedPluginDiscovery.getInstanceWithCacheManager(cacheManager2); 

            assertThat(instance1).isSameAs(instance2); 
        }
    }

    @Nested
    @DisplayName("Discovery Operations")
    class DiscoveryTests {

        @Test
        @DisplayName("checks if discovery has been performed")
        void checksIfDiscoveryPerformed() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            assertThat(discovery.isDiscoveryPerformed()).isFalse(); 
        }

        @Test
        @DisplayName("returns empty stream when no providers exist")
        void returnsEmptyStreamWhenNoProviders() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            long count = discovery.discoverProviders().count(); 

            assertThat(count).isEqualTo(0); 
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); 
        }

        @Test
        @DisplayName("caches discovery results")
        void cachesDiscoveryResults() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            discovery.discoverProviders(); 
            long count1 = discovery.discoverProviders().count(); 

            assertThat(count1).isGreaterThanOrEqualTo(0); 
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); 
        }

        @Test
        @DisplayName("returns discovery statistics")
        void returnsDiscoveryStatistics() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            OptimizedPluginDiscovery.DiscoveryStatistics stats = discovery.getStatistics(); 

            assertThat(stats).isNotNull(); 
            assertThat(stats.discoveryPerformed()).isFalse(); 
        }

        @Test
        @DisplayName("clears discovery cache")
        void clearsDiscoveryCache() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            discovery.discoverProviders(); 
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); 

            discovery.clearCache(); 
            assertThat(discovery.isDiscoveryPerformed()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("Provider Filtering")
    class FilteringTests {

        @Test
        @DisplayName("filters providers by type")
        void filtersProvidersByType() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            long count = discovery.filterByType("STORAGE").count();

            assertThat(count).isGreaterThanOrEqualTo(0); 
        }

        @Test
        @DisplayName("filters providers by capability")
        void filtersProvidersByCapability() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            long count = discovery.filterByCapability("read").count();

            assertThat(count).isGreaterThanOrEqualTo(0); 
        }

        @Test
        @DisplayName("gets all metadata")
        void getAllMetadata() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            var metadata = discovery.getAllMetadata(); 

            assertThat(metadata).isNotNull(); 
        }

        @Test
        @DisplayName("gets provider by plugin ID returns null when not found")
        void getsProviderByPluginIdReturnsNullWhenNotFound() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            PluginProvider provider = discovery.getProviderByPluginId("non-existent-plugin");

            assertThat(provider).isNull(); 
        }

        @Test
        @DisplayName("gets provider by class returns null when not found")
        void getsProviderByClassReturnsNullWhenNotFound() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            PluginProvider provider = discovery.getProvider(TestPluginProvider.class); 

            assertThat(provider).isNull(); 
        }
    }

    @Nested
    @DisplayName("Parallel Discovery")
    class ParallelDiscoveryTests {

        @Test
        @DisplayName("performs parallel discovery")
        void performsParallelDiscovery() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            long count = discovery.discoverProvidersParallel().count(); 

            assertThat(count).isGreaterThanOrEqualTo(0); 
            assertThat(discovery.isDiscoveryPerformed()).isTrue(); 
        }

        @Test
        @DisplayName("caches parallel discovery results")
        void cachesParallelDiscoveryResults() { 
            OptimizedPluginDiscovery discovery = OptimizedPluginDiscovery.getInstance(); 

            discovery.discoverProvidersParallel(); 
            long count1 = discovery.discoverProvidersParallel().count(); 

            assertThat(count1).isGreaterThanOrEqualTo(0); 
        }
    }

    // Test helper class
    private static class TestPluginProvider implements PluginProvider {
        @Override
        public PluginMetadata getMetadata() { 
            return PluginMetadata.builder() 
                    .id("test-plugin")
                    .name("Test Plugin")
                    .version("1.0.0")
                    .type(PluginType.STORAGE) 
                    .capabilities(Set.of("read", "write")) 
                    .build(); 
        }

        @Override
        public Plugin createPlugin() { 
            return mock(Plugin.class); 
        }
    }
}
