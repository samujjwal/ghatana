package com.ghatana.platform.http.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpClientConfig.
 */
class HttpClientConfigTest {

    @Test
    void testDefaultConfiguration() {
        HttpClientConfig config = HttpClientConfig.builder().build();
        
        assertEquals(Duration.ofSeconds(10), config.getConnectTimeout());
        assertEquals(Duration.ofSeconds(30), config.getReadTimeout());
        assertEquals(Duration.ofSeconds(30), config.getCallTimeout());
        assertEquals(10, config.getMaxConnections());
        assertEquals(Duration.ofMinutes(5), config.getKeepAliveDuration());
        assertTrue(config.isRetryOnConnectionFailure());
        assertEquals(10.0, config.getRequestsPerSecond());
        assertNull(config.getUserAgent());
    }

    @Test
    void testCustomConfiguration() {
        HttpClientConfig config = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .callTimeout(Duration.ofSeconds(60))
                .maxConnections(20)
                .keepAliveDuration(Duration.ofMinutes(10))
                .retryOnConnectionFailure(false)
                .requestsPerSecond(5.0)
                .userAgent("MyApp/1.0")
                .build();
        
        assertEquals(Duration.ofSeconds(5), config.getConnectTimeout());
        assertEquals(Duration.ofSeconds(15), config.getReadTimeout());
        assertEquals(Duration.ofSeconds(60), config.getCallTimeout());
        assertEquals(20, config.getMaxConnections());
        assertEquals(Duration.ofMinutes(10), config.getKeepAliveDuration());
        assertFalse(config.isRetryOnConnectionFailure());
        assertEquals(5.0, config.getRequestsPerSecond());
        assertEquals("MyApp/1.0", config.getUserAgent());
    }

    @Test
    void testToBuilder() {
        HttpClientConfig original = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .userAgent("Original/1.0")
                .build();
        
        HttpClientConfig modified = original.toBuilder()
                .maxConnections(50)
                .userAgent("Modified/2.0")
                .build();
        
        // Original unchanged
        assertEquals("Original/1.0", original.getUserAgent());
        assertEquals(10, original.getMaxConnections());
        
        // Modified has new values
        assertEquals("Modified/2.0", modified.getUserAgent());
        assertEquals(50, modified.getMaxConnections());
        
        // Shared values preserved
        assertEquals(Duration.ofSeconds(5), modified.getConnectTimeout());
    }

    @Test
    void testInvalidMaxConnections() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpClientConfig.builder().maxConnections(0).build()
        );
        
        assertThrows(IllegalArgumentException.class, () ->
                HttpClientConfig.builder().maxConnections(-1).build()
        );
    }

    @Test
    void testInvalidRequestsPerSecond() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpClientConfig.builder().requestsPerSecond(0).build()
        );
        
        assertThrows(IllegalArgumentException.class, () ->
                HttpClientConfig.builder().requestsPerSecond(-1).build()
        );
    }

    @Test
    void testNullConnectTimeout() {
        assertThrows(NullPointerException.class, () ->
                HttpClientConfig.builder().connectTimeout(null).build()
        );
    }

    @Test
    void testNullReadTimeout() {
        assertThrows(NullPointerException.class, () ->
                HttpClientConfig.builder().readTimeout(null).build()
        );
    }

    @Test
    void testNullCallTimeout() {
        assertThrows(NullPointerException.class, () ->
                HttpClientConfig.builder().callTimeout(null).build()
        );
    }

    @Test
    void testNullKeepAliveDuration() {
        assertThrows(NullPointerException.class, () ->
                HttpClientConfig.builder().keepAliveDuration(null).build()
        );
    }

    @Test
    void testNullUserAgent() {
        // Null user agent is allowed
        HttpClientConfig config = HttpClientConfig.builder()
                .userAgent(null)
                .build();
        
        assertNull(config.getUserAgent());
    }

    @Test
    void testEquals() {
        HttpClientConfig config1 = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .maxConnections(20)
                .build();
        
        HttpClientConfig config2 = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .maxConnections(20)
                .build();
        
        HttpClientConfig config3 = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .maxConnections(20)
                .build();
        
        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertNotEquals(config1, null);
        assertNotEquals(config1, "not a config");
    }

    @Test
    void testHashCode() {
        HttpClientConfig config1 = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .maxConnections(20)
                .build();
        
        HttpClientConfig config2 = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .maxConnections(20)
                .build();
        
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToString() {
        HttpClientConfig config = HttpClientConfig.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .maxConnections(20)
                .userAgent("TestAgent/1.0")
                .build();
        
        String str = config.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("connectTimeout"));
        assertTrue(str.contains("maxConnections"));
        assertTrue(str.contains("20"));
        assertTrue(str.contains("TestAgent/1.0"));
    }
}
