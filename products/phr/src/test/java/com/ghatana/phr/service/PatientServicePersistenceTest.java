package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.kernel.security.TenantSecurityContext;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.observability.PHRAuditTrailServiceImpl;
import com.ghatana.phr.observability.PHRTelemetryManagerImpl;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientServicePersistenceTest {

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        connectionPool = PhrPersistenceTestSupport.createConnectionPool(postgres, "phr-patient-service-test");

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
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void createRecordPersistsAcrossRepositoryInstances() {
        KernelTelemetryManager telemetry = new PHRTelemetryManagerImpl();
        AuditTrailService auditTrail = new PHRAuditTrailServiceImpl();
        PatientRecordRepository repository = new PatientRecordRepository(connectionPool.getDataSource());
        PatientService patientService = new PatientService(telemetry, auditTrail, repository);

        patientService.createRecord("patient-1", Map.of("diagnosis", "Diabetes", "treatment", "Insulin"));

        PatientRecords persisted = new PatientRecordRepository(connectionPool.getDataSource()).findByPatientId("patient-1");
        assertEquals(1, persisted.size());
        assertEquals("provider-1", persisted.getRecords().getFirst().getCreatedBy());
        assertEquals("Diabetes", persisted.getRecords().getFirst().getData().get("diagnosis"));
    }

    @Test
    void createRecordRollsBackPersistentWriteWhenAuditFails() {
        KernelTelemetryManager telemetry = new PHRTelemetryManagerImpl();
        AuditTrailService failingAuditTrail = new AuditTrailService() {
            @Override
            public void recordAuditEvent(AuditEvent event) {
                throw new IllegalStateException("audit sink unavailable");
            }

            @Override
            public java.util.List<AuditEvent> queryAuditEvents(AuditQuery query) {
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
        PatientRecordRepository repository = new PatientRecordRepository(connectionPool.getDataSource());
        PatientService patientService = new PatientService(telemetry, failingAuditTrail, repository);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            patientService.createRecord("patient-1", Map.of("diagnosis", "Diabetes"))
        );

        assertEquals("Failed to create patient record because audit logging failed", exception.getMessage());
        assertEquals(0, new PatientRecordRepository(connectionPool.getDataSource()).findByPatientId("patient-1").size());
    }
}
