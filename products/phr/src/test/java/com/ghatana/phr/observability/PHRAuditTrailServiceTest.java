package com.ghatana.phr.observability;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataDeleteRequest;
import com.ghatana.kernel.adapter.datacloud.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.DataStream;
import com.ghatana.kernel.adapter.datacloud.DataStreamRequest;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.QueryResult;
import com.ghatana.kernel.adapter.datacloud.SchemaCreateRequest;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.adapter.datacloud.TransactionHandle;
import com.ghatana.kernel.observability.AuditTrailService;
import io.activej.promise.Promise;
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

    @Test
    void testPersistsAcrossServiceRestart() {
        InMemoryAuditAdapter adapter = new InMemoryAuditAdapter();
        AuditTrailService writer = new PHRAuditTrailServiceImpl(adapter);

        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("evt-persist-001")
            .eventType("patient.audit.persisted")
            .entityId("patient-persisted")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("write")
            .data(Map.of("source", "restart-test"))
            .build();

        writer.recordAuditEvent(event);

        AuditTrailService reader = new PHRAuditTrailServiceImpl(adapter);
        List<AuditTrailService.AuditEvent> events = reader.queryAuditEvents(
            AuditTrailService.AuditQuery.builder().entityId("patient-persisted").build()
        );

        assertEquals(1, events.size());
        assertEquals("evt-persist-001", events.getFirst().getEventId());
    }

    @Test
    void testDuplicateEventIdRecordedOnce() {
        AuditTrailService.AuditEvent event = AuditTrailService.AuditEvent.builder()
            .eventId("evt-duplicate-001")
            .eventType("patient.records.accessed")
            .entityId("patient-duplicate")
            .userId("provider-1")
            .tenantId("tenant-1")
            .action("read")
            .data(Map.of("record_count", 1))
            .build();

        auditTrailService.recordAuditEvent(event);
        auditTrailService.recordAuditEvent(event);

        List<AuditTrailService.AuditEvent> events = auditTrailService.queryAuditEvents(
            AuditTrailService.AuditQuery.builder()
                .entityId("patient-duplicate")
                .limit(10)
                .build()
        );

        assertEquals(1, events.size());
        assertEquals("evt-duplicate-001", events.getFirst().getEventId());
    }

    private static final class InMemoryAuditAdapter implements DataCloudKernelAdapter {
        @Override
        public Promise<DataResult> readData(DataReadRequest request) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> writeData(DataWriteRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> deleteData(DataDeleteRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<QueryResult> queryData(DataQueryRequest request) {
            return Promise.of(new QueryResult(List.of(), 0, false));
        }

        @Override
        public Promise<Void> createSchema(SchemaCreateRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<SchemaInfo> getSchema(String datasetId) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<DatasetInfo>> listDatasets() {
            return Promise.of(List.of());
        }

        @Override
        public Promise<TransactionHandle> beginTransaction() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> commitTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> rollbackTransaction(TransactionHandle handle) {
            return Promise.complete();
        }

        @Override
        public Promise<DataStream> openStream(DataStreamRequest request) {
            return Promise.of(null);
        }
    }
}
