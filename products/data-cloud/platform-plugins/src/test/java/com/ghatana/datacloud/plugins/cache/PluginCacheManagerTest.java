package com.ghatana.datacloud.plugins.cache;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("PluginCacheManager Tests")
class PluginCacheManagerTest {

    @BeforeEach
    void setUp() { 
        PluginCacheManager cache = PluginCacheManager.getInstance(); 
        cache.clearMetadataCache(); 
        cache.clearInstanceCache(); 
        cache.clearOperationCache(); 
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { 
            PluginCacheManager instance1 = PluginCacheManager.getInstance(); 
            PluginCacheManager instance2 = PluginCacheManager.getInstance(); 

            assertThat(instance1).isSameAs(instance2); 
        }

        @Test
        @DisplayName("returns instance with custom TTL")
        void returnsInstanceWithCustomTtl() { 
            Duration customTtl = Duration.ofMinutes(10); 
            PluginCacheManager instance = PluginCacheManager.getInstanceWithTtl(customTtl); 

            assertThat(instance).isNotNull(); 
        }

        @Test
        @DisplayName("returns same instance even with custom TTL")
        void returnsSameInstanceWithCustomTtl() { 
            PluginCacheManager instance1 = PluginCacheManager.getInstanceWithTtl(Duration.ofMinutes(5)); 
            PluginCacheManager instance2 = PluginCacheManager.getInstanceWithTtl(Duration.ofMinutes(10)); 

            assertThat(instance1).isSameAs(instance2); 
        }
    }

    @Nested
    @DisplayName("Metadata Caching")
    class MetadataCachingTests {

        @Test
        @DisplayName("caches and retrieves plugin metadata")
        void cachesAndRetrievesMetadata() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() 
                    .id(pluginId) 
                    .name("Test Plugin")
                    .version("1.0.0")
                    .type(PluginType.STORAGE) 
                    .build(); 

            cache.cacheMetadata(pluginId, metadata); 
            PluginMetadata retrieved = cache.getCachedMetadata(pluginId); 

            assertThat(retrieved).isNotNull(); 
            assertThat(retrieved.id()).isEqualTo(pluginId); 
            assertThat(retrieved.name()).isEqualTo("Test Plugin");
        }

        @Test
        @DisplayName("returns null for non-existent metadata")
        void returnsNullForNonExistentMetadata() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 

            PluginMetadata retrieved = cache.getCachedMetadata("non-existent");

