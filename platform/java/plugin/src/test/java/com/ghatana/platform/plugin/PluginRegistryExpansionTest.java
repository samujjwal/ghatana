/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Phase 3 Expansion tests for {@link PluginRegistry}.
 * Tests plugin discovery, capability-based loading, composition patterns at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for platform plugin system
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginRegistry - Phase 3 Expansion")
class PluginRegistryExpansionTest {

    // ============================================
    // PLUGIN REGISTRATION & LOOKUP (5 tests)
    // ============================================

    @Nested
    @DisplayName("Plugin Registration & Lookup")
    class RegistrationTests {

        @Test
        @DisplayName("Many plugins registered and retrieved independently")
        void manyPluginsRegistered() {
            PluginRegistry registry = new PluginRegistry();

            for (int i = 0; i < 50; i++) {
                final int idx = i;
                MockPlugin plugin = new MockPlugin("plugin-" + idx);
                registry.register("plugin-" + idx, plugin);
            }

            for (int i = 0; i < 50; i++) {
                Optional<Plugin> found = registry.find("plugin-" + i);
                assertThat(found)
                    .as("Plugin %d should be found", i)
                    .isPresent();
            }
        }

        @Test
        @DisplayName("Plugins overwrite when same ID registered twice")
        void pluginOverwrite() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin first = new MockPlugin("first");
            MockPlugin second = new MockPlugin("second");

            registry.register("same-id", first);
            assertThat(registry.find("same-id")).hasValue(first);

            registry.register("same-id", second);
            assertThat(registry.find("same-id")).hasValue(second);
        }

        @Test
        @DisplayName("Unregistered plugins return empty Optional")
        void unregisteredPlugins() {
            PluginRegistry registry = new PluginRegistry();

            Optional<Plugin> notFound = registry.find("nonexistent-plugin");

            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("Listing all registered plugins")
        void listAllPlugins() {
            PluginRegistry registry = new PluginRegistry();

            for (int i = 0; i < 10; i++) {
                registry.register("plugin-" + i, new MockPlugin("p-" + i));
            }

            List<Plugin> all = registry.listAll();

            assertThat(all).hasSize(10);
        }

        @Test
        @DisplayName("Clear registry removes all plugins")
        void clearRegistry() {
            PluginRegistry registry = new PluginRegistry();

            for (int i = 0; i < 20; i++) {
                registry.register("plugin-" + i, new MockPlugin("p" + i));
            }

            assertThat(registry.listAll()).hasSize(20);

            registry.clear();

            assertThat(registry.listAll()).isEmpty();
            assertThat(registry.find("plugin-0")).isEmpty();
        }
    }

    // ============================================
    // CAPABILITY-BASED DISCOVERY (4 tests)
    // ============================================

    @Nested
    @DisplayName("Capability-Based Discovery")
    class CapabilityDiscoveryTests {

        @Test
        @DisplayName("Plugins with same capability discovered together")
        void capabilityDiscovery() {
            PluginRegistry registry = new PluginRegistry();

            // Register plugins with same capability
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                MockPlugin plugin = new MockPlugin("storage-" + idx);
                plugin.addCapability(StorageCapability.class);
                registry.register("storage-" + idx, plugin);
            }

            List<Plugin> storagePlugins = registry.findWithCapability(StorageCapability.class);

            assertThat(storagePlugins).hasSize(5);
        }

        @Test
        @DisplayName("Multiple capability-based filters work independently")
        void multipleCapabilities() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin logger = new MockPlugin("logger");
            logger.addCapability(LoggingCapability.class);
            registry.register("logger", logger);

            MockPlugin cache = new MockPlugin("cache");
            cache.addCapability(CacheCapability.class);
            registry.register("cache", cache);

            MockPlugin storage = new MockPlugin("storage");
            storage.addCapability(StorageCapability.class);
            registry.register("storage", storage);

