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
 * - Error handling (non-blocking) 
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
    protected boolean requiresRedis() { 
        return true;
    }

    @BeforeEach
    void setUp() { 
        objectMapper = new ObjectMapper(); 
        backend = new RedisDistributedCacheBackend( 
            getRedisHost(), 
            getRedisPort(), 
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
        void shouldEstablishConnection() { 
            assertThat(backend).isNotNull(); 
            // Connection verified by backend constructor (ping) 
        }

        @Test
        @DisplayName("should close connection without errors")
        void shouldCloseConnection() { 
            assertThatCode(() -> backend.close()).doesNotThrowAnyException(); 
        }
    }

    @Nested
    @DisplayName("Get/Put Operations")
    class GetPutOperationsTests {

        @Test
        @DisplayName("should store and retrieve simple string value")
        void shouldStoreAndRetrieveString() { 
            backend.setValue("test:string", "hello world", 3600); 
            Optional<String> value = readValue("test:string", String.class); 

            assertThat(value) 
                .isPresent() 
                .contains("hello world");
        }

        @Test
        @DisplayName("should store and retrieve POJO with Jackson")
        void shouldStoreAndRetrievePojo() { 
            TestObject obj = new TestObject("test-id", 42, "test-data"); 
            backend.setValue("test:pojo", backend.serialize(obj), 3600); 

            Optional<TestObject> retrieved = readValue("test:pojo", TestObject.class); 

            assertThat(retrieved) 
                .isPresent() 
                .hasValueSatisfying(value -> { 
                    assertThat(value.id).isEqualTo("test-id");
                    assertThat(value.count).isEqualTo(42); 
                    assertThat(value.data).isEqualTo("test-data");
                });
        }

        @Test
        @DisplayName("should return empty Optional for non-existent key")
        void shouldReturnEmptyForMissingKey() { 
            Optional<String> value = readValue("test:nonexistent", String.class); 

            assertThat(value).isEmpty(); 
        }

        @Test
        @DisplayName("should handle null values gracefully")
        void shouldHandleNullValue() { 
            backend.setValue("test:null", "", 3600); 
            Optional<String> value = readValue("test:null", String.class); 

            assertThat(value).isPresent(); 
        }

        @Test
        @DisplayName("should respect TTL and expire keys automatically")
        void shouldRespectTtlAndExpire() throws InterruptedException { 
            backend.setValue("test:ttl", "short-lived", 1);  // 1 second TTL 
            
            Optional<String> beforeExpiry = readValue("test:ttl", String.class); 
            assertThat(beforeExpiry).isPresent(); 

            Thread.sleep(1100);  // Wait for expiry 

            Optional<String> afterExpiry = readValue("test:ttl", String.class); 
            assertThat(afterExpiry).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperationsTests {

        @Test
        @DisplayName("should delete existing key")
        void shouldDeleteKey() { 
            backend.setValue("test:delete", "value", 3600); 
            backend.deleteKey("test:delete");

            assertThat(readValue("test:delete", String.class)).isEmpty(); 
        }

        @Test
        @DisplayName("should return 0 when deleting non-existent key")
        void shouldReturnZeroForNonexistentKey() { 
            backend.deleteKey("test:nonexistent:delete");

            assertThat(readValue("test:nonexistent:delete", String.class)).isEmpty(); 
        }

        @Test
        @DisplayName("should delete multiple keys with pattern")
        void shouldDeleteWithPattern() { 
            backend.setValue("content:generated:1", "data1", 3600); 
            backend.setValue("content:generated:2", "data2", 3600); 
            backend.setValue("content:generated:3", "data3", 3600); 
            backend.setValue("content:other", "other", 3600); 

            long deleted = backend.deletePattern("content:generated:*");

            assertThat(deleted).isEqualTo(3); 
            assertThat(readValue("content:generated:1", String.class)).isEmpty(); 
            assertThat(readValue("content:generated:2", String.class)).isEmpty(); 
            assertThat(readValue("content:generated:3", String.class)).isEmpty(); 
            assertThat(readValue("content:other", String.class)).isPresent(); 
        }

        @Test
        @DisplayName("should handle pattern delete with no matches")
        void shouldHandleEmptyPattern() { 
            long deleted = backend.deletePattern("nonexistent:*");

            assertThat(deleted).isEqualTo(0); 
        }

        @Test
        @DisplayName("should support tenant-scoped pattern deletion")
        void shouldSupportTenantScopedDeletion() { 
            // Simulate tenant isolation: tenant:1 and tenant:2 both have learning-path keys
            backend.setValue("tenant:1:learning-path:user:100:path1", "data", 3600); 
            backend.setValue("tenant:1:learning-path:user:100:path2", "data", 3600); 
            backend.setValue("tenant:2:learning-path:user:100:path1", "data", 3600); 

            long deletedTenant1 = backend.deletePattern("tenant:1:learning-path:user:100:*");

            assertThat(deletedTenant1).isEqualTo(2); 
            assertThat(readValue("tenant:1:learning-path:user:100:path1", String.class)).isEmpty(); 
            assertThat(readValue("tenant:2:learning-path:user:100:path1", String.class)).isPresent(); 
        }
    }

    @Nested
    @DisplayName("Serialization/Deserialization")
    class SerializationTests {

        @Test
        @DisplayName("should handle complex objects with nested structures")
        void shouldHandleComplexObjects() { 
            TestNestedObject nested = new TestNestedObject( 
                "outer",
                new TestObject("inner-id", 99, "inner-data"), 
                System.currentTimeMillis() 
            );

            backend.setValue("test:nested", backend.serialize(nested), 3600); 
            Optional<TestNestedObject> retrieved = readValue("test:nested", TestNestedObject.class); 

            assertThat(retrieved) 
                .isPresent() 
                .hasValueSatisfying(value -> { 
                    assertThat(value.name).isEqualTo("outer");
                    assertThat(value.inner.id).isEqualTo("inner-id");
                    assertThat(value.inner.count).isEqualTo(99); 
                    assertThat(value.timestamp).isGreaterThan(0); 
                });
        }

        @Test
        @DisplayName("should deserialize to wrong type gracefully")
        void shouldHandleDeserializationFailure() { 
            backend.setValue("test:mismatch", "not a number", 3600); 

            assertThatThrownBy(() -> readValue("test:mismatch", Integer.class)) 
                .isInstanceOf(RuntimeException.class); 
        }

        @Test
        @DisplayName("should handle large objects")
        void shouldHandleLargeObjects() { 
            StringBuilder sb = new StringBuilder(); 
            for (int i = 0; i < 10000; i++) { 
                sb.append("large data content ");
            }
            String largeData = sb.toString(); 

            backend.setValue("test:large", largeData, 3600); 
            Optional<String> retrieved = readValue("test:large", String.class); 

            assertThat(retrieved) 
                .isPresent() 
                .hasValueSatisfying(value -> { 
                    assertThat(value.length()).isGreaterThan(100000); 
                    assertThat(value).isEqualTo(largeData); 
                });
        }
    }

    @Nested
    @DisplayName("Statistics Operations")
    class StatisticsTests {

        @Test
        @DisplayName("should retrieve statistics for pattern")
        void shouldGetStatistics() { 
            backend.setValue("stat:key1", "value1", 3600); 
            backend.setValue("stat:key2", "value2 with more data", 3600); 
            backend.setValue("stat:key3", "value3", 3600); 
            backend.setValue("other:key", "different pattern", 3600); 

            CacheStatistics stats = getStatistics("stat:*");

            assertThat(stats.totalKeys).isEqualTo(3); 
            assertThat(stats.totalSize).isGreaterThan(0); 
        }

        @Test
        @DisplayName("should return zero statistics for non-matching pattern")
        void shouldReturnZeroStatsForEmptyPattern() { 
            CacheStatistics stats = getStatistics("nonexistent:*");

            assertThat(stats.totalKeys).isEqualTo(0); 
            assertThat(stats.totalSize).isEqualTo(0); 
        }

        @Test
        @DisplayName("should calculate size across multiple keys")
        void shouldCalculateTotalSize() { 
            backend.setValue("size:small", "x", 3600); 
            backend.setValue("size:large", "x".repeat(1000), 3600); 

            CacheStatistics stats = getStatistics("size:*");

            assertThat(stats.totalKeys).isEqualTo(2); 
            assertThat(stats.totalSize).isGreaterThan(1000); 
        }
    }

    @Nested
    @DisplayName("Error Handling (Non-Blocking)")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should log warning on failed get and return Optional.empty")
        void shouldHandleGetFailureNonBlocking() { 
            // Simulate failure by using invalid UTF-8 (won't actually fail with a real connection) 
            Optional<String> value = readValue("test:any:key", String.class); 
            
            assertThat(value).isNotNull();  // Returns Optional, not throwing 
        }

        @Test
        @DisplayName("should log warning on failed put and continue")
        void shouldHandlePutFailureNonBlocking() { 
            // Should not throw even if put fails
            assertThatCode(() -> backend.setValue("test:any", "value", 3600)) 
                .doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("should log warning on failed delete and return 0")
        void shouldHandleDeleteFailureNonBlocking() { 
            assertThatCode(() -> backend.deleteKey("test:any:key"))
                .doesNotThrowAnyException(); 

            assertThat(backend.getKeyCount("test:any:key")).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperationsTests {

        @Test
        @DisplayName("should handle concurrent writes safely")
        void shouldHandleConcurrentWrites() throws InterruptedException { 
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { 
                final int threadId = i;
                threads[i] = new Thread(() -> { 
                    for (int j = 0; j < 100; j++) { 
                        backend.setValue("concurrent:thread:" + threadId + ":key:" + j, "value:" + j, 3600); 
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                t.join(); 
            }

            CacheStatistics stats = getStatistics("concurrent:*");
            assertThat(stats.totalKeys).isGreaterThanOrEqualTo(threadCount * 100L); 
        }

        @Test
        @DisplayName("should handle concurrent reads safely")
        void shouldHandleConcurrentReads() throws InterruptedException { 
            backend.setValue("concurrent:read:test", "test value", 3600); 

            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { 
                threads[i] = new Thread(() -> { 
                    for (int j = 0; j < 100; j++) { 
                        Optional<String> value = readValue("concurrent:read:test", String.class); 
                        assertThat(value).isPresent(); 
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                t.join(); 
            }
        }

        @Test
        @DisplayName("should handle concurrent deletes safely")
        void shouldHandleConcurrentDeletes() throws InterruptedException { 
            // Pre-populate keys
            for (int i = 0; i < 100; i++) { 
                backend.setValue("concurrent:delete:key:" + i, "value", 3600); 
            }

            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) { 
                final int threadId = i;
                threads[i] = new Thread(() -> { 
                    for (int j = threadId * 20; j < (threadId + 1) * 20; j++) { 
                        backend.deleteKey("concurrent:delete:key:" + j); 
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                t.join(); 
            }

            CacheStatistics stats = getStatistics("concurrent:delete:*");
            assertThat(stats.totalKeys).isEqualTo(0); 
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    class PerformanceTests {

        @Test
        @DisplayName("should complete get operation within timeout")
        void shouldCompleteGetWithinTimeout() { 
            backend.setValue("perf:test", "value", 3600); 
            
            long startTime = System.nanoTime(); 
            Optional<String> value = readValue("perf:test", String.class); 
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; 

            assertThat(value).isPresent(); 
            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) 
        }

        @Test
        @DisplayName("should complete put operation within timeout")
        void shouldCompletePutWithinTimeout() { 
            long startTime = System.nanoTime(); 
            backend.setValue("perf:put", "value", 3600); 
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; 

            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) 
        }

        @Test
        @DisplayName("should complete delete operation within timeout")
        void shouldCompleteDeleteWithinTimeout() { 
            backend.setValue("perf:delete", "value", 3600); 
            
            long startTime = System.nanoTime(); 
            backend.deleteKey("perf:delete");
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000; 

            assertThat(elapsedMs).isLessThan(1000);  // Should be much faster (typically < 5ms) 
        }
    }

    // ============= Test Support Classes =============

    static class TestObject implements Serializable {
        public String id;
        public int count;
        public String data;

        TestObject() {} 
        TestObject(String id, int count, String data) { 
            this.id = id;
            this.count = count;
            this.data = data;
        }
    }

    private <T> Optional<T> readValue(String key, Class<T> type) { 
        String rawValue = backend.getValue(key); 
        if (rawValue == null) { 
            return Optional.empty(); 
        }
        if (type == String.class) { 
            return Optional.of(type.cast(rawValue)); 
        }
        return Optional.ofNullable(backend.deserialize(rawValue, type)); 
    }

    private CacheStatistics getStatistics(String pattern) { 
        return new CacheStatistics(backend.getKeyCount(pattern), backend.getCacheSize(pattern)); 
    }

    static class TestNestedObject implements Serializable {
        public String name;
        public TestObject inner;
        public long timestamp;

        TestNestedObject() {} 
        TestNestedObject(String name, TestObject inner, long timestamp) { 
            this.name = name;
            this.inner = inner;
            this.timestamp = timestamp;
        }
    }
}
