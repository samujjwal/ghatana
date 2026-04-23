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
    void setUp() { // GH-90000
        PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
        cache.clearMetadataCache(); // GH-90000
        cache.clearInstanceCache(); // GH-90000
        cache.clearOperationCache(); // GH-90000
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {

        @Test
        @DisplayName("returns same instance on multiple calls")
        void returnsSameInstanceOnMultipleCalls() { // GH-90000
            PluginCacheManager instance1 = PluginCacheManager.getInstance(); // GH-90000
            PluginCacheManager instance2 = PluginCacheManager.getInstance(); // GH-90000

            assertThat(instance1).isSameAs(instance2); // GH-90000
        }

        @Test
        @DisplayName("returns instance with custom TTL")
        void returnsInstanceWithCustomTtl() { // GH-90000
            Duration customTtl = Duration.ofMinutes(10); // GH-90000
            PluginCacheManager instance = PluginCacheManager.getInstanceWithTtl(customTtl); // GH-90000

            assertThat(instance).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("returns same instance even with custom TTL")
        void returnsSameInstanceWithCustomTtl() { // GH-90000
            PluginCacheManager instance1 = PluginCacheManager.getInstanceWithTtl(Duration.ofMinutes(5)); // GH-90000
            PluginCacheManager instance2 = PluginCacheManager.getInstanceWithTtl(Duration.ofMinutes(10)); // GH-90000

            assertThat(instance1).isSameAs(instance2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Metadata Caching")
    class MetadataCachingTests {

        @Test
        @DisplayName("caches and retrieves plugin metadata")
        void cachesAndRetrievesMetadata() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id(pluginId) // GH-90000
                    .name("Test Plugin")
                    .version("1.0.0")
                    .type(PluginType.STORAGE) // GH-90000
                    .build(); // GH-90000

            cache.cacheMetadata(pluginId, metadata); // GH-90000
            PluginMetadata retrieved = cache.getCachedMetadata(pluginId); // GH-90000

            assertThat(retrieved).isNotNull(); // GH-90000
            assertThat(retrieved.id()).isEqualTo(pluginId); // GH-90000
            assertThat(retrieved.name()).isEqualTo("Test Plugin");
        }

        @Test
        @DisplayName("returns null for non-existent metadata")
        void returnsNullForNonExistentMetadata() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000

            PluginMetadata retrieved = cache.getCachedMetadata("non-existent");

            assertThat(retrieved).isNull(); // GH-90000
        }

        @Test
        @DisplayName("checks if metadata is cached")
        void checksIfMetadataIsCached() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id(pluginId) // GH-90000
                    .name("Test Plugin")
                    .version("1.0.0")
                    .build(); // GH-90000

            assertThat(cache.hasCachedMetadata(pluginId)).isFalse(); // GH-90000

            cache.cacheMetadata(pluginId, metadata); // GH-90000

            assertThat(cache.hasCachedMetadata(pluginId)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("invalidates metadata cache")
        void invalidatesMetadataCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            PluginMetadata metadata = PluginMetadata.builder() // GH-90000
                    .id(pluginId) // GH-90000
                    .name("Test Plugin")
                    .version("1.0.0")
                    .build(); // GH-90000

            cache.cacheMetadata(pluginId, metadata); // GH-90000
            cache.invalidateMetadata(pluginId); // GH-90000

            assertThat(cache.getCachedMetadata(pluginId)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("clears all metadata cache")
        void clearsAllMetadataCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            
            cache.cacheMetadata("plugin1", PluginMetadata.builder().id("plugin1").build());
            cache.cacheMetadata("plugin2", PluginMetadata.builder().id("plugin2").build());
            
            cache.clearMetadataCache(); // GH-90000
            
            assertThat(cache.getCachedMetadata("plugin1")).isNull();
            assertThat(cache.getCachedMetadata("plugin2")).isNull();
        }
    }

    @Nested
    @DisplayName("Instance Caching")
    class InstanceCachingTests {

        @Test
        @DisplayName("caches and retrieves plugin instance")
        void cachesAndRetrievesInstance() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000
            PluginState state = PluginState.RUNNING;

            cache.cacheInstance(pluginId, instance, context, state); // GH-90000
            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); // GH-90000

            assertThat(cached).isNotNull(); // GH-90000
            assertThat(cached.instance()).isSameAs(instance); // GH-90000
            assertThat(cached.state()).isEqualTo(state); // GH-90000
        }

        @Test
        @DisplayName("returns null for non-existent instance")
        void returnsNullForNonExistentInstance() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000

            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance("non-existent");

            assertThat(cached).isNull(); // GH-90000
        }

        @Test
        @DisplayName("returns null for stopped instances")
        void returnsNullForStoppedInstances() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            cache.cacheInstance(pluginId, instance, context, PluginState.STOPPED); // GH-90000
            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); // GH-90000

            assertThat(cached).isNull(); // GH-90000
        }

        @Test
        @DisplayName("checks if instance is cached and valid")
        void checksIfInstanceIsCached() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            assertThat(cache.hasCachedInstance(pluginId)).isFalse(); // GH-90000

            cache.cacheInstance(pluginId, instance, context, PluginState.RUNNING); // GH-90000

            assertThat(cache.hasCachedInstance(pluginId)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("updates plugin state in cache")
        void updatesInstanceState() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            cache.cacheInstance(pluginId, instance, context, PluginState.INITIALIZED); // GH-90000
            cache.updateInstanceState(pluginId, PluginState.RUNNING); // GH-90000

            PluginCacheManager.CachedPluginInstance cached = cache.getCachedInstance(pluginId); // GH-90000
            assertThat(cached.state()).isEqualTo(PluginState.RUNNING); // GH-90000
        }

        @Test
        @DisplayName("invalidates instance cache")
        void invalidatesInstanceCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            cache.cacheInstance(pluginId, instance, context, PluginState.RUNNING); // GH-90000
            cache.invalidateInstance(pluginId); // GH-90000

            assertThat(cache.getCachedInstance(pluginId)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("clears all instance cache")
        void clearsAllInstanceCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            Object instance1 = new Object(); // GH-90000
            Object instance2 = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            cache.cacheInstance("plugin1", instance1, context, PluginState.RUNNING); // GH-90000
            cache.cacheInstance("plugin2", instance2, context, PluginState.RUNNING); // GH-90000
            
            cache.clearInstanceCache(); // GH-90000
            
            assertThat(cache.getCachedInstance("plugin1")).isNull();
            assertThat(cache.getCachedInstance("plugin2")).isNull();
        }
    }

    @Nested
    @DisplayName("Operation Caching")
    class OperationCachingTests {

        @Test
        @DisplayName("caches and retrieves operation result")
        void cachesAndRetrievesOperationResult() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            String operation = "getData";
            String cacheKey = "key1";
            Object result = "test-result";

            cache.cacheOperationResult(pluginId, operation, cacheKey, result); // GH-90000
            Object retrieved = cache.getCachedOperationResult(pluginId, operation, cacheKey); // GH-90000

            assertThat(retrieved).isNotNull(); // GH-90000
            assertThat(retrieved).isEqualTo(result); // GH-90000
        }

        @Test
        @DisplayName("returns null for non-existent operation result")
        void returnsNullForNonExistentOperationResult() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000

            Object retrieved = cache.getCachedOperationResult("plugin", "operation", "key"); // GH-90000

            assertThat(retrieved).isNull(); // GH-90000
        }

        @Test
        @DisplayName("checks if operation result is cached and valid")
        void checksIfOperationResultIsCached() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";
            String operation = "getData";
            String cacheKey = "key1";
            Object result = "test-result";

            assertThat(cache.hasCachedOperationResult(pluginId, operation, cacheKey)).isFalse(); // GH-90000

            cache.cacheOperationResult(pluginId, operation, cacheKey, result); // GH-90000

            assertThat(cache.hasCachedOperationResult(pluginId, operation, cacheKey)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("invalidates all operations for a plugin")
        void invalidatesAllOperationsForPlugin() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";

            cache.cacheOperationResult(pluginId, "op1", "key1", "result1"); // GH-90000
            cache.cacheOperationResult(pluginId, "op2", "key2", "result2"); // GH-90000

            cache.invalidatePluginOperations(pluginId); // GH-90000

            assertThat(cache.getCachedOperationResult(pluginId, "op1", "key1")).isNull(); // GH-90000
            assertThat(cache.getCachedOperationResult(pluginId, "op2", "key2")).isNull(); // GH-90000
        }

        @Test
        @DisplayName("invalidates specific operation for a plugin")
        void invalidatesSpecificOperationForPlugin() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            String pluginId = "test-plugin";

            cache.cacheOperationResult(pluginId, "op1", "key1", "result1"); // GH-90000
            cache.cacheOperationResult(pluginId, "op2", "key2", "result2"); // GH-90000

            cache.invalidatePluginOperation(pluginId, "op1"); // GH-90000

            assertThat(cache.getCachedOperationResult(pluginId, "op1", "key1")).isNull(); // GH-90000
            assertThat(cache.getCachedOperationResult(pluginId, "op2", "key2")).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("clears all operation cache")
        void clearsAllOperationCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000

            cache.cacheOperationResult("plugin1", "op1", "key1", "result1"); // GH-90000
            cache.cacheOperationResult("plugin2", "op2", "key2", "result2"); // GH-90000
            
            cache.clearOperationCache(); // GH-90000
            
            assertThat(cache.getCachedOperationResult("plugin1", "op1", "key1")).isNull(); // GH-90000
            assertThat(cache.getCachedOperationResult("plugin2", "op2", "key2")).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cache Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("returns cache statistics")
        void returnsCacheStatistics() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            PluginMetadata metadata = PluginMetadata.builder().id("plugin1").build();
            Object instance = new Object(); // GH-90000
            PluginContext context = mock(PluginContext.class); // GH-90000

            cache.cacheMetadata("plugin1", metadata); // GH-90000
            cache.cacheInstance("plugin1", instance, context, PluginState.RUNNING); // GH-90000
            cache.cacheOperationResult("plugin1", "op1", "key1", "result1"); // GH-90000

            PluginCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.metadataCacheSize()).isGreaterThan(0); // GH-90000
            assertThat(stats.instanceCacheSize()).isGreaterThan(0); // GH-90000
            assertThat(stats.operationCacheSize()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("returns zero statistics for empty cache")
        void returnsZeroStatisticsForEmptyCache() { // GH-90000
            PluginCacheManager cache = PluginCacheManager.getInstance(); // GH-90000
            
            // Clear all caches
            cache.clearMetadataCache(); // GH-90000
            cache.clearInstanceCache(); // GH-90000
            cache.clearOperationCache(); // GH-90000

            PluginCacheManager.CacheStatistics stats = cache.getStatistics(); // GH-90000

            assertThat(stats.metadataCacheSize()).isEqualTo(0); // GH-90000
            assertThat(stats.instanceCacheSize()).isEqualTo(0); // GH-90000
            assertThat(stats.operationCacheSize()).isEqualTo(0); // GH-90000
        }
    }
}
