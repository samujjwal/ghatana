package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.model.PatientRecord;
import com.ghatana.kernel.security.TenantSecurityContext;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.observability.PHRAuditTrailServiceImpl;
import com.ghatana.phr.observability.PHRTelemetryManagerImpl;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Patient Service.
 * Verifies telemetry and audit trail integration.
 */
class PatientServiceTest {
    private PatientService patientService;
    private PatientRecordRepository recordsRepository;

    @BeforeEach
    void setUp() {
        KernelTelemetryManager telemetry = new PHRTelemetryManagerImpl();
        AuditTrailService auditTrail = new PHRAuditTrailServiceImpl();
        recordsRepository = new PatientRecordRepository();

        patientService = new PatientService(telemetry, auditTrail, recordsRepository);

        TenantSecurityContext context = TenantSecurityContext.builder()
            .tenantId("tenant-1")
            .userId("provider-1")
            .sessionId("session-1")
            .role("HEALTHCARE_PROVIDER")
            .permission("read:patient-records")
            .build();

        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetRecords() {
        PatientRecords records = patientService.getRecords("patient-1");

        assertNotNull(records);
    }

    @Test
    void testCreateRecord() {
        Map<String, Object> recordData = Map.of(
            "diagnosis", "Hypertension",
            "treatment", "Medication prescribed"
        );

        assertDoesNotThrow(() -> patientService.createRecord("patient-1", recordData));

        PatientRecords records = recordsRepository.findByPatientId("patient-1");
        assertEquals(1, records.size());
    }

    @Test
    void testCreateRecord_SanitizesNestedRecordData() {
        patientService.createRecord("patient-1", Map.of(
            "diagnosis", "<script>alert('xss')</script>",
            "nested", Map.of("note", "<b>Urgent</b>")
        ));

        PatientRecords records = recordsRepository.findByPatientId("patient-1");
        PatientRecord stored = records.getRecords().get(0);

        assertThat(stored.getData().get("diagnosis"))
            .isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) stored.getData().get("nested");
        assertThat(nested)
            .containsEntry("note", "&lt;b&gt;Urgent&lt;/b&gt;");
    }

    @Test
    void testCreateRecord_RejectsUnsafePatientId() {
        assertThrows(IllegalArgumentException.class,
            () -> patientService.createRecord("patient 1", Map.of("diagnosis", "Hypertension")));
    }

    @Test
    void testCreateRecord_RollsBackWhenAuditFails() {
        KernelTelemetryManager telemetry = new PHRTelemetryManagerImpl();
        AuditTrailService failingAuditTrail = new AuditTrailService() {
            @Override
            public void recordAuditEvent(AuditTrailEvent event) {
                throw new IllegalStateException("audit storage unavailable");
            }

            @Override
            public java.util.List<AuditTrailEvent> queryAuditEvents(AuditQuery query) {
                return java.util.List.of();
            }

            @Override
            public ImmutableAuditTrail getImmutableTrail(String entityId) {
                throw new UnsupportedOperationException("not used in test");
            }

            @Override
            public VerificationResult verifyTrailIntegrity(String entityId) {
                return new VerificationResult(true, "not used", java.util.List.of());
            }
        };

        patientService = new PatientService(telemetry, failingAuditTrail, recordsRepository);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            patientService.createRecord("patient-1", Map.of("diagnosis", "Hypertension"))
        );

        assertTrue(exception.getMessage().contains("audit logging failed"));
        assertTrue(recordsRepository.findByPatientId("patient-1").isEmpty());
    }
}
