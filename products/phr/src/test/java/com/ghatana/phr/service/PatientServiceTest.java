package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
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
}
