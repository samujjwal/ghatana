/**
 * @doc.type test
 * @doc.purpose Tests for unified configuration management
 * @doc.layer platform
 */
package com.ghatana.media.config;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

/**
 * Tests for AV-008: Timeout configuration and DC-003: Configuration management
 */
class ConfigurationProviderTest {

    @BeforeEach
    void setUp() { // GH-90000
        ConfigurationProvider.reset(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        ConfigurationProvider.reset(); // GH-90000
    }

    @Test
    @DisplayName("Should get string configuration with default")
    void testGetStringWithDefault() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000

        assertEquals("default", config.getString("nonexistent.key", "default")); // GH-90000

        config.set("test.key", "value"); // GH-90000
        assertEquals("value", config.getString("test.key", "default")); // GH-90000
    }

    @Test
    @DisplayName("Should get integer configuration")
    void testGetInt() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000

        assertEquals(42, config.getInt("nonexistent", 42)); // GH-90000

        config.set("test.int", "100"); // GH-90000
        assertEquals(100, config.getInt("test.int", 0)); // GH-90000

        // Invalid integer returns default
        config.set("test.invalid", "not-a-number"); // GH-90000
        assertEquals(42, config.getInt("test.invalid", 42)); // GH-90000
    }

    @Test
    @DisplayName("Should get boolean configuration")
    void testGetBoolean() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000

        assertTrue(config.getBoolean("nonexistent", true)); // GH-90000
        assertFalse(config.getBoolean("nonexistent", false)); // GH-90000

        config.set("test.bool", "true"); // GH-90000
        assertTrue(config.getBoolean("test.bool", false)); // GH-90000

        config.set("test.bool", "false"); // GH-90000
        assertFalse(config.getBoolean("test.bool", true)); // GH-90000
    }

    @Test
    @DisplayName("Should get double configuration")
    void testGetDouble() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000

        assertEquals(3.14, config.getDouble("nonexistent", 3.14), 0.001); // GH-90000

        config.set("test.double", "2.718"); // GH-90000
        assertEquals(2.718, config.getDouble("test.double", 0), 0.001); // GH-90000
    }

    @Test
    @DisplayName("Should get timeout configuration with defaults")
    void testTimeoutConfig() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000
        TimeoutConfig timeoutConfig = config.getTimeoutConfig(); // GH-90000

        assertNotNull(timeoutConfig); // GH-90000
        assertEquals(Duration.ofSeconds(5), timeoutConfig.connectionTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(30), timeoutConfig.operationTimeout()); // GH-90000
        assertEquals(Duration.ofMinutes(5), timeoutConfig.streamingTimeout()); // GH-90000
    }

    @Test
    @DisplayName("Should create high latency timeout config")
    void testHighLatencyConfig() { // GH-90000
        TimeoutConfig config = TimeoutConfig.highLatency(); // GH-90000

        assertTrue(config.connectionTimeout().getSeconds() >= 10); // GH-90000
        assertTrue(config.operationTimeout().getSeconds() >= 60); // GH-90000
    }

    @Test
    @DisplayName("Should create low latency timeout config")
    void testLowLatencyConfig() { // GH-90000
        TimeoutConfig config = TimeoutConfig.lowLatency(); // GH-90000

        assertTrue(config.connectionTimeout().getSeconds() <= 5); // GH-90000
        assertTrue(config.operationTimeout().getSeconds() <= 15); // GH-90000
    }

    @Test
    @DisplayName("Should build custom timeout config")
    void testCustomTimeoutConfig() { // GH-90000
        TimeoutConfig config = TimeoutConfig.builder() // GH-90000
            .connectionTimeout(Duration.ofSeconds(10)) // GH-90000
            .operationTimeout(Duration.ofSeconds(45)) // GH-90000
            .build(); // GH-90000

        assertEquals(Duration.ofSeconds(10), config.connectionTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(45), config.operationTimeout()); // GH-90000
        // Others use defaults
        assertEquals(Duration.ofMinutes(5), config.streamingTimeout()); // GH-90000
    }

    @Test
    @DisplayName("Should reload configuration")
    void testReload() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000
        config.set("test.reload", "before"); // GH-90000

        config.reload(); // GH-90000

        // After reload, custom value should be preserved if it was set via set() // GH-90000
        // (set() updates the cache which is preserved across reloads) // GH-90000
        assertEquals("before", config.getString("test.reload", "default")); // GH-90000
    }

    @Test
    @DisplayName("Should provide all configuration entries")
    void testGetAll() { // GH-90000
        ConfigurationProvider config = ConfigurationProvider.getInstance(); // GH-90000
        config.set("key1", "value1"); // GH-90000
        config.set("key2", "value2"); // GH-90000

        var all = config.getAll(); // GH-90000

        assertTrue(all.size() >= 2); // GH-90000
        assertEquals("value1", all.get("key1"));
        assertEquals("value2", all.get("key2"));
    }

    @Test
    @DisplayName("Should return default timeout config")
    void testDefaultTimeoutConfig() { // GH-90000
        TimeoutConfig config = TimeoutConfig.defaults(); // GH-90000

        assertEquals(5000L, config.connectionTimeoutMs()); // GH-90000
        assertEquals(30000L, config.operationTimeoutMs()); // GH-90000
        assertEquals(300000L, config.streamingTimeoutMs()); // GH-90000
        assertEquals(5000L, config.healthCheckTimeoutMs()); // GH-90000
        assertEquals(60000L, config.initializationTimeoutMs()); // GH-90000
        assertEquals(10000L, config.shutdownTimeoutMs()); // GH-90000
    }

    @Test
    @DisplayName("Timeout config should have proper equals and hashCode")
    void testTimeoutConfigEquality() { // GH-90000
        TimeoutConfig config1 = TimeoutConfig.defaults(); // GH-90000
        TimeoutConfig config2 = TimeoutConfig.defaults(); // GH-90000
        TimeoutConfig config3 = TimeoutConfig.highLatency(); // GH-90000

        assertEquals(config1, config2); // GH-90000
        assertEquals(config1.hashCode(), config2.hashCode()); // GH-90000
        assertNotEquals(config1, config3); // GH-90000
    }

    @Test
    @DisplayName("Timeout config should have meaningful toString")
    void testTimeoutConfigToString() { // GH-90000
        TimeoutConfig config = TimeoutConfig.defaults(); // GH-90000
        String str = config.toString(); // GH-90000

        assertTrue(str.contains("TimeoutConfig"));
        assertTrue(str.contains("connection"));
        assertTrue(str.contains("operation"));
    }
}