            assertThat(registry.findWithCapability(LoggingCapability.class)).hasSize(1);
            assertThat(registry.findWithCapability(CacheCapability.class)).hasSize(1);
            assertThat(registry.findWithCapability(StorageCapability.class)).hasSize(1);
        }

        @Test
        @DisplayName("Single plugin with multiple capabilities found by each")
        void multiCapabilityPlugin() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin universal = new MockPlugin("universal");
            universal.addCapability(StorageCapability.class);
            universal.addCapability(CacheCapability.class);
            universal.addCapability(LoggingCapability.class);

            registry.register("universal", universal);

            assertThat(registry.findWithCapability(StorageCapability.class)).hasSize(1);
            assertThat(registry.findWithCapability(CacheCapability.class)).hasSize(1);
            assertThat(registry.findWithCapability(LoggingCapability.class)).hasSize(1);
        }

        @Test
        @DisplayName("No plugins registered for capability returns empty list")
        void noCapabilityMatch() {
            PluginRegistry registry = new PluginRegistry();
            registry.register("generic", new MockPlugin("g"));

            List<Plugin> customCapability = registry.findWithCapability(CustomCapability.class);

            assertThat(customCapability).isEmpty();
        }
    }

    // ============================================
    // PLUGIN LIFECYCLE (4 tests)
    // ============================================

    @Nested
    @DisplayName("Plugin Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("Plugin initialization called on registration")
        void initializationOnRegister() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin plugin = new MockPlugin("test");
            assertThat(plugin.initialized).isFalse();

            registry.register("test", plugin);
            registry.initialize();

            assertThat(plugin.initialized).isTrue();
        }

        @Test
        @DisplayName("Multiple plugins initialized sequentially")
        void multipleInitialization() {
            PluginRegistry registry = new PluginRegistry();

            List<MockPlugin> plugins = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                MockPlugin plugin = new MockPlugin("plugin-" + i);
                plugins.add(plugin);
                registry.register("plugin-" + i, plugin);
            }

            registry.initialize();

            assertThat(plugins)
                .allMatch(p -> p.initialized, "All plugins should be initialized");
        }

        @Test
        @DisplayName("Plugin shutdown called on registry shutdown")
        void shutdownCall() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin plugin = new MockPlugin("test");
            registry.register("test", plugin);
            registry.initialize();

            assertThat(plugin.shutdown).isFalse();

            registry.shutdown();

            assertThat(plugin.shutdown).isTrue();
        }

        @Test
        @DisplayName("Plugin reload reinitializes plugin")
        void pluginReload() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin plugin = new MockPlugin("reloadable");
            registry.register("reloadable", plugin);
            registry.initialize();

            int initCountBefore = plugin.initCount;

            registry.reload("reloadable");

            assertThat(plugin.initCount).isGreaterThan(initCountBefore);
        }
    }

    // ============================================
    // PLUGIN COMPOSITION (3 tests)
    // ============================================

    @Nested
    @DisplayName("Plugin Composition")
    class CompositionTests {

        @Test
        @DisplayName("Plugin depends on another plugin through registry")
        void pluginDependencies() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin logging = new MockPlugin("logging");
            logging.addCapability(LoggingCapability.class);
            registry.register("logging", logging);

            MockPlugin app = new MockPlugin("app");
            app.setDependency("logging", logging);
            registry.register("app", app);

            registry.initialize();

            assertThat(app.dependencies).containsKey("logging");
        }

        @Test
        @DisplayName("Many plugins form complex dependency graph")
        void complexDependencies() {
            PluginRegistry registry = new PluginRegistry();

            // Create plugins in dependency order
            MockPlugin base = new MockPlugin("base");
            registry.register("base", base);

            MockPlugin middle = new MockPlugin("middle");
            middle.setDependency("base", base);
            registry.register("middle", middle);

            MockPlugin top = new MockPlugin("top");
            top.setDependency("middle", middle);
            top.setDependency("base", base);
            registry.register("top", top);

            registry.initialize();

            assertThat(top.dependencies).hasSize(2);
        }

        @Test
        @DisplayName("Plugins can interact through capabilities")
        void capabilityInteraction() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin storage = new MockPlugin("storage");
            storage.addCapability(StorageCapability.class);
            registry.register("storage", storage);

            MockPlugin consumer = new MockPlugin("consumer");
            registry.register("consumer",consumer);

            List<Plugin> storageList = registry.findWithCapability(StorageCapability.class);
            assertThat(storageList).contains(storage);
        }
    }

    // ============================================
    // CONCURRENCY & THREAD SAFETY (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrency & Thread Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many threads registering plugins concurrently")
        void concurrentRegistration() throws Exception {
            PluginRegistry registry = new PluginRegistry();

            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            registry.register("plugin-" + idx, new MockPlugin("p-" + idx));
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(registry.listAll()).hasSize(threadCount);
        }

        @Test
        @DisplayName("Concurrent lookups while registry is stable")
        void concurrentLookups() throws Exception {
            PluginRegistry registry = new PluginRegistry();

            for (int i = 0; i < 100; i++) {
                registry.register("plugin-" + i, new MockPlugin("p-" + i));
            }

            int threadCount = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int t = 0; t < threadCount; t++) {
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < 100; i++) {
                                if (registry.find("plugin-" + i).isPresent()) {
                                    successCount.incrementAndGet();
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount * 100);
        }

        @Test
        @DisplayName("Capability discovery under concurrent registration")
        void concurrentCapabilitySearch() throws Exception {
            PluginRegistry registry = new PluginRegistry();

            int threadCount = 10;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount * 2);
            AtomicInteger discoveryCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount * 2);
            try {
                // Writer threads
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            for (int j = 0; j < 10; j++) {
                                MockPlugin p = new MockPlugin("storage-" + idx + "-" + j);
                                p.addCapability(StorageCapability.class);
                                registry.register("storage-" + idx + "-" + j, p);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Reader threads
                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            for (int j = 0; j < 10; j++) {
                                List<Plugin> found = registry.findWithCapability(StorageCapability.class);
                                if (!found.isEmpty()) {
                                    discoveryCount.incrementAndGet();
                                }
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(discoveryCount.get()).isGreaterThan(0);
        }
    }

    // ============================================
    // EDGE CASES & ERRORS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Plugin with empty ID works")
        void emptyPluginId() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin plugin = new MockPlugin("with-empty-id");
            registry.register("", plugin);

            Optional<Plugin> found = registry.find("");
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("Plugin with null dependency handled gracefully")
        void nullDependency() {
            PluginRegistry registry = new PluginRegistry();

            MockPlugin plugin = new MockPlugin("nullable-dep");
            plugin.setDependency("optional", null);

            assertThatNoException().isThrownBy(() -> registry.register("nullable-dep", plugin));
        }

        @Test
        @DisplayName("Massive registry with 1000+ plugins")
        void massiveRegistry() {
            PluginRegistry registry = new PluginRegistry();

            for (int i = 0; i < 1000; i++) {
                registry.register("plugin-" + i, new MockPlugin("p" + i));
            }

            assertThat(registry.listAll()).hasSize(1000);
            assertThat(registry.find("plugin-500")).isPresent();
            assertThat(registry.find("plugin-999")).isPresent();
        }
    }

    // ============================================
    // HELPER CLASSES
    // ============================================

    static class MockPlugin implements Plugin {
        final String name;
        boolean initialized = false;
        boolean shutdown = false;
        int initCount = 0;
        List<Class<? extends PluginCapability>> capabilities = new ArrayList<>();
        Map<String, Plugin> dependencies = new HashMap<>();

        MockPlugin(String name) {
            this.name = name;
        }

        void addCapability(Class<? extends PluginCapability> capability) {
            capabilities.add(capability);
        }

        void setDependency(String id, Plugin plugin) {
            dependencies.put(id, plugin);
        }

        @Override
        public String getId() {
            return name;
        }

        @Override
        public void initialize() {
            initialized = true;
            initCount++;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Class<? extends PluginCapability>> getCapabilities() {
            return capabilities;
        }
    }

    // Capability markers
    interface StorageCapability extends PluginCapability {}
    interface CacheCapability extends PluginCapability {}
    interface LoggingCapability extends PluginCapability {}
    interface CustomCapability extends PluginCapability {}
}
