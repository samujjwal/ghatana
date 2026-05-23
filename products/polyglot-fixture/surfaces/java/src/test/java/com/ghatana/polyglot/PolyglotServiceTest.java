package com.ghatana.polyglot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for Polyglot Fixture Java Service
 * 
 * @doc.type class
 * @doc.purpose Tests for Java service surface
 * @doc.layer product
 * @doc.pattern Test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolyglotServiceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        var response = restTemplate.getForObject("/health", PolyglotService.HealthResponse.class);
        assertNotNull(response);
        assertEquals("UP", response.status());
        assertEquals("java-service", response.service());
    }

    @Test
    void pingEndpointReturnsPong() {
        var response = restTemplate.getForObject("/api/ping", PolyglotService.PingResponse.class);
        assertNotNull(response);
        assertEquals("pong", response.message());
        assertNotNull(response.timestamp());
    }
}
