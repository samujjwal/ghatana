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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PatientRegistrationService}.
 *
 * <p>Covers registration happy path, NHS ID uniqueness enforcement,
 * optional-field enrichment, audit event emission, and lookup by ID.</p>
 *
 * @doc.type test
 * @doc.purpose Patient registration service — all branches
 * @doc.layer test
 * @doc.pattern Unit Test
 * @since 1.0.0
 */
@DisplayName("PatientRegistrationService")
class PatientRegistrationServiceTest extends EventloopTestBase {

    private PatientRegistrationService service;
    private StubPatientStore patientStore;
    private RecordingAuditService auditService;

    private static final String TENANT_ID = "tenant-hospital-1";

    @BeforeEach
    void setUp() {
        patientStore = new StubPatientStore();
        auditService = new RecordingAuditService();
        service = new PatientRegistrationService(
            patientStore,
            Executors.newSingleThreadExecutor(),
            new SimpleMeterRegistry(),
            auditService
        );
    }

    // ── Registration happy path ──────────────────────────────────────────────

    @Nested
    @DisplayName("Successful registration")
    class SuccessfulRegistration {

        @Test
        @DisplayName("Registers patient with all required fields")
        void registersPatientWithRequiredFields() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, "NHS-12345", "Ram", "Sharma",
                    LocalDate.of(1990, 5, 15), "male", "1",
                    "+977-9841234567", "ram@example.com", "doctor-1"
                );

            Patient result = runPromise(() -> service.register(req));

            assertThat(result.patientId()).isNotNull();
            assertThat(result.tenantId()).isEqualTo(TENANT_ID);
            assertThat(result.nhsId()).isEqualTo("NHS-12345");
            assertThat(result.firstName()).isEqualTo("Ram");
            assertThat(result.lastName()).isEqualTo("Sharma");
            assertThat(result.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 15));
            assertThat(result.gender()).isEqualTo("male");
            assertThat(result.province()).isEqualTo("1");
            assertThat(result.primaryPhone()).isEqualTo("+977-9841234567");
            assertThat(result.primaryEmail()).isEqualTo("ram@example.com");
            assertThat(result.active()).isTrue();
            assertThat(result.classification()).isEqualTo(DataClassification.C2);
        }

        @Test
        @DisplayName("Patient is persisted in the store")
        void patientIsPersistedInStore() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, null, "Sita", "Thapa",
                    LocalDate.of(1985, 3, 10), "female", null,
                    null, null, "nurse-1"
                );

            Patient result = runPromise(() -> service.register(req));

            assertThat(patientStore.savedPatients).hasSize(1);
            assertThat(patientStore.savedPatients.get(0).patientId()).isEqualTo(result.patientId());
        }

        @Test
        @DisplayName("Registration without NHS ID skips uniqueness check")
        void registrationWithoutNhsIdSkipsUniquenessCheck() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, null, "Hari", "Bahadur",
                    LocalDate.of(2000, 1, 1), "male", "3",
                    null, null, "admin-1"
                );

            Patient result = runPromise(() -> service.register(req));

            assertThat(result.nhsId()).isNull();
            assertThat(result.firstName()).isEqualTo("Hari");
        }

        @Test
        @DisplayName("Registration with blank NHS ID skips uniqueness check")
        void registrationWithBlankNhsIdSkipsUniquenessCheck() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, "  ", "Gita", "KC",
                    LocalDate.of(1995, 7, 20), "female", "5",
                    null, null, "doctor-2"
                );

            Patient result = runPromise(() -> service.register(req));

            assertThat(result.firstName()).isEqualTo("Gita");
        }
    }

    // ── NHS ID uniqueness ────────────────────────────────────────────────────

    @Nested
    @DisplayName("NHS ID uniqueness enforcement")
    class NhsIdUniqueness {

        @Test
        @DisplayName("Throws IllegalStateException when NHS ID already exists in tenant")
        void throwsWhenNhsIdAlreadyExists() {
            // Pre-register a patient with the same NHS ID
            Patient existing = Patient.newPatient(
                TENANT_ID, "NHS-DUPLICATE", "Existing", "Patient",
                LocalDate.of(1980, 1, 1), "male", "system"
            );
            patientStore.existingByNhsId = Optional.of(existing);

            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, "NHS-DUPLICATE", "New", "Patient",
                    LocalDate.of(1990, 6, 15), "female", "2",
                    null, null, "doctor-1"
                );

            assertThatThrownBy(() -> runPromise(() -> service.register(req)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NHS ID already registered");
        }
    }

    // ── Audit event emission ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit logging (ISSUE P13)")
    class AuditLogging {

        @Test
        @DisplayName("Emits PATIENT_REGISTERED audit event with nhsId=present")
        void emitsAuditEventWithNhsId() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, "NHS-AUDIT-1", "Audit", "Test",
                    LocalDate.of(1988, 12, 1), "male", "4",
                    null, null, "doctor-audit"
                );

            Patient result = runPromise(() -> service.register(req));

            assertThat(auditService.events).hasSize(1);
            AuditEvent event = auditService.events.get(0);
            assertThat(event.tenantId()).isEqualTo(TENANT_ID);
            assertThat(event.eventType()).isEqualTo("PATIENT_REGISTERED");
            assertThat(event.principal()).isEqualTo("doctor-audit");
            assertThat(event.resourceType()).isEqualTo("Patient");
            assertThat(event.resourceId()).isEqualTo(result.patientId().toString());
            assertThat(event.success()).isTrue();
            assertThat(event.getDetail("nhsId")).isEqualTo("present");
        }

        @Test
        @DisplayName("Emits PATIENT_REGISTERED audit event with nhsId=absent when no NHS ID")
        void emitsAuditEventWithoutNhsId() {
            PatientRegistrationService.RegistrationRequest req =
                new PatientRegistrationService.RegistrationRequest(
                    TENANT_ID, null, "No", "NhsId",
                    LocalDate.of(1992, 4, 20), "female", "6",
                    null, null, "nurse-2"
                );

            runPromise(() -> service.register(req));

            assertThat(auditService.events).hasSize(1);
            assertThat(auditService.events.get(0).getDetail("nhsId")).isEqualTo("absent");
        }
    }

    // ── Lookup by ID ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById lookup")
    class FindById {

        @Test
        @DisplayName("Returns patient when found in tenant")
        void returnsPatientWhenFound() {
            Patient patient = Patient.newPatient(
                TENANT_ID, "NHS-FIND", "Find", "Me",
                LocalDate.of(1975, 8, 5), "male", "system"
            );
            patientStore.existingById = Optional.of(patient);

            Optional<Patient> result = runPromise(
                () -> service.findById(TENANT_ID, patient.patientId()));

            assertThat(result).isPresent();
            assertThat(result.get().patientId()).isEqualTo(patient.patientId());
        }

        @Test
        @DisplayName("Returns empty when patient not found")
        void returnsEmptyWhenNotFound() {
            Optional<Patient> result = runPromise(
                () -> service.findById(TENANT_ID, UUID.randomUUID()));

            assertThat(result).isEmpty();
        }
    }

    // ── Stubs ────────────────────────────────────────────────────────────────

    private static class StubPatientStore implements PatientStore {
        final List<Patient> savedPatients = new ArrayList<>();
        Optional<Patient> existingByNhsId = Optional.empty();
        Optional<Patient> existingById = Optional.empty();

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
        public void deactivate(String tenantId, UUID patientId) { }

        @Override
        public void hardDelete(String tenantId, UUID patientId) { }

        @Override
        public Optional<Patient> findByNhsId(String tenantId, String nhsId) {
            return existingByNhsId;
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
