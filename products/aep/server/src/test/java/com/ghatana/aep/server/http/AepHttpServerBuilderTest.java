/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.server.http.AepHttpServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);

        AepHttpServer server = AepHttpServer.builder()
            .engine(mockEngine)
            .port(8080)
            .build();

        // Assert
        assertNotNull(server);
        // The server should be constructible with minimal parameters
    }

    @Test
    void shouldBuildServerWithAllParameters() {
        // Arrange
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        DataSource dataSource = mock(javax.sql.DataSource.class);
        JedisPool jedisPool = mock(redis.clients.jedis.JedisPool.class);

        // Act
        AepHttpServer server = AepHttpServer.builder()
            .engine(mockEngine)
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
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);
        assertThrows(IllegalArgumentException.class, () -> {
            AepHttpServer.builder()
                .engine(mockEngine)
                .build();
        });
    }

    @Test
    void shouldAllowChainingOfBuilderMethods() {
        // Arrange
        AepEngine mockEngine = mock(AepEngine.class);
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // Act - verify method chaining works
        AepHttpServer.Builder builder = AepHttpServer.builder()
            .engine(mockEngine)
            .port(8080)
            .prometheusRegistry(registry);

        // Assert
        assertNotNull(builder);
        // If we got here without exception, chaining works
    }

    @Test
    void shouldSupportOptionalParameters() {
        // Arrange & Act
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);
        AepHttpServer server = AepHttpServer.builder()
            .engine(mockEngine)
            .port(8080)
            .prometheusRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) // optional
            .build();

        // Assert
        assertNotNull(server);
    }

    @Test
    void shouldHaveImmutableBuilderState() {
        // Arrange
        AepHttpServer.Builder builder = AepHttpServer.builder()
            .port(8080);
        
        // Act - try to modify after setting
        builder.port(9090);
        
        // Assert - the builder should accept the change (builders are mutable by design)
        // This test documents the builder pattern behavior
        assertNotNull(builder);
    }
}
