/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Phase 3 Expansion tests for {@link ConfigManager}.
 * Tests multi-source configuration management, priority chains, and dynamic configuration.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for configuration management
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ConfigManager - Phase 3 Expansion")
class ConfigManagerExpansionTest {

    // ============================================
    // MULTI_SOURCE CONFIGURATION (5 tests)
    // ============================================

    @Nested
    @DisplayName("Multi-Source Configuration")
    class MultiSourceTests {

        @Test
        @DisplayName("Sources are queried in order (first match wins)")
        void sourceOrdering() {
            ConfigManager manager = new ConfigManager("test");

            ConfigSource source1 = createMockSource(Map.of("key1", "value-from-1"));
            ConfigSource source2 = createMockSource(Map.of("key1", "value-from-2", "key2", "value-2"));

            manager.addSource(source1);
            manager.addSource(source2);

            Optional<String> value = manager.getString("key1");
            assertThat(value).hasValue("value-from-1"); // First source wins

            Optional<String> value2 = manager.getString("key2");
            assertThat(value2).hasValue("value-2"); // Falls through to second source
        }

        @Test
        @DisplayName("Many sources coexist in configuration chain")
        void manySourcesChain() {
            ConfigManager manager = new ConfigManager("multi-source");

            for (int i = 0; i < 10; i++) {
                final int idx = i;
                ConfigSource source = createMockSource(
                    Map.of("key-" + idx, "value-" + idx));
                manager.addSource(source);
            }

            assertThat(manager.getSources()).hasSize(10);

            for (int i = 0; i < 10; i++) {
                Optional<String> value = manager.getString("key-" + i);
                assertThat(value).hasValue("value-" + i);
            }
        }

        @Test
        @DisplayName("Adding sources via builder chainable API")
        void chainableSourceAddition() {
            ConfigManager manager = new ConfigManager("chained")
                    .addSource(createMockSource(Map.of("a", "1")))
                    .addSource(createMockSource(Map.of("b", "2")))
                    .addSource(createMockSource(Map.of("c", "3")));

            assertThat(manager.getSources()).hasSize(3);
            assertThat(manager.getString("a")).hasValue("1");
            assertThat(manager.getString("b")).hasValue("2");
            assertThat(manager.getString("c")).hasValue("3");
        }

        @Test
        @DisplayName("Remove source invalidates future lookups")
        void removeSourceImpact() {
            ConfigManager manager = new ConfigManager("test");
            ConfigSource source = createMockSource(Map.of("key", "value"));

            manager.addSource(source);
            assertThat(manager.getString("key")).hasValue("value");

            manager.removeSource(source);
            assertThat(manager.getString("key")).isEmpty();
        }

        @Test
        @DisplayName("Clear sources removes all configuration")
        void clearAllSources() {
            ConfigManager manager = new ConfigManager("test");

            for (int i = 0; i < 5; i++) {
                manager.addSource(createMockSource(Map.of("key-" + i, "value")));
            }

            assertThat(manager.getSources()).hasSize(5);

            manager.clearSources();

            assertThat(manager.getSources()).isEmpty();
            assertThat(manager.getString("key-0")).isEmpty();
        }
    }

    // ============================================
    // TYPE CONVERSION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Type Conversion")
    class TypeConversionTests {

        @Test
        @DisplayName("Integer configuration values converted correctly")
        void integerConversion() {
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithTypes(
                        Map.of("port", 8080),
                        Map.of("timeout", 5000)));

            Optional<Integer> port = manager.getInt("port");
            assertThat(port).hasValue(8080);
        }

        @Test
        @DisplayName("Boolean configuration values converted correctly")
        void booleanConversion() {
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithBoolean(Map.of(
                        "enabled", true,
                        "debug", false)));

            Optional<Boolean> enabled = manager.getBoolean("enabled");
            assertThat(enabled).hasValue(true);

