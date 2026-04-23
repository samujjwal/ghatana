/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    // MULTI_SOURCE CONFIGURATION (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Source Configuration")
    class MultiSourceTests {

        @Test
        @DisplayName("Sources are queried in order (first match wins)")
        void sourceOrdering() { // GH-90000
            ConfigManager manager = new ConfigManager("test");

            ConfigSource source1 = createMockSource(Map.of("key1", "value-from-1")); // GH-90000
            ConfigSource source2 = createMockSource(Map.of("key1", "value-from-2", "key2", "value-2")); // GH-90000

            manager.addSource(source1); // GH-90000
            manager.addSource(source2); // GH-90000

            Optional<String> value = manager.getString("key1");
            assertThat(value).hasValue("value-from-1"); // First source wins

            Optional<String> value2 = manager.getString("key2");
            assertThat(value2).hasValue("value-2"); // Falls through to second source
        }

        @Test
        @DisplayName("Many sources coexist in configuration chain")
        void manySourcesChain() { // GH-90000
            ConfigManager manager = new ConfigManager("multi-source");

            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                ConfigSource source = createMockSource( // GH-90000
                    Map.of("key-" + idx, "value-" + idx)); // GH-90000
                manager.addSource(source); // GH-90000
            }

            assertThat(manager.getSources()).hasSize(10); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                Optional<String> value = manager.getString("key-" + i); // GH-90000
                assertThat(value).hasValue("value-" + i); // GH-90000
            }
        }

        @Test
        @DisplayName("Adding sources via builder chainable API")
        void chainableSourceAddition() { // GH-90000
            ConfigManager manager = new ConfigManager("chained")
                    .addSource(createMockSource(Map.of("a", "1"))) // GH-90000
                    .addSource(createMockSource(Map.of("b", "2"))) // GH-90000
                    .addSource(createMockSource(Map.of("c", "3"))); // GH-90000

            assertThat(manager.getSources()).hasSize(3); // GH-90000
            assertThat(manager.getString("a")).hasValue("1");
            assertThat(manager.getString("b")).hasValue("2");
            assertThat(manager.getString("c")).hasValue("3");
        }

        @Test
        @DisplayName("Remove source invalidates future lookups")
        void removeSourceImpact() { // GH-90000
            ConfigManager manager = new ConfigManager("test");
            ConfigSource source = createMockSource(Map.of("key", "value")); // GH-90000

            manager.addSource(source); // GH-90000
            assertThat(manager.getString("key")).hasValue("value");

            manager.removeSource(source); // GH-90000
            assertThat(manager.getString("key")).isEmpty();
        }

        @Test
        @DisplayName("Clear sources removes all configuration")
        void clearAllSources() { // GH-90000
            ConfigManager manager = new ConfigManager("test");

            for (int i = 0; i < 5; i++) { // GH-90000
                manager.addSource(createMockSource(Map.of("key-" + i, "value"))); // GH-90000
            }

            assertThat(manager.getSources()).hasSize(5); // GH-90000

            manager.clearSources(); // GH-90000

            assertThat(manager.getSources()).isEmpty(); // GH-90000
            assertThat(manager.getString("key-0")).isEmpty();
        }
    }

    // ============================================
    // TYPE CONVERSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Type Conversion")
    class TypeConversionTests {

        @Test
        @DisplayName("Integer configuration values converted correctly")
        void integerConversion() { // GH-90000
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithTypes( // GH-90000
                        Map.of("port", 8080), // GH-90000
                        Map.of("timeout", 5000))); // GH-90000

            Optional<Integer> port = manager.getInt("port");
            assertThat(port).hasValue(8080); // GH-90000
        }

        @Test
        @DisplayName("Boolean configuration values converted correctly")
        void booleanConversion() { // GH-90000
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithBoolean(Map.of( // GH-90000
                        "enabled", true,
                        "debug", false)));

            Optional<Boolean> enabled = manager.getBoolean("enabled");
            assertThat(enabled).hasValue(true); // GH-90000

            Optional<Boolean> debug = manager.getBoolean("debug");
            assertThat(debug).hasValue(false); // GH-90000
        }

        @Test
        @DisplayName("Long configuration values converted correctly")
        void longConversion() { // GH-90000
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithLong(Map.of( // GH-90000
                        "largeNumber", 9223372036854775L)));

            Optional<Long> large = manager.getLong("largeNumber");
            assertThat(large).hasValue(9223372036854775L); // GH-90000
        }

        @Test
        @DisplayName("Double configuration values converted correctly")
        void doubleConversion() { // GH-90000
            ConfigManager manager = new ConfigManager("types")
                    .addSource(createMockSourceWithDouble(Map.of( // GH-90000
                        "pi", 3.14159)));

            Optional<Double> pi = manager.getDouble("pi");
            assertThat(pi).hasValue(3.14159); // GH-90000
        }
    }

    // ============================================
    // KEYSET & LOOKUPS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Key Detection & Lookups")
    class KeyDetectionTests {

        @Test
        @DisplayName("hasKey detects existing keys across sources")
        void hasKeyDetection() { // GH-90000
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of( // GH-90000
                        "exists-1", "value",
                        "exists-2", "value")))
                    .addSource(createMockSource(Map.of( // GH-90000
                        "exists-3", "value")));

            assertThat(manager.hasKey("exists-1")).isTrue();
            assertThat(manager.hasKey("exists-2")).isTrue();
            assertThat(manager.hasKey("exists-3")).isTrue();
            assertThat(manager.hasKey("does-not-exist")).isFalse();
        }

        @Test
        @DisplayName("getAll returns merged configuration map")
        void getAllMerged() { // GH-90000
            ConfigManager manager = new ConfigManager("merged")
                    .addSource(createMockSource(Map.of( // GH-90000
                        "key1", "value1",
                        "key2", "value2")))
                    .addSource(createMockSource(Map.of( // GH-90000
                        "key3", "value3")));

            Map<String, Object> all = manager.getAll(); // GH-90000

            assertThat(all).containsKeys("key1", "key2", "key3"); // GH-90000
        }

        @Test
        @DisplayName("Missing keys return empty Optional")
        void missingKeyHandling() { // GH-90000
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of("exists", "value"))); // GH-90000

            Optional<String> missing = manager.getString("nonexistent");
            assertThat(missing).isEmpty(); // GH-90000

            Optional<Integer> missingInt = manager.getInt("nonexistent");
            assertThat(missingInt).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Overlapping keys in sources prioritized by order")
        void keyPriority() { // GH-90000
            ConfigManager manager = new ConfigManager("priority")
                    .addSource(createMockSource(Map.of("shared", "first"))) // GH-90000
                    .addSource(createMockSource(Map.of("shared", "second"))) // GH-90000
                    .addSource(createMockSource(Map.of("shared", "third"))); // GH-90000

            Optional<String> value = manager.getString("shared");
            assertThat(value).hasValue("first"); // First source wins
        }
    }

    // ============================================
    // ARRAYS & COMPLEX TYPES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Arrays & Complex Types")
    class ComplexTypeTests {

        @Test
        @DisplayName("String array configuration retrieved correctly")
        void stringArrayRetrieval() { // GH-90000
            ConfigManager manager = new ConfigManager("arrays")
                    .addSource(createMockSourceWithArray(Map.of( // GH-90000
                        "servers", new String[]{"server1", "server2", "server3"})));

            Optional<String[]> servers = manager.getStringArray("servers");
            assertThat(servers).hasValue(new String[]{"server1", "server2", "server3"}); // GH-90000
        }

        @Test
        @DisplayName("Configuration map nested retrieval")
        void mapRetrieval() { // GH-90000
            ConfigManager manager = new ConfigManager("maps")
                    .addSource(createMockSourceWithMap(Map.of( // GH-90000
                        "database", Map.of("host", "localhost", "port", "5432", "name", "config_db")))); // GH-90000

            Optional<Map<String, String>> dbConfig = manager.getMap("database");
            assertThat(dbConfig).isPresent(); // GH-90000
            assertThat(dbConfig.get()).containsEntry("host", "localhost"); // GH-90000
        }

        @Test
        @DisplayName("Nested configuration objects accessed hierarchically")
        void nestedConfig() { // GH-90000
            ConfigManager manager = new ConfigManager("nested")
                    .addSource(createMockSourceWithNested(Map.of( // GH-90000
                        "app.settings", createMockSource(Map.of("threads", "10"))))); // GH-90000

            Optional<ConfigSource> nested = manager.getConfig("app.settings");
            assertThat(nested).isPresent(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENCY & THREAD SAFETY (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrency & Thread Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many threads reading config simultaneously")
        void concurrentReads() throws Exception { // GH-90000
            ConfigManager manager = new ConfigManager("concurrent")
                    .addSource(createMockSource(Map.of( // GH-90000
                        "key1", "value1",
                        "key2", "value2",
                        "key3", "value3")));

            int threadCount = 20;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    executor.submit(() -> { // GH-90000
                        try {
                            Optional<String> value = manager.getString("key" + (idx % 3 + 1)); // GH-90000
                            if (value.isPresent()) { // GH-90000
                                successCount.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Concurrent source addition and reading")
        void concurrentModificationSafety() throws Exception { // GH-90000
            ConfigManager manager = new ConfigManager("concurrent-mod");

            int readThreads = 10;
            int writeThreads = 5;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(readThreads + writeThreads); // GH-90000
            AtomicInteger operations = new AtomicInteger(0); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(readThreads + writeThreads); // GH-90000
            try {
                // Reader threads
                for (int i = 0; i < readThreads; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            for (int j = 0; j < 10; j++) { // GH-90000
                                manager.getString("some-key");
                                operations.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                // Writer threads
                for (int i = 0; i < writeThreads; i++) { // GH-90000
                    final int idx = i;
                    executor.submit(() -> { // GH-90000
                        try {
                            manager.addSource(createMockSource(Map.of("key-" + idx, "value-" + idx))); // GH-90000
                            operations.incrementAndGet(); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(operations.get()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("Rapid source add/remove cycles")
        void rapidModification() { // GH-90000
            ConfigManager manager = new ConfigManager("rapid");

            for (int cycle = 0; cycle < 100; cycle++) { // GH-90000
                ConfigSource source = createMockSource(Map.of("cycle-" + cycle, "value")); // GH-90000
                manager.addSource(source); // GH-90000
                assertThat(manager.getSources()).isNotEmpty(); // GH-90000
                manager.removeSource(source); // GH-90000
                assertThat(manager.getString("cycle-" + cycle)).isEmpty(); // GH-90000
            }
        }
    }

    // ============================================
    // MANAGER INSTANCES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Manager Instances")
    class InstanceTests {

        @Test
        @DisplayName("Multiple manager instances are independent")
        void multipleManagerInstances() { // GH-90000
            ConfigManager manager1 = new ConfigManager("app1")
                    .addSource(createMockSource(Map.of("name", "app1"))); // GH-90000

            ConfigManager manager2 = new ConfigManager("app2")
                    .addSource(createMockSource(Map.of("name", "app2"))); // GH-90000

            assertThat(manager1.getString("name")).hasValue("app1");
            assertThat(manager2.getString("name")).hasValue("app2");
            assertThat(manager1.getSources()).hasSize(1); // GH-90000
            assertThat(manager2.getSources()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("Manager with no sources returns empty for all lookups")
        void emptyManager() { // GH-90000
            ConfigManager manager = new ConfigManager("empty");

            assertThat(manager.getString("any-key")).isEmpty();
            assertThat(manager.getInt("any-key")).isEmpty();
            assertThat(manager.getBoolean("any-key")).isEmpty();
            assertThat(manager.getSources()).isEmpty(); // GH-90000
            assertThat(manager.getAll()).isEmpty(); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Same source added multiple times")
        void sameSourceMultipleAdditions() { // GH-90000
            ConfigManager manager = new ConfigManager("test");
            ConfigSource source = createMockSource(Map.of("key", "value")); // GH-90000

            manager.addSource(source); // GH-90000
            manager.addSource(source); // GH-90000
            manager.addSource(source); // GH-90000

            // Behavior depends on implementation - either duplicates or deduplicated
            assertThatNoException().isThrownBy(() -> manager.getString("key"));
        }

        @Test
        @DisplayName("Remove non-existent source has no effect")
        void removeNonExistentSource() { // GH-90000
            ConfigManager manager = new ConfigManager("test")
                    .addSource(createMockSource(Map.of("key", "value"))); // GH-90000

            ConfigSource nonExistent = createMockSource(Map.of()); // GH-90000

            assertThatNoException().isThrownBy(() -> manager.removeSource(nonExistent)); // GH-90000
            assertThat(manager.getSources()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("Very large configuration sources handled")
        void largeConfigurationSource() { // GH-90000
            Map<String, String> largeConfig = new java.util.HashMap<>(); // GH-90000
            for (int i = 0; i < 10000; i++) { // GH-90000
                largeConfig.put("key-" + i, "value-" + i); // GH-90000
            }

            ConfigManager manager = new ConfigManager("large")
                    .addSource(createMockSource(largeConfig)); // GH-90000

            assertThat(manager.getString("key-5000")).hasValue("value-5000");
            assertThat(manager.getString("key-9999")).hasValue("value-9999");
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private ConfigSource createMockSource(Map<String, String> data) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<String> getString(String key) { // GH-90000
                return Optional.ofNullable(data.get(key)); // GH-90000
            }

            @Override
            public Optional<Integer> getInt(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<Long> getLong(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<Double> getDouble(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<Boolean> getBoolean(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<String[]> getStringArray(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<Map<String, String>> getMap(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Optional<ConfigSource> getConfig(String key) { // GH-90000
                return Optional.empty(); // GH-90000
            }

            @Override
            public Map<String, Object> getAll() { // GH-90000
                return new java.util.HashMap<>(data); // GH-90000
            }

            @Override
            public boolean hasKey(String key) { // GH-90000
                return data.containsKey(key); // GH-90000
            }

            @Override
            public String getName() { // GH-90000
                return "mock";
            }
        };
    }

    private ConfigSource createMockSourceWithTypes(Map<String, Integer> ints, Map<String, Integer> longs) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { // GH-90000
                return Optional.ofNullable(ints.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithBoolean(Map<String, Boolean> booleans) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { // GH-90000
                return Optional.ofNullable(booleans.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithLong(Map<String, Long> longs) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<Long> getLong(String key) { // GH-90000
                return Optional.ofNullable(longs.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithDouble(Map<String, Double> doubles) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { // GH-90000
                return Optional.ofNullable(doubles.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithArray(Map<String, String[]> arrays) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { // GH-90000
                return Optional.ofNullable(arrays.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithMap(Map<String, Map<String, String>> maps) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { // GH-90000
                return Optional.ofNullable(maps.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }

    private ConfigSource createMockSourceWithNested(Map<String, ConfigSource> nested) { // GH-90000
        return new ConfigSource() { // GH-90000
            @Override
            public Optional<ConfigSource> getConfig(String key) { // GH-90000
                return Optional.ofNullable(nested.get(key)); // GH-90000
            }

            @Override
            public Optional<String> getString(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Integer> getInt(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Long> getLong(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Double> getDouble(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Boolean> getBoolean(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<String[]> getStringArray(String key) { return Optional.empty(); } // GH-90000
            @Override
            public Optional<Map<String, String>> getMap(String key) { return Optional.empty(); } // GH-90000
            @Override
            public <T> Optional<T> getObject(String key, Class<T> type) { return Optional.empty(); } // GH-90000
            @Override
            public Map<String, Object> getAll() { return Map.of(); } // GH-90000
            @Override
            public boolean hasKey(String key) { return false; } // GH-90000
            @Override
            public String getName() { return "mock"; } // GH-90000
        };
    }
}
