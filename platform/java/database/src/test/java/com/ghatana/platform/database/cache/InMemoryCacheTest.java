package com.ghatana.platform.database.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryCache.
 */
class InMemoryCacheTest {

    private InMemoryCache<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = InMemoryCache.create("test-cache", Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    void testPutAndGet() {
        cache.put("key1", "value1");
        
        Optional<String> result = cache.get("key1");
        
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void testGetNonExistent() {
        Optional<String> result = cache.get("nonexistent");
        
        assertFalse(result.isPresent());
    }

    @Test
    void testRemove() {
        cache.put("key1", "value1");
        cache.remove("key1");
        
        Optional<String> result = cache.get("key1");
        
        assertFalse(result.isPresent());
    }

    @Test
    void testContains() {
        cache.put("key1", "value1");
        
        assertTrue(cache.contains("key1"));
        assertFalse(cache.contains("nonexistent"));
    }

    @Test
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        cache.clear();
        
        assertFalse(cache.contains("key1"));
        assertFalse(cache.contains("key2"));
        assertFalse(cache.contains("key3"));
        assertEquals(0, cache.size());
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());
        
        cache.put("key1", "value1");
        assertEquals(1, cache.size());
        
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
        
        cache.remove("key1");
        assertEquals(1, cache.size());
    }

    @Test
    void testOverwrite() {
        cache.put("key1", "value1");
        cache.put("key1", "value2");
        
        Optional<String> result = cache.get("key1");
        
        assertTrue(result.isPresent());
        assertEquals("value2", result.get());
        assertEquals(1, cache.size());
    }

    @Test
    void testExpiration() throws InterruptedException {
        InMemoryCache<String, String> shortTtlCache = InMemoryCache.create("short-ttl-cache", Duration.ofMillis(100));
        try {
            shortTtlCache.put("key1", "value1");
            assertTrue(shortTtlCache.get("key1").isPresent());
            
            // Wait for expiration
            TimeUnit.MILLISECONDS.sleep(150);
            
            assertFalse(shortTtlCache.get("key1").isPresent());
        } finally {
            shortTtlCache.close();
        }
    }

    @Test
    void testMultipleKeys() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        assertEquals("value1", cache.get("key1").orElse(null));
        assertEquals("value2", cache.get("key2").orElse(null));
        assertEquals("value3", cache.get("key3").orElse(null));
    }

    @Test
    void testNullKey() {
        assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThrows(NullPointerException.class, () -> cache.remove(null));
        assertThrows(NullPointerException.class, () -> cache.contains(null));
    }

    @Test
    void testNullValue() {
        assertThrows(NullPointerException.class, () -> cache.put("key", null));
    }

    @Test
    void testRemoveNonExistent() {
        // Should not throw
        cache.remove("nonexistent");
        assertEquals(0, cache.size());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "key-" + threadId + "-" + j;
                    cache.put(key, "value-" + j);
                    cache.get(key);
                }
            });
        }
        
        for (Thread thread : threads) {
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Should have entries from all threads
        assertTrue(cache.size() > 0);
    }
}
