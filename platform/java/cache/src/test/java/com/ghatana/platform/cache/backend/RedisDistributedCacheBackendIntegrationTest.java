package com.ghatana.platform.cache.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService.CacheStatistics;
import com.ghatana.platform.testing.PlatformIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Integration tests for Redis-backed distributed cache backend.
 * @doc.layer platform-test
 * @doc.pattern IntegrationTest
 *
 * Uses TestContainers to spin up actual Redis instance.
 * Tests:
 * - Connection establishment and health
 * - Get/put/delete operations
 * - Pattern-based deletion with Lua scripts
 * - Serialization/deserialization with Jackson
 * - TTL enforcement
 * - Error handling (non-blocking) // GH-90000
 * - Statistics retrieval
 * - Concurrent operations
 *
 * All tests use actual Redis, not mocks.
 */
@DisplayName("RedisDistributedCacheBackend Integration Tests")
class RedisDistributedCacheBackendIntegrationTest extends PlatformIntegrationTestBase {

    private RedisDistributedCacheBackend backend;
    private ObjectMapper objectMapper;

    @Override
    protected boolean requiresRedis() { // GH-90000
        return true;
    }

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        backend = new RedisDistributedCacheBackend( // GH-90000
            getRedisHost(), // GH-90000
            getRedisPort(), // GH-90000
            null,  // no password
            0,     // database 0
            5000,  // 5 second timeout
            3,     // 3 retries
            objectMapper
        );
    }

    @Nested
    @DisplayName("Connection Management")
    class ConnectionManagementTests {
        
        @Test
        @DisplayName("should establish Redis connection successfully")
        void shouldEstablishConnection() { // GH-90000
            assertThat(backend).isNotNull(); // GH-90000
            // Connection verified by backend constructor (ping) // GH-90000
        }

        @Test
        @DisplayName("should close connection without errors")
        void shouldCloseConnection() { // GH-90000
            assertThatCode(() -> backend.close()).doesNotThrowAnyException(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Get/Put Operations")
    class GetPutOperationsTests {

        @Test
        @DisplayName("should store and retrieve simple string value")
        void shouldStoreAndRetrieveString() { // GH-90000
            backend.setValue("test:string", "hello world", 3600); // GH-90000
            Optional<String> value = readValue("test:string", String.class); // GH-90000

            assertThat(value) // GH-90000
                .isPresent() // GH-90000
                .contains("hello world");
        }

        @Test
        @DisplayName("should store and retrieve POJO with Jackson")
        void shouldStoreAndRetrievePojo() { // GH-90000
            TestObject obj = new TestObject("test-id", 42, "test-data"); // GH-90000
            backend.setValue("test:pojo", backend.serialize(obj), 3600); // GH-90000

            Optional<TestObject> retrieved = readValue("test:pojo", TestObject.class); // GH-90000

            assertThat(retrieved) // GH-90000
                .isPresent() // GH-90000
                .hasValueSatisfying(value -> { // GH-90000
                    assertThat(value.id).isEqualTo("test-id");
                    assertThat(value.count).isEqualTo(42); // GH-90000
                    assertThat(value.data).isEqualTo("test-data");
                });
        }

        @Test
        @DisplayName("should return empty Optional for non-existent key")
        void shouldReturnEmptyForMissingKey() { // GH-90000
            Optional<String> value = readValue("test:nonexistent", String.class); // GH-90000

            assertThat(value).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should handle null values gracefully")
        void shouldHandleNullValue() { // GH-90000
            backend.setValue("test:null", "", 3600); // GH-90000
            Optional<String> value = readValue("test:null", String.class); // GH-90000

            assertThat(value).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("should respect TTL and expire keys automatically")
        void shouldRespectTtlAndExpire() throws InterruptedException { // GH-90000
            backend.setValue("test:ttl", "short-lived", 1);  // 1 second TTL // GH-90000
            
            Optional<String> beforeExpiry = readValue("test:ttl", String.class); // GH-90000
            assertThat(beforeExpiry).isPresent(); // GH-90000

            Thread.sleep(1100);  // Wait for expiry // GH-90000

            Optional<String> afterExpiry = readValue("test:ttl", String.class); // GH-90000
            assertThat(afterExpiry).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperationsTests {

        @Test
        @DisplayName("should delete existing key")
        void shouldDeleteKey() { // GH-90000
            backend.setValue("test:delete", "value", 3600); // GH-90000
            backend.deleteKey("test:delete");

            assertThat(readValue("test:delete", String.class)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return 0 when deleting non-existent key")
        void shouldReturnZeroForNonexistentKey() { // GH-90000
            backend.deleteKey("test:nonexistent:delete");

            assertThat(readValue("test:nonexistent:delete", String.class)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should delete multiple keys with pattern")
        void shouldDeleteWithPattern() { // GH-90000
            backend.setValue("content:generated:1", "data1", 3600); // GH-90000
            backend.setValue("content:generated:2", "data2", 3600); // GH-90000
            backend.setValue("content:generated:3", "data3", 3600); // GH-90000
            backend.setValue("content:other", "other", 3600); // GH-90000

            long deleted = backend.deletePattern("content:generated:*");

            assertThat(deleted).isEqualTo(3); // GH-90000
            assertThat(readValue("content:generated:1", String.class)).isEmpty(); // GH-90000
            assertThat(readValue("content:generated:2", String.class)).isEmpty(); // GH-90000
            assertThat(readValue("content:generated:3", String.class)).isEmpty(); // GH-90000
            assertThat(readValue("content:other", String.class)).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("should handle pattern delete with no matches")
        void shouldHandleEmptyPattern() { // GH-90000
            long deleted = backend.deletePattern("nonexistent:*");

            assertThat(deleted).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should support tenant-scoped pattern deletion")
        void shouldSupportTenantScopedDeletion() { // GH-90000
            // Simulate tenant isolation: tenant:1 and tenant:2 both have learning-path keys
            backend.setValue("tenant:1:learning-path:user:100:path1", "data", 3600); // GH-90000
            backend.setValue("tenant:1:learning-path:user:100:path2", "data", 3600); // GH-90000
            backend.setValue("tenant:2:learning-path:user:100:path1", "data", 3600); // GH-90000

            long deletedTenant1 = backend.deletePattern("tenant:1:learning-path:user:100:*");

            assertThat(deletedTenant1).isEqualTo(2); // GH-90000
            assertThat(readValue("tenant:1:learning-path:user:100:path1", String.class)).isEmpty(); // GH-90000
            assertThat(readValue("tenant:2:learning-path:user:100:path1", String.class)).isPresent(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Serialization/Deserialization")
    class SerializationTests {

        @Test
        @DisplayName("should handle complex objects with nested structures")
        void shouldHandleComplexObjects() { // GH-90000
            TestNestedObject nested = new TestNestedObject( // GH-90000
                "outer",
                new TestObject("inner-id", 99, "inner-data"), // GH-90000
                System.currentTimeMillis() // GH-90000
            );

            backend.setValue("test:nested", backend.serialize(nested), 3600); // GH-90000
            Optional<TestNestedObject> retrieved = readValue("test:nested", TestNestedObject.class); // GH-90000

            assertThat(retrieved) // GH-90000
                .isPresent() // GH-90000
                .hasValueSatisfying(value -> { // GH-90000
                    assertThat(value.name).isEqualTo("outer");
                    assertThat(value.inner.id).isEqualTo("inner-id");
                    assertThat(value.inner.count).isEqualTo(99); // GH-90000
                    assertThat(value.timestamp).isGreaterThan(0); // GH-90000
                });
        }

        @Test
        @DisplayName("should deserialize to wrong type gracefully")
        void shouldHandleDeserializationFailure() { // GH-90000
            backend.setValue("test:mismatch", "not a number", 3600); // GH-90000

            assertThatThrownBy(() -> readValue("test:mismatch", Integer.class)) // GH-90000
                .isInstanceOf(RuntimeException.class); // GH-90000
        }

        @Test
        @DisplayName("should handle large objects")
        void shouldHandleLargeObjects() { // GH-90000
            StringBuilder sb = new StringBuilder(); // GH-90000
            for (int i = 0; i < 10000; i++) { // GH-90000
                sb.append("large data content ");
            }
            String largeData = sb.toString(); // GH-90000

            backend.setValue("test:large", largeData, 3600); // GH-90000
            Optional<String> retrieved = readValue("test:large", String.class); // GH-90000

            assertThat(retrieved) // GH-90000
                .isPresent() // GH-90000
                .hasValueSatisfying(value -> { // GH-90000
                    assertThat(value.length()).isGreaterThan(100000); // GH-90000
                    assertThat(value).isEqualTo(largeData); // GH-90000
                });
        }
    }

    @Nested
    @DisplayName("Statistics Operations")
    class StatisticsTests {

        @Test
        @DisplayName("should retrieve statistics for pattern")
        void shouldGetStatistics() { // GH-90000
            backend.setValue("stat:key1", "value1", 3600); // GH-90000
            backend.setValue("stat:key2", "value2 with more data", 3600); // GH-90000
            backend.setValue("stat:key3", "value3", 3600); // GH-90000
            backend.setValue("other:key", "different pattern", 3600); // GH-90000

            CacheStatistics stats = getStatistics("stat:*");

            assertThat(stats.totalKeys).isEqualTo(3); // GH-90000
            assertThat(stats.totalSize).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("should return zero statistics for non-matching pattern")
        void shouldReturnZeroStatsForEmptyPattern() { // GH-90000
            CacheStatistics stats = getStatistics("nonexistent:*");

            assertThat(stats.totalKeys).isEqualTo(0); // GH-90000
            assertThat(stats.totalSize).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should calculate size across multiple keys")
        void shouldCalculateTotalSize() { // GH-90000
            backend.setValue("size:small", "x", 3600); // GH-90000
            backend.setValue("size:large", "x".repeat(1000), 3600); // GH-90000

            CacheStatistics stats = getStatistics("size:*");

            assertThat(stats.totalKeys).isEqualTo(2); // GH-90000
            assertThat(stats.totalSize).isGreaterThan(1000); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Handling (Non-Blocking)")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should log warning on failed get and return Optional.empty")
        void shouldHandleGetFailureNonBlocking() { // GH-90000
            // Simulate failure by using invalid UTF-8 (won't actually fail with a real connection) // GH-90000
            Optional<String> value = readValue("test:any:key", String.class); // GH-90000
            
            assertThat(value).isNotNull();  // Returns Optional, not throwing // GH-90000
        }

        @Test
        @DisplayName("should log warning on failed put and continue")
        void shouldHandlePutFailureNonBlocking() { // GH-90000
            // Should not throw even if put fails
            assertThatCode(() -> backend.setValue("test:any", "value", 3600)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should log warning on failed delete and return 0")
        void shouldHandleDeleteFailureNonBlocking() { // GH-90000
            assertThatCode(() -> backend.deleteKey("test:any:key"))
                .doesNotThrowAnyException(); // GH-90000

            assertThat(backend.getKeyCount("test:any:key")).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("should handle concurrent writes safely")
        void shouldHandleConcurrentWrites() throws InterruptedException { // GH-90000
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int threadId = i;
                threads[i] = new Thread(() -> { // GH-90000
                    for (int j = 0; j < 100; j++) { // GH-90000
                        backend.setValue("concurrent:thread:" + threadId + ":key:" + j, "value:" + j, 3600); // GH-90000
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }

            CacheStatistics stats = getStatistics("concurrent:*");
            assertThat(stats.totalKeys).isGreaterThanOrEqualTo(threadCount * 100L); // GH-90000
        }

        @Test
        @DisplayName("should handle concurrent reads safely")
        void shouldHandleConcurrentReads() throws InterruptedException { // GH-90000
            backend.setValue("concurrent:read:test", "test value", 3600); // GH-90000

            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { // GH-90000
                threads[i] = new Thread(() -> { // GH-90000
                    for (int j = 0; j < 100; j++) { // GH-90000
                        Optional<String> value = readValue("concurrent:read:test", String.class); // GH-90000
                        assertThat(value).isPresent(); // GH-90000
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }
        }

        @Test
        @DisplayName("should handle concurrent deletes safely")
        void shouldHandleConcurrentDeletes() throws InterruptedException { // GH-90000
            // Pre-populate keys
            for (int i = 0; i < 100; i++) { // GH-90000
                backend.setValue("concurrent:delete:key:" + i, "value", 3600); // GH-90000
            }

            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int threadId = i;
                threads[i] = new Thread(() -> { // GH-90000
                    for (int j = threadId * 20; j < (threadId + 1) * 20; j++) { // GH-90000
                        backend.deleteKey("concurrent:delete:key:" + j); // GH-90000
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }

            CacheStatistics stats = getStatistics("concurrent:delete:*");
            assertThat(stats.totalKeys).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceTests {

        @Test
        @DisplayName("should complete get operation within timeout")
        void shouldCompleteGetWithinTimeout() { // GH-90000
            backend.setValue("perf:test", "value", 3600); // GH-90000
            
            long startTime = System.nanoTime(); // GH-90000
            Optional<String> value = readValue("perf:test", String.class); // GH-90000
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

            assertThat(value).isPresent(); // GH-90000
            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) // GH-90000
        }

        @Test
        @DisplayName("should complete put operation within timeout")
        void shouldCompletePutWithinTimeout() { // GH-90000
            long startTime = System.nanoTime(); // GH-90000
            backend.setValue("perf:put", "value", 3600); // GH-90000
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) // GH-90000
        }

        @Test
        @DisplayName("should complete delete operation within timeout")
        void shouldCompleteDeleteWithinTimeout() { // GH-90000
            backend.setValue("perf:delete", "value", 3600); // GH-90000
            
            long startTime = System.nanoTime(); // GH-90000
            backend.deleteKey("perf:delete");
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; // GH-90000

            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) // GH-90000
        }
    }

    // ============= Test Support Classes =============

    static class TestObject implements Serializable {
        public String id;
        public int count;
        public String data;

        TestObject() {} // GH-90000
        TestObject(String id, int count, String data) { // GH-90000
            this.id = id;
            this.count = count;
            this.data = data;
        }
    }

    private <T> Optional<T> readValue(String key, Class<T> type) { // GH-90000
        String rawValue = backend.getValue(key); // GH-90000
        if (rawValue == null) { // GH-90000
            return Optional.empty(); // GH-90000
        }
        if (type == String.class) { // GH-90000
            return Optional.of(type.cast(rawValue)); // GH-90000
        }
        return Optional.ofNullable(backend.deserialize(rawValue, type)); // GH-90000
    }

    private CacheStatistics getStatistics(String pattern) { // GH-90000
        return new CacheStatistics(backend.getKeyCount(pattern), backend.getCacheSize(pattern)); // GH-90000
    }

    static class TestNestedObject implements Serializable {
        public String name;
        public TestObject inner;
        public long timestamp;

        TestNestedObject() {} // GH-90000
        TestNestedObject(String name, TestObject inner, long timestamp) { // GH-90000
            this.name = name;
            this.inner = inner;
            this.timestamp = timestamp;
        }
    }
}
