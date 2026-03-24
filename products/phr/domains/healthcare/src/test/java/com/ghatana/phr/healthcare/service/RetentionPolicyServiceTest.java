package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RetentionPolicyService}.
 *
 * <p>Covers all 5 erasure decision branches: PATIENT_NOT_FOUND, LEGAL_HOLD,
 * ACTIVE_TREATMENT, RETENTION_PERIOD_NOT_ELAPSED, and EXECUTED. Also verifies
 * audit event emission for each branch (ISSUE P13).</p>
 *
 * @doc.type test
 * @doc.purpose Retention policy + right-to-erasure — all branches with audit verification
 * @doc.layer test
 * @doc.pattern Unit Test
 * @since 1.0.0
 */
@DisplayName("RetentionPolicyService")
class RetentionPolicyServiceTest extends EventloopTestBase {

    private RetentionPolicyService service;
    private StubPatientStore patientStore;
    private RecordingAuditService auditService;

    private static final String TENANT_ID = "tenant-hospital-1";
    private static final UUID PATIENT_ID = UUID.randomUUID();

    /** A patient registered 30 years ago (eligible for erasure). */
    private static final Patient OLD_PATIENT = new Patient(
        PATIENT_ID, TENANT_ID, "NHS-OLD",
        "Old", "Patient", LocalDate.of(1960, 1, 1), "male",
        null, null, null, null, "1",
        DataClassification.C2, "system",
        Instant.now().minus(365 * 30, ChronoUnit.DAYS),  // 30 years ago
        null, true
    );

    /** A patient registered 5 years ago (NOT eligible — retention period not met). */
    private static final Patient RECENT_PATIENT = new Patient(
        PATIENT_ID, TENANT_ID, "NHS-RECENT",
        "Recent", "Patient", LocalDate.of(2000, 6, 15), "female",
        null, null, null, null, "3",
        DataClassification.C2, "system",
        Instant.now().minus(365 * 5, ChronoUnit.DAYS),   // 5 years ago
        null, true
    );

    @BeforeEach
    void setUp() {
        patientStore = new StubPatientStore();
        auditService = new RecordingAuditService();
        service = new RetentionPolicyService(
            patientStore,
            Executors.newSingleThreadExecutor(),
            new SimpleMeterRegistry(),
            auditService
        );
    }

    // ── Erasure blocked — patient not found ──────────────────────────────────

    @Nested
    @DisplayName("Patient not found")
    class PatientNotFound {

        @Test
        @DisplayName("Returns PATIENT_NOT_FOUND when patient does not exist")
        void blockedWhenPatientNotFound() {
            patientStore.existingById = Optional.empty();

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID, noTreatment(), noLegalHold()));

