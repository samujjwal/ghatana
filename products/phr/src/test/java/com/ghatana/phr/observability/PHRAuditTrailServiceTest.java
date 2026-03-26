package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.AuditTrailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PHR Audit Trail Service.
 * Verifies immutable audit logging and hash chain integrity.
 */
class PHRAuditTrailServiceTest {
    private AuditTrailService auditTrailService;

    @BeforeEach
    void setUp() {
        auditTrailService = new PHRAuditTrailServiceImpl();
    }

    @Test
    void testRecordAuditEvent() {
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("evt-001")
            .eventType("patient.records.accessed")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("record_count", 5))
            .build();

        assertDoesNotThrow(() -> auditTrailService.recordAuditEvent(event));
    }

    @Test
    void testQueryAuditEvents() {
        AuditTrailService.AuditEvent event1 = AuditTrailService.AuditEvent.builder()
            .eventId("evt-001")
            .eventType("patient.records.accessed")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("record_count", 5))
            .build();

        AuditTrailService.AuditEvent event2 = AuditTrailService.AuditEvent.builder()
            .eventId("evt-002")
            .eventType("patient.records.created")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("create")
            .data(Map.of("record_id", "rec-001"))
            .build();

        auditTrailService.recordAuditEvent(event1);
        auditTrailService.recordAuditEvent(event2);

        AuditTrailService.AuditQuery query = AuditTrailService.AuditQuery.builder()
            .entityId("patient-1")
            .build();

        List<AuditTrailService.AuditEvent> events = auditTrailService.queryAuditEvents(query);

        assertEquals(2, events.size());
    }

    @Test
    void testGetImmutableTrail() {
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("evt-001")
            .eventType("patient.records.accessed")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("record_count", 5))
            .build();

        auditTrailService.recordAuditEvent(event);

        AuditTrailService.ImmutableAuditTrail trail = 
            auditTrailService.getImmutableTrail("patient-1");

        assertNotNull(trail);
        assertEquals("patient-1", trail.getEntityId());
        assertEquals(1, trail.getEvents().size());
        assertNotNull(trail.getMerkleRoot());
        assertTrue(trail.isIntact());
    }

    @Test
    void testVerifyTrailIntegrity() {
        AuditTrailService.AuditEvent event1 = AuditTrailService.AuditEvent.builder()
            .eventId("evt-001")
            .eventType("patient.records.accessed")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("record_count", 5))
            .build();

        AuditTrailService.AuditEvent event2 = AuditTrailService.AuditEvent.builder()
            .eventId("evt-002")
            .eventType("patient.records.created")
            .entityId("patient-1")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("create")
            .data(Map.of("record_id", "rec-001"))
            .build();

        auditTrailService.recordAuditEvent(event1);
        auditTrailService.recordAuditEvent(event2);

        AuditTrailService.VerificationResult result = 
            auditTrailService.verifyTrailIntegrity("patient-1");

        assertTrue(result.isValid());
        assertEquals("Audit trail intact", result.getMessage());
        assertTrue(result.getViolations().isEmpty());
    }
}
