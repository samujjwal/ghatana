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
    void ignoresDuplicateEventIds() { // GH-90000
        RecordingPersistence persistence = new RecordingPersistence(); // GH-90000
        DefaultAuditTrailService service = new DefaultAuditTrailService( // GH-90000
            new ObjectMapper().findAndRegisterModules(), // GH-90000
            persistence
        );

        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder() // GH-90000
            .eventId("audit-duplicate-1 [GH-90000]")
            .eventType("patient.read [GH-90000]")
            .entityId("patient-1 [GH-90000]")
            .userId("provider-1 [GH-90000]")
            .tenantId("tenant-1 [GH-90000]")
            .action("read [GH-90000]")
            .data(Map.of("source", "test")) // GH-90000
            .build(); // GH-90000

        service.recordAuditEvent(event); // GH-90000
        service.recordAuditEvent(event); // GH-90000

        List<AuditTrailService.AuditEvent> events = service.queryAuditEvents( // GH-90000
            AuditTrailService.AuditQuery.builder().entityId("patient-1 [GH-90000]").limit(10).build()
        );

        assertEquals(1, events.size()); // GH-90000
        assertEquals(1, persistence.loadAll().size()); // GH-90000
    }

    private static final class RecordingPersistence implements AuditTrailPersistence {
        private final List<DefaultAuditTrailService.StoredAuditEvent> events = new ArrayList<>(); // GH-90000

        @Override
        public void persist(DefaultAuditTrailService.StoredAuditEvent event) { // GH-90000
            events.add(event); // GH-90000
        }

        @Override
        public List<DefaultAuditTrailService.StoredAuditEvent> loadAll() { // GH-90000
            return List.copyOf(events); // GH-90000
        }
    }
}