            assertThat(outcome.executed()).isFalse();
            assertThat(outcome.reason()).isEqualTo("PATIENT_NOT_FOUND");
        }

        @Test
        @DisplayName("Audit event emitted for PATIENT_NOT_FOUND")
        void auditEmittedForNotFound() {
            patientStore.existingById = Optional.empty();

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), noLegalHold()));

            assertAuditEvent("ERASURE_BLOCKED", false, "PATIENT_NOT_FOUND");
        }
    }

    // ── Erasure blocked — legal hold ─────────────────────────────────────────

    @Nested
    @DisplayName("Legal hold blocks erasure")
    class LegalHold {

        @Test
        @DisplayName("Returns LEGAL_HOLD when patient is under legal hold")
        void blockedByLegalHold() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID,
                    noTreatment(), activeLegalHold()));

            assertThat(outcome.executed()).isFalse();
            assertThat(outcome.reason()).isEqualTo("LEGAL_HOLD");
        }

        @Test
        @DisplayName("Audit event emitted for LEGAL_HOLD block")
        void auditEmittedForLegalHold() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), activeLegalHold()));

            assertAuditEvent("ERASURE_BLOCKED", false, "LEGAL_HOLD");
        }
    }

    // ── Erasure blocked — active treatment ───────────────────────────────────

    @Nested
    @DisplayName("Active treatment blocks erasure")
    class ActiveTreatment {

        @Test
        @DisplayName("Returns ACTIVE_TREATMENT when patient has active treatment episodes")
        void blockedByActiveTreatment() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID,
                    activeTreatment(), noLegalHold()));

            assertThat(outcome.executed()).isFalse();
            assertThat(outcome.reason()).isEqualTo("ACTIVE_TREATMENT");
        }

        @Test
        @DisplayName("Audit event emitted for ACTIVE_TREATMENT block")
        void auditEmittedForActiveTreatment() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                activeTreatment(), noLegalHold()));

            assertAuditEvent("ERASURE_BLOCKED", false, "ACTIVE_TREATMENT");
        }
    }

    // ── Erasure blocked — retention period not elapsed ───────────────────────

    @Nested
    @DisplayName("Retention period not elapsed")
    class RetentionPeriodNotElapsed {

        @Test
        @DisplayName("Returns RETENTION_PERIOD_NOT_ELAPSED when <25 years since registration")
        void blockedByRetentionPeriod() {
            patientStore.existingById = Optional.of(RECENT_PATIENT);

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID,
                    noTreatment(), noLegalHold()));

            assertThat(outcome.executed()).isFalse();
            assertThat(outcome.reason()).isEqualTo("RETENTION_PERIOD_NOT_ELAPSED");
        }

        @Test
        @DisplayName("Audit event emitted for RETENTION_PERIOD_NOT_ELAPSED block")
        void auditEmittedForRetentionPeriod() {
            patientStore.existingById = Optional.of(RECENT_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), noLegalHold()));

            assertAuditEvent("ERASURE_BLOCKED", false, "RETENTION_PERIOD_NOT_ELAPSED");
        }
    }

    // ── Erasure executed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful erasure execution")
    class ErasureExecuted {

        @Test
        @DisplayName("Returns EXECUTED when all gates pass — 25+ years, no hold, no treatment")
        void erasureExecutedSuccessfully() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID,
                    noTreatment(), noLegalHold()));

            assertThat(outcome.executed()).isTrue();
            assertThat(outcome.reason()).isEqualTo("EXECUTED");
        }

        @Test
        @DisplayName("Patient is deactivated before hard delete")
        void patientDeactivatedBeforeHardDelete() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), noLegalHold()));

            assertThat(patientStore.deactivatedIds).containsExactly(PATIENT_ID);
            assertThat(patientStore.hardDeletedIds).containsExactly(PATIENT_ID);
        }

        @Test
        @DisplayName("Deactivation happens before hard delete (ordering)")
        void deactivationBeforeHardDelete() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), noLegalHold()));

            // StubPatientStore records operations in order
            assertThat(patientStore.operationOrder)
                .containsExactly("deactivate", "hardDelete");
        }

        @Test
        @DisplayName("Audit event emitted for successful EXECUTED erasure")
        void auditEmittedForErasure() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            runPromise(() -> service.requestErasure(TENANT_ID, PATIENT_ID,
                noTreatment(), noLegalHold()));

            assertAuditEvent("ERASURE_EXECUTED", true, "EXECUTED");
        }
    }

    // ── Gate evaluation order ────────────────────────────────────────────────

    @Nested
    @DisplayName("Gate evaluation order — legal hold checked before treatment")
    class GateOrder {

        @Test
        @DisplayName("Legal hold takes precedence over active treatment")
        void legalHoldPrecedesActiveTreatment() {
            patientStore.existingById = Optional.of(OLD_PATIENT);

            RetentionPolicyService.ErasureOutcome outcome = runPromise(
                () -> service.requestErasure(TENANT_ID, PATIENT_ID,
                    activeTreatment(), activeLegalHold()));

            // Legal hold should be checked first
            assertThat(outcome.reason()).isEqualTo("LEGAL_HOLD");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertAuditEvent(String expectedEventType, boolean expectedSuccess,
                                   String expectedReason) {
        assertThat(auditService.events).hasSize(1);
        AuditEvent event = auditService.events.get(0);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.eventType()).isEqualTo(expectedEventType);
        assertThat(event.resourceType()).isEqualTo("Patient");
        assertThat(event.resourceId()).isEqualTo(PATIENT_ID.toString());
        assertThat(event.success()).isEqualTo(expectedSuccess);
        assertThat(event.getDetail("reason")).isEqualTo(expectedReason);
    }

    private static RetentionPolicyService.ActiveTreatmentPort noTreatment() {
        return (tenantId, patientId) -> false;
    }

    private static RetentionPolicyService.ActiveTreatmentPort activeTreatment() {
        return (tenantId, patientId) -> true;
    }

    private static RetentionPolicyService.LegalHoldPort noLegalHold() {
        return (tenantId, patientId) -> false;
    }

    private static RetentionPolicyService.LegalHoldPort activeLegalHold() {
        return (tenantId, patientId) -> true;
    }

    // ── Stubs ────────────────────────────────────────────────────────────────

    private static class StubPatientStore implements PatientStore {
        Optional<Patient> existingById = Optional.empty();
        final List<Patient> savedPatients = new ArrayList<>();
        final List<UUID> deactivatedIds = new ArrayList<>();
        final List<UUID> hardDeletedIds = new ArrayList<>();
        final List<String> operationOrder = new ArrayList<>();

        @Override
        public void save(Patient patient) {
            savedPatients.add(patient);
        }

        @Override
        public Optional<Patient> findById(String tenantId, UUID patientId) {
            return existingById;
        }

        @Override
        public List<Patient> findByTenant(String tenantId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void deactivate(String tenantId, UUID patientId) {
            deactivatedIds.add(patientId);
            operationOrder.add("deactivate");
        }

        @Override
        public void hardDelete(String tenantId, UUID patientId) {
            hardDeletedIds.add(patientId);
            operationOrder.add("hardDelete");
        }

        @Override
        public Optional<Patient> findByNhsId(String tenantId, String nhsId) {
            return Optional.empty();
        }
    }

    private static class RecordingAuditService implements AuditService {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public Promise<Void> record(AuditEvent event) {
            events.add(event);
            return Promise.complete();
        }
    }
}
