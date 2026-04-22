package com.ghatana.platform.http.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpClientConfig.
 */
class HttpClientConfigTest {

    @Test
    void testDefaultConfiguration() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder().build(); // GH-90000

        assertEquals(Duration.ofSeconds(10), config.getConnectTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(30), config.getReadTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(30), config.getCallTimeout()); // GH-90000
        assertEquals(10, config.getMaxConnections()); // GH-90000
        assertEquals(Duration.ofMinutes(5), config.getKeepAliveDuration()); // GH-90000
        assertTrue(config.isRetryOnConnectionFailure()); // GH-90000
        assertEquals(10.0, config.getRequestsPerSecond()); // GH-90000
        assertNull(config.getUserAgent()); // GH-90000
    }

    @Test
    void testCustomConfiguration() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .readTimeout(Duration.ofSeconds(15)) // GH-90000
                .callTimeout(Duration.ofSeconds(60)) // GH-90000
                .maxConnections(20) // GH-90000
                .keepAliveDuration(Duration.ofMinutes(10)) // GH-90000
                .retryOnConnectionFailure(false) // GH-90000
                .requestsPerSecond(5.0) // GH-90000
                .userAgent("MyApp/1.0 [GH-90000]")
                .build(); // GH-90000

        assertEquals(Duration.ofSeconds(5), config.getConnectTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(15), config.getReadTimeout()); // GH-90000
        assertEquals(Duration.ofSeconds(60), config.getCallTimeout()); // GH-90000
        assertEquals(20, config.getMaxConnections()); // GH-90000
        assertEquals(Duration.ofMinutes(10), config.getKeepAliveDuration()); // GH-90000
        assertFalse(config.isRetryOnConnectionFailure()); // GH-90000
        assertEquals(5.0, config.getRequestsPerSecond()); // GH-90000
        assertEquals("MyApp/1.0", config.getUserAgent()); // GH-90000
    }

    @Test
    void testToBuilder() { // GH-90000
        HttpClientConfig original = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .userAgent("Original/1.0 [GH-90000]")
                .build(); // GH-90000

        HttpClientConfig modified = original.toBuilder() // GH-90000
                .maxConnections(50) // GH-90000
                .userAgent("Modified/2.0 [GH-90000]")
                .build(); // GH-90000

        // Original unchanged
        assertEquals("Original/1.0", original.getUserAgent()); // GH-90000
        assertEquals(10, original.getMaxConnections()); // GH-90000

        // Modified has new values
        assertEquals("Modified/2.0", modified.getUserAgent()); // GH-90000
        assertEquals(50, modified.getMaxConnections()); // GH-90000

        // Shared values preserved
        assertEquals(Duration.ofSeconds(5), modified.getConnectTimeout()); // GH-90000
    }

    @Test
    void testInvalidMaxConnections() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                HttpClientConfig.builder().maxConnections(0).build() // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                HttpClientConfig.builder().maxConnections(-1).build() // GH-90000
        );
    }

    @Test
    void testInvalidRequestsPerSecond() { // GH-90000
        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                HttpClientConfig.builder().requestsPerSecond(0).build() // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
                HttpClientConfig.builder().requestsPerSecond(-1).build() // GH-90000
        );
    }

    @Test
    void testNullConnectTimeout() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                HttpClientConfig.builder().connectTimeout(null).build() // GH-90000
        );
    }

    @Test
    void testNullReadTimeout() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                HttpClientConfig.builder().readTimeout(null).build() // GH-90000
        );
    }

    @Test
    void testNullCallTimeout() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                HttpClientConfig.builder().callTimeout(null).build() // GH-90000
        );
    }

    @Test
    void testNullKeepAliveDuration() { // GH-90000
        assertThrows(NullPointerException.class, () -> // GH-90000
                HttpClientConfig.builder().keepAliveDuration(null).build() // GH-90000
        );
    }

    @Test
    void testNullUserAgent() { // GH-90000
        // Null user agent is allowed
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .userAgent(null) // GH-90000
                .build(); // GH-90000

        assertNull(config.getUserAgent()); // GH-90000
    }

    @Test
    void testEquals() { // GH-90000
        HttpClientConfig config1 = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

        HttpClientConfig config2 = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

        HttpClientConfig config3 = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(10)) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

        assertEquals(config1, config2); // GH-90000
        assertNotEquals(config1, config3); // GH-90000
        assertNotEquals(config1, null); // GH-90000
        assertNotEquals(config1, "not a config"); // GH-90000
    }

    @Test
    void testHashCode() { // GH-90000
        HttpClientConfig config1 = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

        HttpClientConfig config2 = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .maxConnections(20) // GH-90000
                .build(); // GH-90000

        assertEquals(config1.hashCode(), config2.hashCode()); // GH-90000
    }

    @Test
    void testToString() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .maxConnections(20) // GH-90000
                .userAgent("TestAgent/1.0 [GH-90000]")
                .build(); // GH-90000

        String str = config.toString(); // GH-90000

        assertNotNull(str); // GH-90000
        assertTrue(str.contains("connectTimeout [GH-90000]"));
        assertTrue(str.contains("maxConnections [GH-90000]"));
        assertTrue(str.contains("20 [GH-90000]"));
        assertTrue(str.contains("TestAgent/1.0 [GH-90000]"));
    }
}
