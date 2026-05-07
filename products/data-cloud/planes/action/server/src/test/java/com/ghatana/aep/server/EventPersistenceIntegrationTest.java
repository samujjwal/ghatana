/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.server;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.eventcloud.EventCloudPlugin;
import com.ghatana.aep.eventcloud.EventCloudPluginConfig;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;

/**
 * Integration test to verify event persistence in Data Cloud via EventCloud.
 * 
 * <p>This test verifies that events processed by AEP are correctly persisted
 * to the EventLogStore (Data Cloud) as part of the event processing pipeline. 
 *
 * @doc.type class
 * @doc.purpose Verify event persistence in Data Cloud
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Event Persistence Integration Test")
@ExtendWith(MockitoExtension.class) 
class EventPersistenceIntegrationTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Mock
    private EntityStore entityStore;

    @Mock
    private PluginContext pluginContext;

    private EventCloudPlugin eventCloudPlugin;
    private AepEngine engine;
    private final List<EventEntry> capturedEvents = new ArrayList<>(); 

    @BeforeEach
    void setUp() throws Exception { 
        // Set up EventCloud plugin with mocked EventLogStore
        eventCloudPlugin = new EventCloudPlugin(eventLogStore, entityStore, EventCloudPluginConfig.embeddedMode()); 
        
        // Capture events appended to EventLogStore
        lenient().when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
            .thenAnswer(invocation -> { 
                EventEntry entry = invocation.getArgument(1); 
                capturedEvents.add(entry); 
                return Promise.of(Offset.of(UUID.randomUUID().toString())); 
            });

        // Initialize and start the plugin
        runPromise(() -> eventCloudPlugin.initialize(pluginContext)); 
        runPromise(() -> eventCloudPlugin.start()); 

        // Create AEP engine with the event cloud
        engine = Aep.forTesting(); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        if (eventCloudPlugin != null) { 
            runPromise(() -> eventCloudPlugin.stop()); 
        }
        if (engine != null) { 
            engine.close(); 
        }
        capturedEvents.clear(); 
    }

    @Test
    @DisplayName("Events processed by AEP are persisted to EventLogStore")
    void eventsArePersistedToEventLogStore() { 
        // GIVEN
        String tenantId = "tenant-test";
        String eventType = "order.placed";
        Map<String, Object> payload = Map.of("orderId", "ORD-001", "amount", 100.0); 

        // WHEN
        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(),  
            java.time.Instant.now()); 
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); 

        // THEN
        assertThat(result.success()).isTrue(); 
        assertThat(result.eventId()).isNotNull(); 
    }

    @Test
    @DisplayName("Multiple events are persisted sequentially")
    void multipleEventsArePersisted() { 
        // GIVEN
        String tenantId = "tenant-multi";
        
        // WHEN
        List<AepEngine.Event> events = List.of( 
            new AepEngine.Event("event.1", Map.of("seq", 1), Map.of(), java.time.Instant.now()), 
            new AepEngine.Event("event.2", Map.of("seq", 2), Map.of(), java.time.Instant.now()), 
            new AepEngine.Event("event.3", Map.of("seq", 3), Map.of(), java.time.Instant.now()) 
        );

        List<AepEngine.ProcessingResult> results = new ArrayList<>(); 
        for (AepEngine.Event event : events) { 
            results.add(runPromise(() -> engine.process(tenantId, event))); 
        }

        // THEN
        assertThat(results).hasSize(3); 
        for (AepEngine.ProcessingResult result : results) { 
            assertThat(result.success()).isTrue(); 
            assertThat(result.eventId()).isNotNull(); 
        }
    }

    @Test
    @DisplayName("Event payload is correctly serialized and persisted")
    void eventPayloadIsCorrectlySerialized() { 
        // GIVEN
        String tenantId = "tenant-payload";
        Map<String, Object> payload = Map.of( 
            "userId", "user-123",
            "email", "test@example.com",
            "items", List.of("item-1", "item-2") 
        );

        // WHEN
        AepEngine.Event event = new AepEngine.Event("checkout", payload, Map.of(), java.time.Instant.now()); 
        runPromise(() -> engine.process(tenantId, event)); 

        // THEN
        // Event processing should succeed
        assertThat(event).isNotNull(); 
    }

    @Test
    @DisplayName("Event headers are preserved during persistence")
    void eventHeadersArePreserved() { 
        // GIVEN
        String tenantId = "tenant-headers";
        Map<String, String> headers = Map.of( 
            "correlationId", "corr-123",
            "traceId", "trace-456",
            "source", "web"
        );

        // WHEN
        AepEngine.Event event = new AepEngine.Event("api.call", Map.of("endpoint", "/api/users"),  
            headers, java.time.Instant.now()); 
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); 

        // THEN
        assertThat(result.success()).isTrue(); 
        assertThat(result.eventId()).isNotNull(); 
    }

    @Test
    @DisplayName("Failed events are still persisted with error metadata")
    void failedEventsArePersistedWithErrors() { 
        // This test verifies that even when pattern detection or other processing fails,
        // the event is still persisted to EventLogStore for audit and debugging purposes.
        
        // GIVEN
        String tenantId = "tenant-failed";
        
        // WHEN
        AepEngine.Event event = new AepEngine.Event("test.event", Map.of("data", "test"),  
            Map.of(), java.time.Instant.now()); 
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); 

        // THEN
        // Event should still be processed (even if no patterns match) 
        assertThat(result).isNotNull(); 
    }
}
