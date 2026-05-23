package com.ghatana.kernel.interaction;

import com.ghatana.kernel.bridge.port.BridgeContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DataCloudProductInteractionEventProvider.
 *
 * @doc.type class
 * @doc.purpose Test Data Cloud-backed event provider functionality
 * @doc.layer kernel
 */
@DisplayName("DataCloudProductInteractionEventProvider Tests")
class DataCloudProductInteractionEventProviderTest {

    private static final BridgeContext TENANT_1_CONTEXT = BridgeContext.builder()
        .tenantId("tenant-1")
        .workspaceId("workspace-1")
        .principalId("principal-1")
        .correlationId("correlation-1")
        .build();

    @Test
    @DisplayName("Store event successfully")
    void storeEventSuccessfully() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        ProductInteractionEventEnvelope<String> envelope = createTestEnvelope();
        
        doNothing().when(mockClient).storeEvent(
            anyString(), anyString(), anyString(), anyString(), anyMap()
        );
        
        boolean result = provider.store(envelope, ProductInteractionStatus.ALLOWED);
        
        assertTrue(result);
        verify(mockClient).storeEvent(
            eq("tenant-1"), eq("workspace-1"), eq("product-interaction-events"), 
            eq("event-1"), anyMap()
        );
    }

    @Test
    @DisplayName("Get event returns envelope when found")
    void getEventReturnsEnvelopeWhenFound() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        Map<String, Object> eventRecord = createTestEventRecord();
        when(mockClient.getEvent("tenant-1", "workspace-1", "product-interaction-events", "event-1"))
            .thenReturn(eventRecord);
        
        Optional<ProductInteractionEventEnvelope<?>> result = provider.get(TENANT_1_CONTEXT, "event-1");
        
        assertTrue(result.isPresent());
        assertEquals("event-1", result.get().eventId());
    }

    @Test
    @DisplayName("Get event returns empty when not found")
    void getEventReturnsEmptyWhenNotFound() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        when(mockClient.getEvent("tenant-1", "workspace-1", "product-interaction-events", "event-1"))
            .thenReturn(null);
        
        Optional<ProductInteractionEventEnvelope<?>> result = provider.get(TENANT_1_CONTEXT, "event-1");
        
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Update status successfully")
    void updateStatusSuccessfully() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        doNothing().when(mockClient).updateEventStatus(
            "tenant-1", "workspace-1", "product-interaction-events", "event-1", "SUCCEEDED");
        
        boolean result = provider.updateStatus(TENANT_1_CONTEXT, "event-1", ProductInteractionStatus.SUCCEEDED);
        
        assertTrue(result);
        verify(mockClient).updateEventStatus(
            "tenant-1", "workspace-1", "product-interaction-events", "event-1", "SUCCEEDED");
    }

    @Test
    @DisplayName("Is delivered returns true for succeeded events")
    void isDeliveredReturnsTrueForSucceededEvents() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        Map<String, Object> eventRecord = createTestEventRecord();
        eventRecord.put("status", "SUCCEEDED");
        when(mockClient.getEvent("tenant-1", "workspace-1", "product-interaction-events", "event-1"))
            .thenReturn(eventRecord);
        
        boolean result = provider.isDelivered(TENANT_1_CONTEXT, "event-1");
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Is delivered returns false for non-succeeded events")
    void isDeliveredReturnsFalseForNonSucceededEvents() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        Map<String, Object> eventRecord = createTestEventRecord();
        eventRecord.put("status", "BLOCKED");
        when(mockClient.getEvent("tenant-1", "workspace-1", "product-interaction-events", "event-1"))
            .thenReturn(eventRecord);
        
        boolean result = provider.isDelivered(TENANT_1_CONTEXT, "event-1");
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Send to DLQ successfully")
    void sendToDlqSuccessfully() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        ProductInteractionEventEnvelope<String> envelope = createTestEnvelope();
        
        doNothing().when(mockClient).storeEvent(
            anyString(), anyString(), anyString(), anyString(), anyMap()
        );
        
        boolean result = provider.sendToDlq(envelope, "policy_denied");
        
        assertTrue(result);
        verify(mockClient).storeEvent(
            eq("tenant-1"), eq("workspace-1"), eq("product-interaction-dlq"), 
            eq("event-1"), anyMap()
        );
    }

    @Test
    @DisplayName("Get DLQ events returns list")
    void getDlqEventsReturnsList() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        List<Map<String, Object>> dlqRecords = List.of(createTestEventRecord());
        when(mockClient.queryDlqEvents("tenant-1", "workspace-1", "product-interaction-dlq", "test-topic", 10))
            .thenReturn(dlqRecords);
        
        List<ProductInteractionEventEnvelope<?>> result = provider.getDlqEvents(TENANT_1_CONTEXT, "test-topic", 10);
        
        assertEquals(1, result.size());
        assertEquals("event-1", result.get(0).eventId());
    }

    @Test
    @DisplayName("Get events for replay returns list")
    void getEventsForReplayReturnsList() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        List<Map<String, Object>> eventRecords = List.of(createTestEventRecord());
        when(mockClient.queryEventsForReplay(
            "tenant-1", "workspace-1", "product-interaction-events", "test-topic", 0L, 1000L, 10
        )).thenReturn(eventRecords);
        
        List<ProductInteractionEventEnvelope<?>> result = provider.getEventsForReplay(
            TENANT_1_CONTEXT, "test-topic", 0L, 1000L, 10
        );
        
        assertEquals(1, result.size());
        assertEquals("event-1", result.get(0).eventId());
    }

    @Test
    @DisplayName("Delete events before returns count")
    void deleteEventsBeforeReturnsCount() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        when(mockClient.deleteEventsBefore("tenant-1", "workspace-1", "product-interaction-events", 1000L))
            .thenReturn(5L);
        
        long result = provider.deleteEventsBefore(TENANT_1_CONTEXT, 1000L);
        
        assertEquals(5L, result);
    }

    @Test
    @DisplayName("Null envelope throws exception on store")
    void nullEnvelopeThrowsExceptionOnStore() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        assertThrows(NullPointerException.class, () -> {
            provider.store(null, ProductInteractionStatus.ALLOWED);
        });
    }

    @Test
    @DisplayName("Null status throws exception on store")
    void nullStatusThrowsExceptionOnStore() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        assertThrows(NullPointerException.class, () -> {
            provider.store(createTestEnvelope(), null);
        });
    }

    @Test
    @DisplayName("Negative limit throws exception on get DLQ events")
    void negativeLimitThrowsExceptionOnGetDlqEvents() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        assertThrows(IllegalArgumentException.class, () -> {
            provider.getDlqEvents(TENANT_1_CONTEXT, "test-topic", -1);
        });
    }

    @Test
    @DisplayName("Invalid timestamp range throws exception on get events for replay")
    void invalidTimestampRangeThrowsException() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient = 
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        
        DataCloudProductInteractionEventProvider provider = 
            new DataCloudProductInteractionEventProvider(mockClient);
        
        assertThrows(IllegalArgumentException.class, () -> {
            provider.getEventsForReplay(TENANT_1_CONTEXT, "test-topic", 1000L, 500L, 10);
        });
    }

    @Test
    @DisplayName("Cross-tenant get returns empty even when client returns a record")
    void crossTenantGetReturnsEmpty() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient =
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        DataCloudProductInteractionEventProvider provider =
            new DataCloudProductInteractionEventProvider(mockClient);
        BridgeContext tenant2Context = BridgeContext.builder()
            .tenantId("tenant-2")
            .workspaceId("workspace-1")
            .principalId("principal-2")
            .correlationId("correlation-2")
            .build();

        when(mockClient.getEvent("tenant-2", "workspace-1", "product-interaction-events", "event-1"))
            .thenReturn(createTestEventRecord());

        Optional<ProductInteractionEventEnvelope<?>> result = provider.get(tenant2Context, "event-1");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Replay and DLQ queries filter out records outside scope")
    void replayAndDlqQueriesFilterOutRecordsOutsideScope() {
        DataCloudProductInteractionEventProvider.DataCloudEventClient mockClient =
            mock(DataCloudProductInteractionEventProvider.DataCloudEventClient.class);
        DataCloudProductInteractionEventProvider provider =
            new DataCloudProductInteractionEventProvider(mockClient);
        Map<String, Object> otherTenantRecord = createTestEventRecord();
        otherTenantRecord.put("tenantId", "tenant-2");

        when(mockClient.queryDlqEvents("tenant-1", "workspace-1", "product-interaction-dlq", "test-topic", 10))
            .thenReturn(List.of(createTestEventRecord(), otherTenantRecord));
        when(mockClient.queryEventsForReplay(
            "tenant-1", "workspace-1", "product-interaction-events", "test-topic", 0L, 1000L, 10))
            .thenReturn(List.of(createTestEventRecord(), otherTenantRecord));

        assertEquals(1, provider.getDlqEvents(TENANT_1_CONTEXT, "test-topic", 10).size());
        assertEquals(1, provider.getEventsForReplay(TENANT_1_CONTEXT, "test-topic", 0L, 1000L, 10).size());
    }

    @Test
    @DisplayName("Null client throws exception on construction")
    void nullClientThrowsExceptionOnConstruction() {
        assertThrows(NullPointerException.class, () -> {
            new DataCloudProductInteractionEventProvider(null);
        });
    }

    private ProductInteractionEventEnvelope<String> createTestEnvelope() {
        return new ProductInteractionEventEnvelope<>(
            "1.0.0",
            "event-1",
            "contract-1",
            "1.0.0",
            "provider-1",
            List.of("consumer-1"),
            "product-unit-1",
            "tenant-1",
            "workspace-1",
            "run-1",
            "correlation-1",
            Instant.now(),
            Map.of("key", "value"),
            "test-topic",
            "payload"
        );
    }

    private Map<String, Object> createTestEventRecord() {
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("eventId", "event-1");
        record.put("schemaVersion", "1.0.0");
        record.put("contractId", "contract-1");
        record.put("contractVersion", "1.0.0");
        record.put("providerProductId", "provider-1");
        record.put("consumerProductIds", List.of("consumer-1"));
        record.put("productUnitId", "product-unit-1");
        record.put("tenantId", "tenant-1");
        record.put("workspaceId", "workspace-1");
        record.put("runId", "run-1");
        record.put("correlationId", "correlation-1");
        record.put("topic", "test-topic");
        record.put("publishedAt", Instant.now().toString());
        record.put("policyContext", Map.of("key", "value"));
        record.put("payload", "payload");
        record.put("status", "ALLOWED");
        return record;
    }
}
