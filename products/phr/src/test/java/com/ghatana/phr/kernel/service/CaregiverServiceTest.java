package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.CaregiverService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CaregiverService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR caregiver relationship management
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CaregiverService")
class CaregiverServiceTest extends EventloopTestBase {

    private CaregiverService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new CaregiverService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        void serviceName() {
            assertEquals("caregiver", service.getName());
        }
    }

    @Nested
    @DisplayName("createRelationship")
    class CreateRelationshipTests {

        @Test
        @DisplayName("stores an ACTIVE relationship")
        void storesActive() {
            CaregiverRelationship rel = build("caregiver-1", "patient-1");

            CaregiverRelationship stored = runPromise(() -> service.createRelationship(rel));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(RelationshipStatus.ACTIVE);
            assertNotNull(stored.createdAt());
        }

        @Test
        @DisplayName("rejects null caregiverId")
        void rejectsNullCaregiverId() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createRelationship(build(null, "p1"))));
            clearFatalError();
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatientId() {
            assertThrows(Exception.class,
                    () -> runPromise(() -> service.createRelationship(build("cg1", null))));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("revokeRelationship")
    class RevokeRelationshipTests {

        @Test
        @DisplayName("transitions status to REVOKED")
        void revokesRelationship() {
            CaregiverRelationship stored = runPromise(() ->
                    service.createRelationship(build("cg-2", "patient-2")));

            CaregiverRelationship revoked = runPromise(() ->
                    service.revokeRelationship(stored.id()));

            assertThat(revoked.status()).isEqualTo(RelationshipStatus.REVOKED);
        }

        @Test
        @DisplayName("revoking already-revoked throws")
        void cannotRevokeAlreadyRevoked() {
            CaregiverRelationship stored = runPromise(() ->
                    service.createRelationship(build("cg-3", "patient-3")));
            runPromise(() -> service.revokeRelationship(stored.id()));

            assertThrows(Exception.class,
                    () -> runPromise(() -> service.revokeRelationship(stored.id())));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("getActiveCaregiversForPatient")
    class ActiveCaregiverTests {

        @Test
        @DisplayName("returns only active relationships")
        void returnsActiveOnly() {
            runPromise(() -> service.createRelationship(build("cg-A", "patient-X")));
            CaregiverRelationship toRevoke = runPromise(() ->
                    service.createRelationship(build("cg-B", "patient-X")));
            runPromise(() -> service.revokeRelationship(toRevoke.id()));
            runPromise(() -> service.createRelationship(build("cg-C", "other-patient")));

            List<CaregiverRelationship> active =
                    runPromise(() -> service.getActiveCaregiversForPatient("patient-X"));

            assertThat(active).hasSize(1);
            assertThat(active.get(0).caregiverId()).isEqualTo("cg-A");
        }
    }

    @Nested
    @DisplayName("getPatientsForCaregiver")
    class PatientsForCaregiverTests {

        @Test
        @DisplayName("returns all active patient relationships for a caregiver")
        void returnsActivePatients() {
            runPromise(() -> service.createRelationship(build("super-cg", "patient-1")));
            runPromise(() -> service.createRelationship(build("super-cg", "patient-2")));
            runPromise(() -> service.createRelationship(build("other-cg", "patient-3")));

            List<CaregiverRelationship> patients =
                    runPromise(() -> service.getPatientsForCaregiver("super-cg"));

            assertThat(patients).hasSize(2);
            assertThat(patients).allMatch(r -> "super-cg".equals(r.caregiverId()));
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static CaregiverRelationship build(String caregiverId, String patientId) {
        return new CaregiverRelationship(
                null, caregiverId, patientId,
                RelationshipType.PARENT,
                Set.of("medications", "labs"),
                RelationshipStatus.ACTIVE,
                null, null);
    }
}
