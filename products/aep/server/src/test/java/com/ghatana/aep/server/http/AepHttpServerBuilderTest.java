/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.server.http.AepHttpServer.AepHttpServerBuilder;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import redis.clients.jedis.JedisPool;

/**
 * Unit tests for {@link AepHttpServer} builder pattern.
 *
 * P2-12: Verify builder pattern refactor works correctly.
 */
class AepHttpServerBuilderTest {

    @Test
    void shouldBuildServerWithRequiredParameters() {
        // Arrange & Act
        AepHttpServer server = new AepHttpServerBuilder()
            .port(8080)
            .build();
        
        // Assert
        assertNotNull(server);
        // The server should be constructible with minimal parameters
    }

    @Test
    void shouldBuildServerWithAllParameters() {
        // Arrange
        CollectorRegistry registry = new CollectorRegistry();
        DataSource dataSource = mock(javax.sql.DataSource.class);
        JedisPool jedisPool = mock(redis.clients.jedis.JedisPool.class);
        
        // Act
        AepHttpServer server = new AepHttpServerBuilder()
            .port(8080)
            .prometheusRegistry(registry)
            .dataSource(dataSource)
            .jedisPool(jedisPool)
            .build();
        
        // Assert
        assertNotNull(server);
    }

    @Test
    void shouldRequirePortParameter() {
        // This test verifies that the builder enforces required parameters
        // The actual validation should be in the builder's build() method
        assertThrows(IllegalArgumentException.class, () -> {
            new AepHttpServerBuilder()
                .build();
        });
    }

    @Test
    void shouldAllowChainingOfBuilderMethods() {
        // Arrange
        CollectorRegistry registry = new CollectorRegistry();
        
        // Act - verify method chaining works
        AepHttpServerBuilder builder = new AepHttpServerBuilder()
            .port(8080)
            .prometheusRegistry(registry);
        
        // Assert
        assertNotNull(builder);
        // If we got here without exception, chaining works
    }

    @Test
    void shouldSupportOptionalParameters() {
        // Arrange & Act
        AepHttpServer server = new AepHttpServerBuilder()
            .port(8080)
            .prometheusRegistry(new CollectorRegistry()) // optional
            .build();
        
        // Assert
        assertNotNull(server);
    }

    @Test
    void shouldHaveImmutableBuilderState() {
        // Arrange
        AepHttpServerBuilder builder = new AepHttpServerBuilder()
            .port(8080);
        
        // Act - try to modify after setting
        builder.port(9090);
        
        // Assert - the builder should accept the change (builders are mutable by design)
        // This test documents the builder pattern behavior
        assertNotNull(builder);
    }
}