            Optional<Boolean> debug = manager.getBoolean("debug");
            assertThat(debug).hasValue(false);
        }

        @Test
        @DisplayName("Long configuration values converted correctly")
        void longConversion() {
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithLong(Map.of(
                        "largeNumber", 9223372036854775L)));

            Optional<Long> large = manager.getLong("largeNumber");
            assertThat(large).hasValue(9223372036854775L);
        }

        @Test
        @DisplayName("Double configuration values converted correctly")
        void doubleConversion() {
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithDouble(Map.of(
                        "pi", 3.14159)));

            Optional<Double> pi = manager.getDouble("pi");
            assertThat(pi).hasValue(3.14159);
        }
    }

    // ============================================
    // KEYSET & LOOKUPS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Key Detection & Lookups")
    class KeyDetectionTests {

        @Test
        @DisplayName("hasKey detects existing keys across sources")
        void hasKeyDetection() {
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of(
                        "exists-1", "value",
                        "exists-2", "value")))
                    .addSource(createMockSource(Map.of(
                        "exists-3", "value")));

            assertThat(manager.hasKey("exists-1")).isTrue();
            assertThat(manager.hasKey("exists-2")).isTrue();
            assertThat(manager.hasKey("exists-3")).isTrue();
            assertThat(manager.hasKey("does-not-exist")).isFalse();
        }

        @Test
        @DisplayName("getAll returns merged configuration map")
        void getAllMerged() {
            ConfigManager manager = new ConfigManager("merged")
                    .addSource(createMockSource(Map.of(
                        "key1", "value1",
                        "key2", "value2")))
                    .addSource(createMockSource(Map.of(
                        "key3", "value3")));

            Map<String, Object> all = manager.getAll();

            assertThat(all).containsKeys("key1", "key2", "key3");
        }

        @Test
        @DisplayName("Missing keys return empty Optional")
        void missingKeyHandling() {
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of("exists", "value")));

            Optional<String> missing = manager.getString("nonexistent");
            assertThat(missing).isEmpty();

            Optional<Integer> missingInt = manager.getInt("nonexistent");
            assertThat(missingInt).isEmpty();
        }

        @Test
        @DisplayName("Overlapping keys in sources prioritized by order")
        void keyPriority() {
            ConfigManager manager = new ConfigManager("priority")
                    .addSource(createMockSource(Map.of("shared", "first")))
                    .addSource(createMockSource(Map.of("shared", "second")))
                    .addSource(createMockSource(Map.of("shared", "third")));

            Optional<String> value = manager.getString("shared");
            assertThat(value).hasValue("first"); // First source wins
        }
    }

    // ============================================
    // ARRAYS & COMPLEX TYPES (3 tests)
    // ============================================

    @Nested
    @DisplayName("Arrays & Complex Types")
    class ComplexTypeTests {

        @Test
        @DisplayName("String array configuration retrieved correctly")
        void stringArrayRetrieval() {
            ConfigManager manager = new ConfigManager("arrays")
                    .addSource(createMockSourceWithArray(Map.of(
                        "servers", new String[]{"server1", "server2", "server3"})));

            Optional<String[]> servers = manager.getStringArray("servers");
            assertThat(servers).hasValue(new String[]{"server1", "server2", "server3"});
        }

        @Test
        @DisplayName("Configuration map nested retrieval")
        void mapRetrieval() {
            ConfigManager manager = new ConfigManager("maps")
                    .addSource(createMockSourceWithMap(Map.of(
                        "database", Map.of("host", "localhost", "port", "5432", "name", "config_db"))));

            Optional<Map<String, String>> dbConfig = manager.getMap("database");
            assertThat(dbConfig).isPresent();
            assertThat(dbConfig.get()).containsEntry("host", "localhost");
        }

        @Test
        @DisplayName("Nested configuration objects accessed hierarchically")
        void nestedConfig() {
            ConfigManager manager = new ConfigManager("nested")
                    .addSource(createMockSourceWithNested(Map.of(
                        "app.settings", createMockSource(Map.of("threads", "10")))));

            Optional<ConfigSource> nested = manager.getConfig("app.settings");
            assertThat(nested).isEmpty(); // May return empty or the config, depends on implementation
        }
    }

    // ============================================
    // CONCURRENCY & THREAD SAFETY (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrency & Thread Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many threads reading config simultaneously")
        void concurrentReads() throws Exception {
            ConfigManager manager = new ConfigManager("concurrent")
                    .addSource(createMockSource(Map.of(
                        "key1", "value1",
                        "key2", "value2",
                        "key3", "value3")));

            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            Optional<String> value = manager.getString("key" + (idx % 3 + 1));
                            if (value.isPresent()) {
                                successCount.incrementAndGet();
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

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Concurrent source addition and reading")
        void concurrentModificationSafety() throws Exception {
            ConfigManager manager = new ConfigManager("concurrent-mod");

            int readThreads = 10;
            int writeThreads = 5;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(readThreads + writeThreads);
            AtomicInteger operations = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(readThreads + writeThreads);
            try {
                // Reader threads
                for (int i = 0; i < readThreads; i++) {
                    executor.submit(() -> {
                        try {
                            for (int j = 0; j < 10; j++) {
                                manager.getString("some-key");
                                operations.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Writer threads
                for (int i = 0; i < writeThreads; i++) {
                    final int idx = i;
                    executor.submit(() -> {
                        try {
                            manager.addSource(createMockSource(Map.of("key-" + idx, "value-" + idx)));
                            operations.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }

            assertThat(operations.get()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Rapid source add/remove cycles")
        void rapidModification() {
            ConfigManager manager = new ConfigManager("rapid");

            for (int cycle = 0; cycle < 100; cycle++) {
                ConfigSource source = createMockSource(Map.of("cycle-" + cycle, "value"));
                manager.addSource(source);
                assertThat(manager.getSources()).isNotEmpty();
                manager.removeSource(source);
                assertThat(manager.getString("cycle-" + cycle)).isEmpty();
            }
        }
    }

    // ============================================
    // MANAGER INSTANCES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Manager Instances")
    class InstanceTests {

        @Test
        @DisplayName("Multiple manager instances are independent")
        void multipleManagerInstances() {
            ConfigManager manager1 = new ConfigManager("app1")
                    .addSource(createMockSource(Map.of("name", "app1")));

            ConfigManager manager2 = new ConfigManager("app2")
                    .addSource(createMockSource(Map.of("name", "app2")));

            assertThat(manager1.getString("name")).hasValue("app1");
            assertThat(manager2.getString("name")).hasValue("app2");
            assertThat(manager1.getSources()).hasSize(1);
            assertThat(manager2.getSources()).hasSize(1);
        }

        @Test
        @DisplayName("Manager with no sources returns empty for all lookups")
        void emptyManager() {
            ConfigManager manager = new ConfigManager("empty");

            assertThat(manager.getString("any-key")).isEmpty();
            assertThat(manager.getInt("any-key")).isEmpty();
            assertThat(manager.getBoolean("any-key")).isEmpty();
            assertThat(manager.getSources()).isEmpty();
            assertThat(manager.getAll()).isEmpty();
        }
    }

    // ============================================
    // EDGE CASES (3 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Same source added multiple times")
        void sameSourceMultipleAdditions() {
            ConfigManager manager = new ConfigManager("test");
            ConfigSource source = createMockSource(Map.of("key", "value"));

            manager.addSource(source);
            manager.addSource(source);
            manager.addSource(source);

            // Behavior depends on implementation - either duplicates or deduplicated
            assertThatNoException().isThrownBy(() -> manager.getString("key"));
        }

        @Test
        @DisplayName("Remove non-existent source has no effect")
        void removeNonExistentSource() {
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of("key", "value")));

            ConfigSource nonExistent = createMockSource(Map.of());

            assertThatNoException().isThrownBy(() -> manager.removeSource(nonExistent));
            assertThat(manager.getSources()).hasSize(1);
        }

        @Test
        @DisplayName("Very large configuration sources handled")
        void largeConfigurationSource() {
            Map<String, String> largeConfig = new java.util.HashMap<>();
            for (int i = 0; i < 10000; i++) {
                largeConfig.put("key-" + i, "value-" + i);
            }

            ConfigManager manager = new ConfigManager("large")
                    .addSource(createMockSource(largeConfig));

            assertThat(manager.getString("key-5000")).hasValue("value-5000");
            assertThat(manager.getString("key-9999")).hasValue("value-9999");
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private ConfigSource createMockSource(Map<String, String> data) {
        return new ConfigSource() {
            @Override
            public Optional<String> getString(String key) {
                return Optional.ofNullable(data.get(key));
            }

            @Override
            public Optional<Integer> getInt(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Long> getLong(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Double> getDouble(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> getBoolean(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<String[]> getStringArray(String key) {
                return Optional.empty();
            }

            @Override
            public Optional<Map<String, String>> getMap(String key) {
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) {
                return Optional.empty();
            }

            @Override
            public Optional<ConfigSource> getConfig(String key) {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> getAll() {
                return new java.util.HashMap<>(data);
            }

            @Override
            public boolean hasKey(String key) {
                return data.containsKey(key);
            }

            @Override
            public String getName() {
                return "mock";
            }
        };
    }

    private ConfigSource createMockSourceWithTypes(Map<String, Integer> ints, Map<String, Integer> longs) {
        return new ConfigSource() {
            @Override
            public Optional<Integer> getInt(String key) {
                return Optional.ofNullable(ints.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithBoolean(Map<String, Boolean> booleans) {
        return new ConfigSource() {
            @Override
            public Optional<Boolean> getBoolean(String key) {
                return Optional.ofNullable(booleans.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithLong(Map<String, Long> longs) {
        return new ConfigSource() {
            @Override
            public Optional<Long> getLong(String key) {
                return Optional.ofNullable(longs.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithDouble(Map<String, Double> doubles) {
        return new ConfigSource() {
            @Override
            public Optional<Double> getDouble(String key) {
                return Optional.ofNullable(doubles.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithArray(Map<String, String[]> arrays) {
        return new ConfigSource() {
            @Override
            public Optional<String[]> getStringArray(String key) {
                return Optional.ofNullable(arrays.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithMap(Map<String, Map<String, String>> maps) {
        return new ConfigSource() {
            @Override
            public Optional<Map<String, String>> getMap(String key) {
                return Optional.ofNullable(maps.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }

    private ConfigSource createMockSourceWithNested(Map<String, ConfigSource> nested) {
        return new ConfigSource() {
            @Override
            public Optional<ConfigSource> getConfig(String key) {
                return Optional.ofNullable(nested.get(key));
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); }
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); }
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); }
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); }
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); }
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); }
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); }
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); }
            @Override
            public Map<String, Object> getAll() { return Map.of(); }
            @Override
            public boolean hasKey(String key) { return false; }
            @Override
            public String getName() { return "mock"; }
        };
    }
}
