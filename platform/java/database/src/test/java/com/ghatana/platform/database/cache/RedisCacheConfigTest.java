package com.ghatana.platform.database.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedisCacheConfig.
 */
class RedisCacheConfigTest {

    @Test
    void testDefaultConfiguration() { // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder().build(); // GH-90000

        assertEquals("localhost", config.getHost()); // GH-90000
        assertEquals(6379, config.getPort()); // GH-90000
        assertNull(config.getPassword()); // GH-90000
        assertEquals(0, config.getDatabase()); // GH-90000
        assertEquals(Duration.ofSeconds(5), config.getTimeout()); // GH-90000
        assertEquals(86400, config.getTtlSeconds()); // GH-90000
        assertEquals("", config.getKeyPrefix()); // GH-90000
    }

    @Test
    void testCustomConfiguration() { // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .password("secret [GH-90000]")
                .database(5) // GH-90000
                .timeout(Duration.ofSeconds(10)) // GH-90000
                .ttlSeconds(3600) // GH-90000
                .keyPrefix("myapp: [GH-90000]")
                .build(); // GH-90000

        assertEquals("redis.example.com", config.getHost()); // GH-90000
        assertEquals(6380, config.getPort()); // GH-90000
        assertEquals("secret", config.getPassword()); // GH-90000
        assertEquals(5, config.getDatabase()); // GH-90000
        assertEquals(Duration.ofSeconds(10), config.getTimeout()); // GH-90000
        assertEquals(3600, config.getTtlSeconds()); // GH-90000
        assertEquals("myapp:", config.getKeyPrefix()); // GH-90000
    }

    @Test
    void testToBuilder() { // GH-90000
        RedisCacheConfig original = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .keyPrefix("original: [GH-90000]")
                .build(); // GH-90000

        RedisCacheConfig modified = original.toBuilder() // GH-90000
                .database(3) // GH-90000
                .keyPrefix("modified: [GH-90000]")
                .build(); // GH-90000

        // Original unchanged
        assertEquals("original:", original.getKeyPrefix()); // GH-90000
        assertEquals(0, original.getDatabase()); // GH-90000

        // Modified has new values
        assertEquals("modified:", modified.getKeyPrefix()); // GH-90000
        assertEquals(3, modified.getDatabase()); // GH-90000

        // Shared values preserved
        assertEquals("redis.example.com", modified.getHost()); // GH-90000
        assertEquals(6380, modified.getPort()); // GH-90000
    }

    @Test
    void testInvalidPort() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().port(0).build() // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().port(-1).build() // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().port(65536).build() // GH-90000
        );
    }

    @Test
    void testInvalidDatabase() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().database(-1).build() // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().database(16).build() // GH-90000
        );
    }

    @Test
    void testInvalidTtl() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                RedisCacheConfig.builder().ttlSeconds(-1).build() // GH-90000
        );
    }

    @Test
    void testNullHost() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                RedisCacheConfig.builder().host(null).build() // GH-90000
        );
    }

    @Test
    void testNullTimeout() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                RedisCacheConfig.builder().timeout(null).build() // GH-90000
        );
    }

    @Test
    void testNullKeyPrefix() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                RedisCacheConfig.builder().keyPrefix(null).build() // GH-90000
        );
    }

    @Test
    void testNullPassword() { // GH-90000
        // Null password is allowed (no authentication) // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder() // GH-90000
                .password(null) // GH-90000
                .build(); // GH-90000

        assertNull(config.getPassword()); // GH-90000
    }

    @Test
    void testEquals() { // GH-90000
        RedisCacheConfig config1 = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .build(); // GH-90000

        RedisCacheConfig config2 = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .build(); // GH-90000

        RedisCacheConfig config3 = RedisCacheConfig.builder() // GH-90000
                .host("redis.other.com [GH-90000]")
                .port(6380) // GH-90000
                .build(); // GH-90000

        assertEquals(config1, config2); // GH-90000
        assertNotEquals(config1, config3); // GH-90000
        assertNotEquals(config1, null); // GH-90000
        assertNotEquals(config1, "not a config"); // GH-90000
    }

    @Test
    void testHashCode() { // GH-90000
        RedisCacheConfig config1 = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .build(); // GH-90000

        RedisCacheConfig config2 = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .build(); // GH-90000

        assertEquals(config1.hashCode(), config2.hashCode()); // GH-90000
    }

    @Test
    void testToString() { // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder() // GH-90000
                .host("redis.example.com [GH-90000]")
                .port(6380) // GH-90000
                .database(5) // GH-90000
                .keyPrefix("test: [GH-90000]")
                .build(); // GH-90000

        String str = config.toString(); // GH-90000

        assertNotNull(str); // GH-90000
        assertTrue(str.contains("redis.example.com [GH-90000]"));
        assertTrue(str.contains("6380 [GH-90000]"));
        assertTrue(str.contains("5 [GH-90000]"));
        assertTrue(str.contains("test: [GH-90000]"));
        // Password should not be in toString for security
        assertFalse(str.contains("password [GH-90000]"));
    }

    @Test
    void testValidPortBoundaries() { // GH-90000
        // Min valid port
        RedisCacheConfig minPort = RedisCacheConfig.builder().port(1).build(); // GH-90000
        assertEquals(1, minPort.getPort()); // GH-90000

        // Max valid port
        RedisCacheConfig maxPort = RedisCacheConfig.builder().port(65535).build(); // GH-90000
        assertEquals(65535, maxPort.getPort()); // GH-90000
    }

    @Test
    void testValidDatabaseBoundaries() { // GH-90000
        // Min valid database
        RedisCacheConfig minDb = RedisCacheConfig.builder().database(0).build(); // GH-90000
        assertEquals(0, minDb.getDatabase()); // GH-90000

        // Max valid database
        RedisCacheConfig maxDb = RedisCacheConfig.builder().database(15).build(); // GH-90000
        assertEquals(15, maxDb.getDatabase()); // GH-90000
    }

    @Test
    void testZeroTtl() { // GH-90000
        // Zero TTL is valid (infinite) // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder().ttlSeconds(0).build(); // GH-90000
        assertEquals(0, config.getTtlSeconds()); // GH-90000
    }
}
