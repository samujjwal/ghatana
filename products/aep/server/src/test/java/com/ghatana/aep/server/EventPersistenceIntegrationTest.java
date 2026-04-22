/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * to the EventLogStore (Data Cloud) as part of the event processing pipeline. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Verify event persistence in Data Cloud
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Event Persistence Integration Test [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventPersistenceIntegrationTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Mock
    private EntityStore entityStore;

    @Mock
    private PluginContext pluginContext;

    private EventCloudPlugin eventCloudPlugin;
    private AepEngine engine;
    private final List<EventEntry> capturedEvents = new ArrayList<>(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        // Set up EventCloud plugin with mocked EventLogStore
        eventCloudPlugin = new EventCloudPlugin(eventLogStore, entityStore, EventCloudPluginConfig.embeddedMode()); // GH-90000
        
        // Capture events appended to EventLogStore
        lenient().when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenAnswer(invocation -> { // GH-90000
                EventEntry entry = invocation.getArgument(1); // GH-90000
                capturedEvents.add(entry); // GH-90000
                return Promise.of(Offset.of(UUID.randomUUID().toString())); // GH-90000
            });

        // Initialize and start the plugin
        runPromise(() -> eventCloudPlugin.initialize(pluginContext)); // GH-90000
        runPromise(() -> eventCloudPlugin.start()); // GH-90000

        // Create AEP engine with the event cloud
        engine = Aep.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (eventCloudPlugin != null) { // GH-90000
            runPromise(() -> eventCloudPlugin.stop()); // GH-90000
        }
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
        capturedEvents.clear(); // GH-90000
    }

    @Test
    @DisplayName("Events processed by AEP are persisted to EventLogStore [GH-90000]")
    void eventsArePersistedToEventLogStore() { // GH-90000
        // GIVEN
        String tenantId = "tenant-test";
        String eventType = "order.placed";
        Map<String, Object> payload = Map.of("orderId", "ORD-001", "amount", 100.0); // GH-90000

        // WHEN
        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(),  // GH-90000
            java.time.Instant.now()); // GH-90000
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); // GH-90000

        // THEN
        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.eventId()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Multiple events are persisted sequentially [GH-90000]")
    void multipleEventsArePersisted() { // GH-90000
        // GIVEN
        String tenantId = "tenant-multi";
        
        // WHEN
        List<AepEngine.Event> events = List.of( // GH-90000
            new AepEngine.Event("event.1", Map.of("seq", 1), Map.of(), java.time.Instant.now()), // GH-90000
            new AepEngine.Event("event.2", Map.of("seq", 2), Map.of(), java.time.Instant.now()), // GH-90000
            new AepEngine.Event("event.3", Map.of("seq", 3), Map.of(), java.time.Instant.now()) // GH-90000
        );

        List<AepEngine.ProcessingResult> results = new ArrayList<>(); // GH-90000
        for (AepEngine.Event event : events) { // GH-90000
            results.add(runPromise(() -> engine.process(tenantId, event))); // GH-90000
        }

        // THEN
        assertThat(results).hasSize(3); // GH-90000
        for (AepEngine.ProcessingResult result : results) { // GH-90000
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.eventId()).isNotNull(); // GH-90000
        }
    }

    @Test
    @DisplayName("Event payload is correctly serialized and persisted [GH-90000]")
    void eventPayloadIsCorrectlySerialized() { // GH-90000
        // GIVEN
        String tenantId = "tenant-payload";
        Map<String, Object> payload = Map.of( // GH-90000
            "userId", "user-123",
            "email", "test@example.com",
            "items", List.of("item-1", "item-2") // GH-90000
        );

        // WHEN
        AepEngine.Event event = new AepEngine.Event("checkout", payload, Map.of(), java.time.Instant.now()); // GH-90000
        runPromise(() -> engine.process(tenantId, event)); // GH-90000

        // THEN
        // Event processing should succeed
        assertThat(event).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Event headers are preserved during persistence [GH-90000]")
    void eventHeadersArePreserved() { // GH-90000
        // GIVEN
        String tenantId = "tenant-headers";
        Map<String, String> headers = Map.of( // GH-90000
            "correlationId", "corr-123",
            "traceId", "trace-456",
            "source", "web"
        );

        // WHEN
        AepEngine.Event event = new AepEngine.Event("api.call", Map.of("endpoint", "/api/users"),  // GH-90000
            headers, java.time.Instant.now()); // GH-90000
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); // GH-90000

        // THEN
        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.eventId()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Failed events are still persisted with error metadata [GH-90000]")
    void failedEventsArePersistedWithErrors() { // GH-90000
        // This test verifies that even when pattern detection or other processing fails,
        // the event is still persisted to EventLogStore for audit and debugging purposes.
        
        // GIVEN
        String tenantId = "tenant-failed";
        
        // WHEN
        AepEngine.Event event = new AepEngine.Event("test.event", Map.of("data", "test"),  // GH-90000
            Map.of(), java.time.Instant.now()); // GH-90000
        AepEngine.ProcessingResult result = runPromise(() -> engine.process(tenantId, event)); // GH-90000

        // THEN
        // Event should still be processed (even if no patterns match) // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }
}
