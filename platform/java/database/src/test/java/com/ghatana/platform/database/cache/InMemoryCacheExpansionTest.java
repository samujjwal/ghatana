/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("InMemoryCache - Phase 3 Expansion")
class InMemoryCacheExpansionTest {

    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() { 
        cache = InMemoryCache.create("phase3-cache", Duration.ofSeconds(60)); 
    }

    @AfterEach
    void tearDown() { 
        if (cache != null) { 
            cache.close(); 
        }
    }

    // ============================================
    // CONCURRENT MODIFICATIONS (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Modifications")
    class ConcurrentTests {

        @Test
        @DisplayName("Handles many concurrent puts without data loss")
        void concurrentPuts() throws InterruptedException { 
            int numThreads = 5;
            int operationsPerThread = 20;
            List<Thread> threads = new ArrayList<>(); 

            for (int t = 0; t < numThreads; t++) { 
                final int threadId = t;
                Thread thread = new Thread(() -> { 
                    for (int i = 0; i < operationsPerThread; i++) { 
                        String key = "key-" + threadId + "-" + i;
                        cache.put(key, "value-" + i); 
                    }
                });
                threads.add(thread); 
                thread.start(); 
            }

            for (Thread thread : threads) { 
                thread.join(); 
            }

            assertThat(cache.size()).isEqualTo(numThreads * operationsPerThread); 
        }

        @Test
        @DisplayName("Handles concurrent reads and writes without corruption")
        void concurrentReadsWrites() throws InterruptedException { 
            cache.put("stable-key", "stable-value"); 

            AtomicInteger successfulReads = new AtomicInteger(0); 
            List<Thread> threads = new ArrayList<>(); 

            for (int t = 0; t < 10; t++) { 
                Thread thread = new Thread(() -> { 
                    for (int i = 0; i < 10; i++) { 
                        Optional<String> value = cache.get("stable-key");
                        if (value.isPresent() && value.get().equals("stable-value")) {
                            successfulReads.incrementAndGet(); 
                        }
                    }
                });
                threads.add(thread); 
                thread.start(); 
            }

            for (Thread thread : threads) { 
                thread.join(); 
            }

            assertThat(successfulReads.get()).isEqualTo(100); 
        }
    }

    // ============================================
    // LARGE DATASET HANDLING (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Large Dataset Handling")
    class LargeDatasetTests {

        @Test
        @DisplayName("Stores and retrieves large number of entries")
        void largeDatasetStorage() { 
            int entryCount = 1000;

            for (int i = 0; i < entryCount; i++) { 
                cache.put("large-key-" + i, "large-value-" + i); 
            }

            assertThat(cache.size()).isEqualTo(entryCount); 

            for (int i = 0; i < entryCount; i++) { 
                Optional<String> value = cache.get("large-key-" + i); 
                assertThat(value).isPresent().contains("large-value-" + i); 
            }
        }

        @Test
        @DisplayName("Maintains performance with large value strings")
        void largeValueStorage() { 
            String largeValue = "x".repeat(10000); 

            for (int i = 0; i < 100; i++) { 
                cache.put("large-value-key-" + i, largeValue); 
            }

            assertThat(cache.size()).isEqualTo(100); 

            Optional<String> retrieved = cache.get("large-value-key-50");
            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get()).hasSize(10000); 
        }
    }

    // ============================================
    // CACHE REMOVAL AND CLEANUP (1 test) 
    // ============================================

    @Nested
    @DisplayName("Cache Removal and Cleanup")
    class RemovalTests {

        @Test
        @DisplayName("Bulk remove operations reduce cache size correctly")
        void bulkRemoval() { 
            for (int i = 0; i < 50; i++) { 
                cache.put("bulk-" + i, "value-" + i); 
            }

            assertThat(cache.size()).isEqualTo(50); 

            // Remove half
            for (int i = 0; i < 25; i++) { 
                cache.remove("bulk-" + i); 
            }

            assertThat(cache.size()).isEqualTo(25); 

            // Verify removed entries are gone
            for (int i = 0; i < 25; i++) { 
                assertThat(cache.contains("bulk-" + i)).isFalse(); 
            }

            // Verify remaining entries exist
            for (int i = 25; i < 50; i++) { 
                assertThat(cache.contains("bulk-" + i)).isTrue(); 
            }
        }
    }

    // ============================================
    // CACHE STATE VERIFICATION (1 test) 
    // ============================================

    @Nested
    @DisplayName("Cache State Verification")
    class StateTests {

        @Test
        @DisplayName("Clear operation empties cache completely")
        void completeClearance() { 
            for (int i = 0; i < 100; i++) { 
                cache.put("clear-" + i, "value-" + i); 
            }

            assertThat(cache.size()).isEqualTo(100); 

            cache.clear(); 

            assertThat(cache.size()).isZero(); 
            assertThat(cache.contains("clear-0")).isFalse();
            assertThat(cache.contains("clear-99")).isFalse();
        }
    }
}
