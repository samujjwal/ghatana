package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.*;
import com.ghatana.phr.healthcare.port.ConsentStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsentEnforcementService unit tests.
 *
 * <p>Tests the consent decision layers for C3/C4 classified healthcare data
 * against the scenarios in the PHR consent service interface specification.</p>
 *
 * @doc.type test
 * @doc.purpose Unit tests for the consent enforcement service
 * @doc.layer test
 * @doc.pattern Unit Test
 * @since 1.0.0
 */
@DisplayName("ConsentEnforcementService")
class ConsentEnforcementServiceTest {

    private ConsentEnforcementService service;
    private StubConsentStore consentStore;

    private static final String TENANT_ID = "tenant-hospital-1";
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final String PROVIDER_ID = "provider-dr-sharma";

    @BeforeEach
    void setUp() {
        consentStore = new StubConsentStore();
        service = new ConsentEnforcementService(
            consentStore,
            Executors.newSingleThreadExecutor(),
            new SimpleMeterRegistry()
        );
    }

    // ── Emergency access ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Emergency access override")
    class EmergencyAccessTests {

        @Test
        @DisplayName("EMERGENCY_READ with override flag is always allowed regardless of consent")
        void emergencyReadIsAlwaysAllowed() {
            // No consent in store
            consentStore.activeConsent = Optional.empty();

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-1", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.EMERGENCY_READ, DataClassification.C4,
                "EMERGENCY", true, "Trauma — patient unconscious"
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.EMERGENCY_GRANT);
            assertThat(decision.auditRequired()).isTrue(); // always true for emergency
        }
    }

    // ── Self-access ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Patient self-access")
    class SelfAccessTests {

        @Test
        @DisplayName("Patient can read their own C2 data without an explicit consent grant")
        void patientCanReadOwnC2Data() {
            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-2", TENANT_ID, PATIENT_ID,
                PATIENT_ID.toString(),        // actor == patient
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PATIENT,
                ConsentAction.PATIENT_READ, DataClassification.C2,
                "SELF_SERVICE", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.SELF_ACCESS);
        }

        @Test
        @DisplayName("Patient self-access is NOT bypassed for C4 restricted data")
        void patientSelfAccessBlockedForC4() {
            // C4 requires explicit consent even for self-access (write)
            consentStore.activeConsent = Optional.empty();

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-3", TENANT_ID, PATIENT_ID,
                PATIENT_ID.toString(),
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PATIENT,
                ConsentAction.DOCUMENT_WRITE, DataClassification.C4,
                "SELF_SERVICE", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            // C4 self-write is a write action, not in the self-access allowlist → lookup required
            assertThat(decision.allowed()).isFalse();
        }
    }

    // ── Classification gate ──────────────────────────────────────────────────

    @Nested
    @DisplayName("C1/C2 classification gate (role-based, no consent lookup)")
    class ClassificationGateTests {

        @Test
        @DisplayName("C2 data is accessible with only role-based access (no consent needed)")
        void c2DataAccessibleWithRoleOnly() {
            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-4", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.PATIENT_READ, DataClassification.C2,
                "CARE_DELIVERY", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.ROLE_ALLOWED);
            assertThat(consentStore.lookupCalled).isFalse(); // no consent store lookup
        }
    }

    // ── Explicit consent gate (C3/C4) ────────────────────────────────────────

    @Nested
    @DisplayName("C3/C4 explicit consent gate")
    class ExplicitConsentTests {

        @Test
        @DisplayName("C3 data is allowed when an active consent grant exists")
        void c3AllowedWithActiveConsent() {
            ConsentRecord activeConsent = ConsentRecord.newGrant(
                TENANT_ID, PATIENT_ID,
                PATIENT_ID.toString(), "PATIENT",
                PROVIDER_ID, "PROVIDER",
                List.of(ConsentAction.DOCUMENT_READ),
                DataClassification.C3, "CARE_DELIVERY",
                Instant.now().plusSeconds(3600),
                "system"
            );
            consentStore.activeConsent = Optional.of(activeConsent);

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-5", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.DOCUMENT_READ, DataClassification.C3,
                "CARE_DELIVERY", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.EXPLICIT_GRANT);
            assertThat(decision.grantId()).isEqualTo(activeConsent.consentId().toString());
        }

        @Test
        @DisplayName("C3 data is denied when no consent grant exists")
        void c3DeniedWhenNoConsentExists() {
            consentStore.activeConsent = Optional.empty();

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-6", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.DOCUMENT_READ, DataClassification.C3,
                "CARE_DELIVERY", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.OUT_OF_SCOPE);
        }

        @Test
        @DisplayName("C4 data is denied when consent is revoked")
        void c4DeniedWhenConsentRevoked() {
            ConsentRecord revokedConsent = ConsentRecord.newGrant(
                TENANT_ID, PATIENT_ID,
                PATIENT_ID.toString(), "PATIENT",
                PROVIDER_ID, "PROVIDER",
                List.of(ConsentAction.MEDICATION_READ),
                DataClassification.C4, "CARE_DELIVERY",
                null, "system"
            ).revoked("Patient withdrew consent", Instant.now().minusSeconds(60));

            consentStore.activeConsent = Optional.of(revokedConsent);

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-7", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.MEDICATION_READ, DataClassification.C4,
                "CARE_DELIVERY", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reasonCode())
                .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.GRANT_REVOKED);
        }

        @Test
        @DisplayName("C4 audit required flag is set in access decision")
        void c4AuditRequiredFlagSet() {
            ConsentRecord activeConsent = ConsentRecord.newGrant(
                TENANT_ID, PATIENT_ID,
                PATIENT_ID.toString(), "PATIENT",
                PROVIDER_ID, "PROVIDER",
                List.of(ConsentAction.MEDICATION_READ),
                DataClassification.C4, "CARE_DELIVERY",
                Instant.now().plusSeconds(3600), "system"
            );
            consentStore.activeConsent = Optional.of(activeConsent);

            ConsentEnforcementService.ConsentCheckRequest req = new ConsentEnforcementService.ConsentCheckRequest(
                "req-8", TENANT_ID, PATIENT_ID, PROVIDER_ID,
                ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
                ConsentAction.MEDICATION_READ, DataClassification.C4,
                "CARE_DELIVERY", false, null
            );

            ConsentEnforcementService.AccessDecision decision = service.checkAccess(req).getResult();

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.auditRequired()).isTrue();  // C4 always requires tamper-evident audit
        }
    }

    // ── Stub ─────────────────────────────────────────────────────────────────

    static class StubConsentStore implements ConsentStore {
        Optional<ConsentRecord> activeConsent = Optional.empty();
        boolean lookupCalled = false;

        @Override
        public void append(ConsentRecord record) {}

        @Override
        public Optional<ConsentRecord> findActiveConsent(
                String tenantId, UUID patientId, String granteeId, ConsentAction action) {
            lookupCalled = true;
            return activeConsent;
        }

        @Override
        public List<ConsentRecord> findAllForPatient(String tenantId, UUID patientId) {
            return List.of();
        }

        @Override
        public List<ConsentRecord> findAllForGrantee(String tenantId, String granteeId) {
            return List.of();
        }
    }
}
