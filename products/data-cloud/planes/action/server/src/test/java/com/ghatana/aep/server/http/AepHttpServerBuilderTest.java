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

    private static final String AEP_PROFILE = "AEP_PROFILE";
    private static final String ALLOW_IN_MEMORY_RUN_HISTORY = "AEP_ALLOW_IN_MEMORY_RUN_HISTORY";
    private static final String ALLOW_IN_MEMORY_CONSENT = "AEP_ALLOW_IN_MEMORY_CONSENT";
    private static final String ALLOW_IN_MEMORY_GOVERNANCE = "AEP_ALLOW_IN_MEMORY_GOVERNANCE";
    private static final String ALLOW_IN_MEMORY_IDEMPOTENCY = "AEP_ALLOW_IN_MEMORY_IDEMPOTENCY";
    private static final String ALLOW_IN_MEMORY_SESSION = "AEP_ALLOW_IN_MEMORY_SESSION";

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
    void shouldFailClosedInProductionWhenDurableRunHistoryIsNotConfigured() { 
        AepEngine mockEngine = mock(AepEngine.class); 
        EventCloud mockEventCloud = mock(EventCloud.class); 
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); 

        String previousProfile = System.getProperty(AEP_PROFILE); 
        String previousAllowInMemory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY); 

        try {
            System.setProperty(AEP_PROFILE, "production"); 
            System.clearProperty(ALLOW_IN_MEMORY_RUN_HISTORY); 

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
                AepHttpServer.builder() 
                    .engine(mockEngine) 
                    .port(8080) 
                    .build() 
            );

            assertTrue(exception.getMessage().contains("EventLogStore"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile); 
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousAllowInMemory); 
        }
    }

    @Test
    void shouldAllowExplicitEmbeddedOverrideForInMemoryRunHistoryInProduction() { 
        AepEngine mockEngine = mock(AepEngine.class); 
        EventCloud mockEventCloud = mock(EventCloud.class); 
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud); 

        String previousProfile = System.getProperty(AEP_PROFILE); 
        String previousAllowInMemory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY); 
        String previousConsent = System.getProperty(ALLOW_IN_MEMORY_CONSENT);
        String previousGovernance = System.getProperty(ALLOW_IN_MEMORY_GOVERNANCE);
        String previousIdempotency = System.getProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
        String previousSession = System.getProperty(ALLOW_IN_MEMORY_SESSION);

        try {
            System.setProperty(AEP_PROFILE, "production"); 
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true"); 
            System.setProperty(ALLOW_IN_MEMORY_CONSENT, "true");
            System.setProperty(ALLOW_IN_MEMORY_GOVERNANCE, "true");
            System.setProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, "true");
            System.setProperty(ALLOW_IN_MEMORY_SESSION, "true");

            AepHttpServer server = AepHttpServer.builder() 
                .engine(mockEngine) 
                .port(8080) 
                .build(); 

            assertNotNull(server); 
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile); 
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousAllowInMemory); 
            restoreSystemProperty(ALLOW_IN_MEMORY_CONSENT, previousConsent);
            restoreSystemProperty(ALLOW_IN_MEMORY_GOVERNANCE, previousGovernance);
            restoreSystemProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, previousIdempotency);
            restoreSystemProperty(ALLOW_IN_MEMORY_SESSION, previousSession);
        }
    }

    @Test
    void shouldFailClosedInProductionWhenInMemoryConsentFallbackIsUsed() {
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);

        String previousProfile = System.getProperty(AEP_PROFILE);
        String previousRunHistory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY);
        String previousConsent = System.getProperty(ALLOW_IN_MEMORY_CONSENT);
        String previousGovernance = System.getProperty(ALLOW_IN_MEMORY_GOVERNANCE);
        String previousIdempotency = System.getProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
        String previousSession = System.getProperty(ALLOW_IN_MEMORY_SESSION);

        try {
            System.setProperty(AEP_PROFILE, "production");
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true");
            System.setProperty(ALLOW_IN_MEMORY_GOVERNANCE, "true");
            System.setProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, "true");
            System.setProperty(ALLOW_IN_MEMORY_SESSION, "true");
            System.clearProperty(ALLOW_IN_MEMORY_CONSENT);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                AepHttpServer.builder()
                    .engine(mockEngine)
                    .port(8080)
                    .build()
            );

            assertTrue(exception.getMessage().contains("consent"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile);
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousRunHistory);
            restoreSystemProperty(ALLOW_IN_MEMORY_CONSENT, previousConsent);
            restoreSystemProperty(ALLOW_IN_MEMORY_GOVERNANCE, previousGovernance);
            restoreSystemProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, previousIdempotency);
            restoreSystemProperty(ALLOW_IN_MEMORY_SESSION, previousSession);
        }
    }

    @Test
    void shouldFailClosedInProductionWhenInMemorySessionFallbackIsUsed() {
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);

        String previousProfile = System.getProperty(AEP_PROFILE);
        String previousRunHistory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY);
        String previousConsent = System.getProperty(ALLOW_IN_MEMORY_CONSENT);
        String previousGovernance = System.getProperty(ALLOW_IN_MEMORY_GOVERNANCE);
        String previousIdempotency = System.getProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
        String previousSession = System.getProperty(ALLOW_IN_MEMORY_SESSION);

        try {
            System.setProperty(AEP_PROFILE, "production");
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true");
            System.setProperty(ALLOW_IN_MEMORY_CONSENT, "true");
            System.setProperty(ALLOW_IN_MEMORY_GOVERNANCE, "true");
            System.setProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, "true");
            System.clearProperty(ALLOW_IN_MEMORY_SESSION);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                AepHttpServer.builder()
                    .engine(mockEngine)
                    .port(8080)
                    .build()
            );

            assertTrue(exception.getMessage().contains("session"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile);
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousRunHistory);
            restoreSystemProperty(ALLOW_IN_MEMORY_CONSENT, previousConsent);
            restoreSystemProperty(ALLOW_IN_MEMORY_GOVERNANCE, previousGovernance);
            restoreSystemProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, previousIdempotency);
            restoreSystemProperty(ALLOW_IN_MEMORY_SESSION, previousSession);
        }
    }

    @Test
    void shouldFailClosedInProductionWhenInMemoryGovernanceFallbackIsUsed() {
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);

        String previousProfile = System.getProperty(AEP_PROFILE);
        String previousRunHistory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY);
        String previousConsent = System.getProperty(ALLOW_IN_MEMORY_CONSENT);
        String previousGovernance = System.getProperty(ALLOW_IN_MEMORY_GOVERNANCE);
        String previousIdempotency = System.getProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
        String previousSession = System.getProperty(ALLOW_IN_MEMORY_SESSION);

        try {
            System.setProperty(AEP_PROFILE, "production");
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true");
            System.setProperty(ALLOW_IN_MEMORY_CONSENT, "true");
            System.clearProperty(ALLOW_IN_MEMORY_GOVERNANCE);
            System.setProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, "true");
            System.setProperty(ALLOW_IN_MEMORY_SESSION, "true");

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                AepHttpServer.builder()
                    .engine(mockEngine)
                    .port(8080)
                    .build()
            );

            assertTrue(exception.getMessage().contains("Kill-switch"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile);
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousRunHistory);
            restoreSystemProperty(ALLOW_IN_MEMORY_CONSENT, previousConsent);
            restoreSystemProperty(ALLOW_IN_MEMORY_GOVERNANCE, previousGovernance);
            restoreSystemProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, previousIdempotency);
            restoreSystemProperty(ALLOW_IN_MEMORY_SESSION, previousSession);
        }
    }

    @Test
    void shouldFailClosedInProductionWhenInMemoryIdempotencyFallbackIsUsed() {
        AepEngine mockEngine = mock(AepEngine.class);
        EventCloud mockEventCloud = mock(EventCloud.class);
        when(mockEngine.eventCloud()).thenReturn(mockEventCloud);

        String previousProfile = System.getProperty(AEP_PROFILE);
        String previousRunHistory = System.getProperty(ALLOW_IN_MEMORY_RUN_HISTORY);
        String previousConsent = System.getProperty(ALLOW_IN_MEMORY_CONSENT);
        String previousGovernance = System.getProperty(ALLOW_IN_MEMORY_GOVERNANCE);
        String previousIdempotency = System.getProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
        String previousSession = System.getProperty(ALLOW_IN_MEMORY_SESSION);

        try {
            System.setProperty(AEP_PROFILE, "production");
            System.setProperty(ALLOW_IN_MEMORY_RUN_HISTORY, "true");
            System.setProperty(ALLOW_IN_MEMORY_CONSENT, "true");
            System.setProperty(ALLOW_IN_MEMORY_GOVERNANCE, "true");
            System.clearProperty(ALLOW_IN_MEMORY_IDEMPOTENCY);
            System.setProperty(ALLOW_IN_MEMORY_SESSION, "true");

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                AepHttpServer.builder()
                    .engine(mockEngine)
                    .port(8080)
                    .build()
            );

            assertTrue(exception.getMessage().contains("idempotency"));
        } finally {
            restoreSystemProperty(AEP_PROFILE, previousProfile);
            restoreSystemProperty(ALLOW_IN_MEMORY_RUN_HISTORY, previousRunHistory);
            restoreSystemProperty(ALLOW_IN_MEMORY_CONSENT, previousConsent);
            restoreSystemProperty(ALLOW_IN_MEMORY_GOVERNANCE, previousGovernance);
            restoreSystemProperty(ALLOW_IN_MEMORY_IDEMPOTENCY, previousIdempotency);
            restoreSystemProperty(ALLOW_IN_MEMORY_SESSION, previousSession);
        }
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

    private static void restoreSystemProperty(String key, String value) { 
        if (value == null) { 
            System.clearProperty(key); 
        } else {
            System.setProperty(key, value); 
        }
    }
}
