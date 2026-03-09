package com.ghatana.platform.database.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedisCacheConfig.
 */
class RedisCacheConfigTest {

    @Test
    void testDefaultConfiguration() {
        RedisCacheConfig config = RedisCacheConfig.builder().build();
        
        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertNull(config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
        assertEquals(86400, config.getTtlSeconds());
        assertEquals("", config.getKeyPrefix());
    }

    @Test
    void testCustomConfiguration() {
        RedisCacheConfig config = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .password("secret")
                .database(5)
                .timeout(Duration.ofSeconds(10))
                .ttlSeconds(3600)
                .keyPrefix("myapp:")
                .build();
        
        assertEquals("redis.example.com", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals("secret", config.getPassword());
        assertEquals(5, config.getDatabase());
        assertEquals(Duration.ofSeconds(10), config.getTimeout());
        assertEquals(3600, config.getTtlSeconds());
        assertEquals("myapp:", config.getKeyPrefix());
    }

    @Test
    void testToBuilder() {
        RedisCacheConfig original = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .keyPrefix("original:")
                .build();
        
        RedisCacheConfig modified = original.toBuilder()
                .database(3)
                .keyPrefix("modified:")
                .build();
        
        // Original unchanged
        assertEquals("original:", original.getKeyPrefix());
        assertEquals(0, original.getDatabase());
        
        // Modified has new values
        assertEquals("modified:", modified.getKeyPrefix());
        assertEquals(3, modified.getDatabase());
        
        // Shared values preserved
        assertEquals("redis.example.com", modified.getHost());
        assertEquals(6380, modified.getPort());
    }

    @Test
    void testInvalidPort() {
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().port(0).build()
        );
        
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().port(-1).build()
        );
        
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().port(65536).build()
        );
    }

    @Test
    void testInvalidDatabase() {
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().database(-1).build()
        );
        
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().database(16).build()
        );
    }

    @Test
    void testInvalidTtl() {
        assertThrows(IllegalArgumentException.class, () ->
                RedisCacheConfig.builder().ttlSeconds(-1).build()
        );
    }

    @Test
    void testNullHost() {
        assertThrows(NullPointerException.class, () ->
                RedisCacheConfig.builder().host(null).build()
        );
    }

    @Test
    void testNullTimeout() {
        assertThrows(NullPointerException.class, () ->
                RedisCacheConfig.builder().timeout(null).build()
        );
    }

    @Test
    void testNullKeyPrefix() {
        assertThrows(NullPointerException.class, () ->
                RedisCacheConfig.builder().keyPrefix(null).build()
        );
    }

    @Test
    void testNullPassword() {
        // Null password is allowed (no authentication)
        RedisCacheConfig config = RedisCacheConfig.builder()
                .password(null)
                .build();
        
        assertNull(config.getPassword());
    }

    @Test
    void testEquals() {
        RedisCacheConfig config1 = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .build();
        
        RedisCacheConfig config2 = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .build();
        
        RedisCacheConfig config3 = RedisCacheConfig.builder()
                .host("redis.other.com")
                .port(6380)
                .build();
        
        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertNotEquals(config1, null);
        assertNotEquals(config1, "not a config");
    }

    @Test
    void testHashCode() {
        RedisCacheConfig config1 = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .build();
        
        RedisCacheConfig config2 = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .build();
        
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToString() {
        RedisCacheConfig config = RedisCacheConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .database(5)
                .keyPrefix("test:")
                .build();
        
        String str = config.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("redis.example.com"));
        assertTrue(str.contains("6380"));
        assertTrue(str.contains("5"));
        assertTrue(str.contains("test:"));
        // Password should not be in toString for security
        assertFalse(str.contains("password"));
    }

    @Test
    void testValidPortBoundaries() {
        // Min valid port
        RedisCacheConfig minPort = RedisCacheConfig.builder().port(1).build();
        assertEquals(1, minPort.getPort());
        
        // Max valid port
        RedisCacheConfig maxPort = RedisCacheConfig.builder().port(65535).build();
        assertEquals(65535, maxPort.getPort());
    }

    @Test
    void testValidDatabaseBoundaries() {
        // Min valid database
        RedisCacheConfig minDb = RedisCacheConfig.builder().database(0).build();
        assertEquals(0, minDb.getDatabase());
        
        // Max valid database
        RedisCacheConfig maxDb = RedisCacheConfig.builder().database(15).build();
        assertEquals(15, maxDb.getDatabase());
    }

    @Test
    void testZeroTtl() {
        // Zero TTL is valid (infinite)
        RedisCacheConfig config = RedisCacheConfig.builder().ttlSeconds(0).build();
        assertEquals(0, config.getTtlSeconds());
    }
}
