package com.ghatana.datacloud.launcher.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EventLogAuditService")
class EventLogAuditServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("record persists audit events to the dedicated audit stream")
    void recordPersistsAuditEvents() { // GH-90000
        EventLogStore eventLogStore = mock(EventLogStore.class); // GH-90000
        when(eventLogStore.append(any(TenantContext.class), any(EventLogStore.EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of(12L))); // GH-90000
        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, new ObjectMapper().findAndRegisterModules()); // GH-90000

        runPromise(() -> auditService.record(AuditEvent.builder() // GH-90000
            .tenantId("tenant-a")
            .eventType("PII_REDACT")
            .principal("svc-user")
            .resourceType("GOVERNANCE")
            .resourceId("users")
            .success(true) // GH-90000
            .timestamp(Instant.parse("2026-04-14T12:00:00Z"))
            .detail("requestId", "req-1") // GH-90000
            .build())); // GH-90000

        ArgumentCaptor<EventLogStore.EventEntry> entryCaptor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class); // GH-90000
        verify(eventLogStore).append(any(TenantContext.class), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("PII_REDACT");
        assertThat(entryCaptor.getValue().headers()).containsEntry("stream", "__audit"); // GH-90000
    }

    @Test
    @DisplayName("summarize aggregates persisted audit events by type")
    void summarizeAggregatesPersistedAuditEvents() throws Exception { // GH-90000
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules(); // GH-90000
        EventLogStore eventLogStore = mock(EventLogStore.class); // GH-90000
        when(eventLogStore.readByTimeRange(any(TenantContext.class), any(Instant.class), any(Instant.class), anyInt())) // GH-90000
            .thenReturn(Promise.of(List.of( // GH-90000
                auditEntry(objectMapper, "AUTH_FAILURE", Instant.parse("2026-04-14T12:00:00Z"), Map.of("requestId", "req-1")),
                auditEntry(objectMapper, "PII_REDACT", Instant.parse("2026-04-14T12:05:00Z")),
                auditEntry(objectMapper, "RETENTION_PURGE", Instant.parse("2026-04-14T12:06:00Z"))
            )));
        EventLogAuditService auditService = new EventLogAuditService(eventLogStore, objectMapper); // GH-90000

        AuditSummaryProvider.AuditSummary summary = runPromise(() -> // GH-90000
            auditService.summarize("tenant-a", Instant.parse("2026-04-01T00:00:00Z"), 100));

        assertThat(summary.lastAuditAt()).isEqualTo(Instant.parse("2026-04-14T12:06:00Z"));
        assertThat(summary.eventCounts()).containsEntry("AUTH_FAILURE", 1L); // GH-90000
        assertThat(summary.eventCounts()).containsEntry("PII_REDACT", 1L); // GH-90000
        assertThat(summary.eventCounts()).containsEntry("RETENTION_PURGE", 1L); // GH-90000
        assertThat(summary.recentEvents()).hasSize(3); // GH-90000
    }

    private EventLogStore.EventEntry auditEntry(ObjectMapper objectMapper, String eventType, Instant timestamp) throws Exception { // GH-90000
        return auditEntry(objectMapper, eventType, timestamp, Map.of()); // GH-90000
    }

    private EventLogStore.EventEntry auditEntry(ObjectMapper objectMapper, String eventType, Instant timestamp, Map<String, Object> details) throws Exception { // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
            .eventType(eventType) // GH-90000
            .timestamp(timestamp) // GH-90000
            .payload(objectMapper.writeValueAsBytes(Map.of( // GH-90000
                "id", eventType + "-id",
                "tenantId", "tenant-a",
                "eventType", eventType,
                "timestamp", timestamp.toString(), // GH-90000
                "details", details
            )))
            .headers(Map.of("stream", "__audit")) // GH-90000
            .build(); // GH-90000
    }

        }