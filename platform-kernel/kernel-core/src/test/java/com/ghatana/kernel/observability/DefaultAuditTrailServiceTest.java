package com.ghatana.kernel.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Verifies idempotent event recording in DefaultAuditTrailService
 * @doc.layer core
 * @doc.pattern Test
 */
class DefaultAuditTrailServiceTest {

    @Test
    void ignoresDuplicateEventIds() {
        RecordingPersistence persistence = new RecordingPersistence();
        DefaultAuditTrailService service = new DefaultAuditTrailService(
            new ObjectMapper().findAndRegisterModules(),
            persistence
        );

        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("audit-duplicate-1")
            .eventType("patient.read")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("source", "test"))
            .build();

        service.recordAuditEvent(event);
        service.recordAuditEvent(event);

        List<AuditTrailService.AuditEvent> events = service.queryAuditEvents(
            AuditTrailService.AuditQuery.builder().entityId("patient-1").limit(10).build()
        );

        assertEquals(1, events.size());
        assertEquals(1, persistence.loadAll().size());
    }

    private static final class RecordingPersistence implements AuditTrailPersistence {
        private final List<DefaultAuditTrailService.StoredAuditEvent> events = new ArrayList<>();

        @Override
        public void persist(DefaultAuditTrailService.StoredAuditEvent event) {
            events.add(event);
        }

        @Override
        public List<DefaultAuditTrailService.StoredAuditEvent> loadAll() {
            return List.copyOf(events);
        }
    }
}