            assertThat(retrieved).isNull(); 
        }

        @Test
        @DisplayName("checks if metadata is cached")
        void checksIfMetadataIsCached() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() 
                    .id(pluginId) 
                    .name("Test Plugin")
                    .version("1.0.0")
                    .build(); 

            assertThat(cache.hasCachedMetadata(pluginId)).isFalse(); 

            cache.cacheMetadata(pluginId, metadata); 

            assertThat(cache.hasCachedMetadata(pluginId)).isTrue(); 
        }

        @Test
        @DisplayName("invalidates metadata cache")
        void invalidatesMetadataCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() 
                    .id(pluginId) 
                    .name("Test Plugin")
                    .version("1.0.0")
                    .build(); 

            cache.cacheMetadata(pluginId, metadata); 
            cache.invalidateMetadata(pluginId); 

            assertThat(cache.getCachedMetadata(pluginId)).isNull(); 
        }

        @Test
        @DisplayName("clears all metadata cache")
        void clearsAllMetadataCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            
            cache.cacheMetadata("plugin1", PluginMetadata.builder().id("plugin1").build());
            cache.cacheMetadata("plugin2", PluginMetadata.builder().id("plugin2").build());
            
            cache.clearMetadataCache(); 
            
            assertThat(cache.getCachedMetadata("plugin1")).isNull();
            assertThat(cache.getCachedMetadata("plugin2")).isNull();
        }
    }

    @Nested
    @DisplayName("Instance Caching")
    class InstanceCachingTests {

        @Test
        @DisplayName("caches and retrieves plugin instance")
        void cachesAndRetrievesInstance() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 
            PluginState state = PluginState.RUNNING;

            cache.cacheInstance(pluginId, instance, context, state); 
            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); 

            assertThat(cached).isNotNull(); 
            assertThat(cached.instance()).isSameAs(instance); 
            assertThat(cached.state()).isEqualTo(state); 
        }

        @Test
        @DisplayName("returns null for non-existent instance")
        void returnsNullForNonExistentInstance() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 

            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance("non-existent");

            assertThat(cached).isNull(); 
        }

        @Test
        @DisplayName("returns null for stopped instances")
        void returnsNullForStoppedInstances() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            cache.cacheInstance(pluginId, instance, context, PluginState.STOPPED); 
            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); 

            assertThat(cached).isNull(); 
        }

        @Test
        @DisplayName("checks if instance is cached and valid")
        void checksIfInstanceIsCached() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            assertThat(cache.hasCachedInstance(pluginId)).isFalse(); 

            cache.cacheInstance(pluginId, instance, context, PluginState.RUNNING); 

            assertThat(cache.hasCachedInstance(pluginId)).isTrue(); 
        }

        @Test
        @DisplayName("updates plugin state in cache")
        void updatesInstanceState() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            cache.cacheInstance(pluginId, instance, context, PluginState.INITIALIZED); 
            cache.updateInstanceState(pluginId, PluginState.RUNNING); 

            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); 
            assertThat(cached.state()).isEqualTo(PluginState.RUNNING); 
        }

        @Test
        @DisplayName("invalidates instance cache")
        void invalidatesInstanceCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            cache.cacheInstance(pluginId, instance, context, PluginState.RUNNING); 
            cache.invalidateInstance(pluginId); 

            assertThat(cache.getCachedInstance(pluginId)).isNull(); 
        }

        @Test
        @DisplayName("clears all instance cache")
        void clearsAllInstanceCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            Object instance1 = new Object(); 
            Object instance2 = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            cache.cacheInstance("plugin1", instance1, context, PluginState.RUNNING); 
            cache.cacheInstance("plugin2", instance2, context, PluginState.RUNNING); 
            
            cache.clearInstanceCache(); 
            
            assertThat(cache.getCachedInstance("plugin1")).isNull();
            assertThat(cache.getCachedInstance("plugin2")).isNull();
        }
    }

    @Nested
    @DisplayName("Operation Caching")
    class OperationCachingTests {

        @Test
        @DisplayName("caches and retrieves operation result")
        void cachesAndRetrievesOperationResult() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            String operation = "getData";
            String cacheKey = "key1";
            Object result = "test-result";

            cache.cacheOperationResult(pluginId, operation, cacheKey, result); 
            Object retrieved = cache.getCachedOperationResult(pluginId, operation, cacheKey); 

            assertThat(retrieved).isNotNull(); 
            assertThat(retrieved).isEqualTo(result); 
        }

        @Test
        @DisplayName("returns null for non-existent operation result")
        void returnsNullForNonExistentOperationResult() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 

            Object retrieved = cache.getCachedOperationResult("plugin", "operation", "key"); 

            assertThat(retrieved).isNull(); 
        }

        @Test
        @DisplayName("checks if operation result is cached and valid")
        void checksIfOperationResultIsCached() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";
            String operation = "getData";
            String cacheKey = "key1";
            Object result = "test-result";

            assertThat(cache.hasCachedOperationResult(pluginId, operation, cacheKey)).isFalse(); 

            cache.cacheOperationResult(pluginId, operation, cacheKey, result); 

            assertThat(cache.hasCachedOperationResult(pluginId, operation, cacheKey)).isTrue(); 
        }

        @Test
        @DisplayName("invalidates all operations for a plugin")
        void invalidatesAllOperationsForPlugin() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";

            cache.cacheOperationResult(pluginId, "op1", "key1", "result1"); 
            cache.cacheOperationResult(pluginId, "op2", "key2", "result2"); 

            cache.invalidatePluginOperations(pluginId); 

            assertThat(cache.getCachedOperationResult(pluginId, "op1", "key1")).isNull(); 
            assertThat(cache.getCachedOperationResult(pluginId, "op2", "key2")).isNull(); 
        }

        @Test
        @DisplayName("invalidates specific operation for a plugin")
        void invalidatesSpecificOperationForPlugin() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            String pluginId = "test-plugin";

            cache.cacheOperationResult(pluginId, "op1", "key1", "result1"); 
            cache.cacheOperationResult(pluginId, "op2", "key2", "result2"); 

            cache.invalidatePluginOperation(pluginId, "op1"); 

            assertThat(cache.getCachedOperationResult(pluginId, "op1", "key1")).isNull(); 
            assertThat(cache.getCachedOperationResult(pluginId, "op2", "key2")).isNotNull(); 
        }

        @Test
        @DisplayName("clears all operation cache")
        void clearsAllOperationCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 

            cache.cacheOperationResult("plugin1", "op1", "key1", "result1"); 
            cache.cacheOperationResult("plugin2", "op2", "key2", "result2"); 
            
            cache.clearOperationCache(); 
            
            assertThat(cache.getCachedOperationResult("plugin1", "op1", "key1")).isNull(); 
            assertThat(cache.getCachedOperationResult("plugin2", "op2", "key2")).isNull(); 
        }
    }

    @Nested
    @DisplayName("Cache Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("returns cache statistics")
        void returnsCacheStatistics() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            PluginMetadata metadata = PluginMetadata.builder().id("plugin1").build();
            Object instance = new Object(); 
            PluginContext context = mock(PluginContext.class); 

            cache.cacheMetadata("plugin1", metadata); 
            cache.cacheInstance("plugin1", instance, context, PluginState.RUNNING); 
            cache.cacheOperationResult("plugin1", "op1", "key1", "result1"); 

            PluginCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.metadataCacheSize()).isGreaterThan(0); 
            assertThat(stats.instanceCacheSize()).isGreaterThan(0); 
            assertThat(stats.operationCacheSize()).isGreaterThan(0); 
        }

        @Test
        @DisplayName("returns zero statistics for empty cache")
        void returnsZeroStatisticsForEmptyCache() { 
            PluginCacheManager cache = PluginCacheManager.getInstance(); 
            
            // Clear all caches
            cache.clearMetadataCache(); 
            cache.clearInstanceCache(); 
            cache.clearOperationCache(); 

            PluginCacheManager.CacheStatistics stats = cache.getStatistics(); 

            assertThat(stats.metadataCacheSize()).isEqualTo(0); 
            assertThat(stats.instanceCacheSize()).isEqualTo(0); 
            assertThat(stats.operationCacheSize()).isEqualTo(0); 
        }
    }
}
