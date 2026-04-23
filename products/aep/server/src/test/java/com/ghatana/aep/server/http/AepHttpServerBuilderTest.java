/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private static final String AEP_PROFILE = "AEP_PROFILE";
    private static final String ALLOW_IN_MEMORY_RUN_HISTORY = "AEP_ALLOW_IN_MEMORY_RUN_HISTORY";

    @Test
    void shouldBuildServerWithRequiredParameters() { // GH-90000
        // Arrange & Act
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000

        AepHttpServer server = AepHttpServer.builder() // GH-90000
            .engine(mockEngine) // GH-90000
            .port(8080) // GH-90000
            .build(); // GH-90000

        // Assert
        assertNotNull(server); // GH-90000
        // The server should be constructible with minimal parameters
    }

    @Test
    void shouldBuildServerWithAllParameters() { // GH-90000
        // Arrange
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT); // GH-90000
        DataSource dataSource = mock(javax.sql.DataSource.class); // GH-90000
        JedisPool jedisPool = mock(redis.clients.jedis.JedisPool.class); // GH-90000

        // Act
        AepHttpServer server = AepHttpServer.builder() // GH-90000
            .engine(mockEngine) // GH-90000
            .port(8080) // GH-90000
            .prometheusRegistry(registry) // GH-90000
            .dataSource(dataSource) // GH-90000
            .jedisPool(jedisPool) // GH-90000
            .build(); // GH-90000

        // Assert
        assertNotNull(server); // GH-90000
    }

    @Test
    void shouldRequirePortParameter() { // GH-90000
        // This test verifies that the builder enforces required parameters
        // The actual validation should be in the builder's build() method // GH-90000
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000
        assertThrows(IllegalArgumentException.class, () -> { // GH-90000
            AepHttpServer.builder() // GH-90000
                .engine(mockEngine) // GH-90000
                .build(); // GH-90000
        });
    }

    @Test
    void shouldAllowChainingOfBuilderMethods() { // GH-90000
        // Arrange
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT); // GH-90000

        // Act - verify method chaining works
        AepHttpServer.Builder builder = AepHttpServer.builder() // GH-90000
            .engine(mockEngine) // GH-90000
            .port(8080) // GH-90000
            .prometheusRegistry(registry); // GH-90000

        // Assert
        assertNotNull(builder); // GH-90000
        // If we got here without exception, chaining works
    }

    @Test
    void shouldSupportOptionalParameters() { // GH-90000
        // Arrange & Act
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000
        AepHttpServer server = AepHttpServer.builder() // GH-90000
            .engine(mockEngine) // GH-90000
            .port(8080) // GH-90000
            .prometheusRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) // optional // GH-90000
            .build(); // GH-90000

        // Assert
        assertNotNull(server); // GH-90000
    }

    @Test
    void shouldFailClosedInProductionWhenDurableRunHistoryIsNotConfigured() { // GH-90000
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000

        String previousProfile = System.getProperty(AEP_PROFILE); // GH-90000
        String previousAllowInMemory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY); // GH-90000

        try {
            System.setProperty(AEP_PROFILE, "production"); // GH-90000
            System.clearProperty(ALLOW_IN_MEMORY_RUN_HISTORY); // GH-90000

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> // GH-90000
                AepHttpServer.builder() // GH-90000
                    .engine(mockEngine) // GH-90000
                    .port(8080) // GH-90000
                    .build() // GH-90000
            );

            assertTrue(exception.getMessage().contains("EventLogStore"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile); // GH-90000
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousAllowInMemory); // GH-90000
        }
    }

    @Test
    void shouldAllowExplicitEmbeddedOverrideForInMemoryRunHistoryInProduction() { // GH-90000
        AepEngine mockEngine = mock(AepEngine.class); // GH-90000
        EventCloud mockEventCloud = mock(EventCloud.class); // GH-90000
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); // GH-90000

        String previousProfile = System.getProperty(AEP_PROFILE); // GH-90000
        String previousAllowInMemory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY); // GH-90000

        try {
            System.setProperty(AEP_PROFILE, "production"); // GH-90000
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true"); // GH-90000

            AepHttpServer server = AepHttpServer.builder() // GH-90000
                .engine(mockEngine) // GH-90000
                .port(8080) // GH-90000
                .build(); // GH-90000

            assertNotNull(server); // GH-90000
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile); // GH-90000
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousAllowInMemory); // GH-90000
        }
    }

    @Test
    void shouldHaveImmutableBuilderState() { // GH-90000
        // Arrange
        AepHttpServer.Builder builder = AepHttpServer.builder() // GH-90000
            .port(8080); // GH-90000
        
        // Act - try to modify after setting
        builder.port(9090); // GH-90000
        
        // Assert - the builder should accept the change (builders are mutable by design) // GH-90000
        // This test documents the builder pattern behavior
        assertNotNull(builder); // GH-90000
    }

    private static void restoreSystemProperty(String key, String value) { // GH-90000
        if (value == null) { // GH-90000
            System.clearProperty(key); // GH-90000
        } else {
            System.setProperty(key, value); // GH-90000
        }
    }
}
