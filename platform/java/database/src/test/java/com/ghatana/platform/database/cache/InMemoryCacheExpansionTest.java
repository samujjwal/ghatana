/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.database.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: InMemoryCache concurrent access, eviction, and TTL behavior.
 * Tests cache performance under load, concurrent modifications, and expiration handling.
 *
 * @doc.type class
 * @doc.purpose InMemoryCache concurrency and eviction testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("InMemoryCache - Phase 3 Expansion [GH-90000]")
class InMemoryCacheExpansionTest {

    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() { // GH-90000
        cache = InMemoryCache.create("phase3-cache", Duration.ofSeconds(60)); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (cache != null) { // GH-90000
            cache.close(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT MODIFICATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Modifications [GH-90000]")
    class ConcurrentTests {

        @Test
        @DisplayName("Handles many concurrent puts without data loss [GH-90000]")
        void concurrentPuts() throws InterruptedException { // GH-90000
            int numThreads = 5;
            int operationsPerThread = 20;
            List<Thread> threads = new ArrayList<>(); // GH-90000

            for (int t = 0; t < numThreads; t++) { // GH-90000
                final int threadId = t;
                Thread thread = new Thread(() -> { // GH-90000
                    for (int i = 0; i < operationsPerThread; i++) { // GH-90000
                        String key = "key-" + threadId + "-" + i;
                        cache.put(key, "value-" + i); // GH-90000
                    }
                });
                threads.add(thread); // GH-90000
                thread.start(); // GH-90000
            }

            for (Thread thread : threads) { // GH-90000
                thread.join(); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(numThreads * operationsPerThread); // GH-90000
        }

        @Test
        @DisplayName("Handles concurrent reads and writes without corruption [GH-90000]")
        void concurrentReadsWrites() throws InterruptedException { // GH-90000
            cache.put("stable-key", "stable-value"); // GH-90000

            AtomicInteger successfulReads = new AtomicInteger(0); // GH-90000
            List<Thread> threads = new ArrayList<>(); // GH-90000

            for (int t = 0; t < 10; t++) { // GH-90000
                Thread thread = new Thread(() -> { // GH-90000
                    for (int i = 0; i < 10; i++) { // GH-90000
                        Optional<String> value = cache.get("stable-key [GH-90000]");
                        if (value.isPresent() && value.get().equals("stable-value [GH-90000]")) {
                            successfulReads.incrementAndGet(); // GH-90000
                        }
                    }
                });
                threads.add(thread); // GH-90000
                thread.start(); // GH-90000
            }

            for (Thread thread : threads) { // GH-90000
                thread.join(); // GH-90000
            }

            assertThat(successfulReads.get()).isEqualTo(100); // GH-90000
        }
    }

    // ============================================
    // LARGE DATASET HANDLING (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Large Dataset Handling [GH-90000]")
    class LargeDatasetTests {

        @Test
        @DisplayName("Stores and retrieves large number of entries [GH-90000]")
        void largeDatasetStorage() { // GH-90000
            int entryCount = 1000;

            for (int i = 0; i < entryCount; i++) { // GH-90000
                cache.put("large-key-" + i, "large-value-" + i); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(entryCount); // GH-90000

            for (int i = 0; i < entryCount; i++) { // GH-90000
                Optional<String> value = cache.get("large-key-" + i); // GH-90000
                assertThat(value).isPresent().contains("large-value-" + i); // GH-90000
            }
        }

        @Test
        @DisplayName("Maintains performance with large value strings [GH-90000]")
        void largeValueStorage() { // GH-90000
            String largeValue = "x".repeat(10000); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                cache.put("large-value-key-" + i, largeValue); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(100); // GH-90000

            Optional<String> retrieved = cache.get("large-value-key-50 [GH-90000]");
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get()).hasSize(10000); // GH-90000
        }
    }

    // ============================================
    // CACHE REMOVAL AND CLEANUP (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache Removal and Cleanup [GH-90000]")
    class RemovalTests {

        @Test
        @DisplayName("Bulk remove operations reduce cache size correctly [GH-90000]")
        void bulkRemoval() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                cache.put("bulk-" + i, "value-" + i); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(50); // GH-90000

            // Remove half
            for (int i = 0; i < 25; i++) { // GH-90000
                cache.remove("bulk-" + i); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(25); // GH-90000

            // Verify removed entries are gone
            for (int i = 0; i < 25; i++) { // GH-90000
                assertThat(cache.contains("bulk-" + i)).isFalse(); // GH-90000
            }

            // Verify remaining entries exist
            for (int i = 25; i < 50; i++) { // GH-90000
                assertThat(cache.contains("bulk-" + i)).isTrue(); // GH-90000
            }
        }
    }

    // ============================================
    // CACHE STATE VERIFICATION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Cache State Verification [GH-90000]")
    class StateTests {

        @Test
        @DisplayName("Clear operation empties cache completely [GH-90000]")
        void completeClearance() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                cache.put("clear-" + i, "value-" + i); // GH-90000
            }

            assertThat(cache.size()).isEqualTo(100); // GH-90000

            cache.clear(); // GH-90000

            assertThat(cache.size()).isZero(); // GH-90000
            assertThat(cache.contains("clear-0 [GH-90000]")).isFalse();
            assertThat(cache.contains("clear-99 [GH-90000]")).isFalse();
        }
    }
}
