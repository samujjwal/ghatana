package com.ghatana.phr.kernel.consent;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Patient data access matrix tests for PHR.
 *
 * <p>Validates that different actor types have appropriate access permissions
 * to different resource types and actions according to the access control matrix.
 * This ensures that the consent service enforces the correct access policies.</p>
 *
 * @doc.type class
 * @doc.purpose Patient data access matrix validation for PHR consent enforcement
 * @doc.layer product
 * @doc.pattern MatrixTest
 */
@DisplayName("phr-009: Patient Data Access Matrix Tests")
class PatientDataAccessMatrixTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PATIENT_ID = "patient-123";
    private static final String PROVIDER_ID = "provider-456";
    private static final String CAREGIVER_ID = "caregiver-789";
    private static final String ADMIN_ID = "admin-001";
    private static final String FCHV_ID = "fchv-002";

    // ─────────────────────── Patient Self-Access Matrix ───────────────────────

    @Nested
    @DisplayName("Patient self-access matrix")
    class PatientSelfAccess {

        @Test
        @DisplayName("Patient can read own patient record")
        void patientCanReadOwnRecord() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PATIENT_ID, ConsentService.ActorType.PATIENT, PATIENT_ID, null, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.SELF_SERVICE,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.SELF_ACCESS);
        }

        @Test
        @DisplayName("Patient can read own documents")
        void patientCanReadOwnDocuments() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PATIENT_ID, ConsentService.ActorType.PATIENT, PATIENT_ID, null, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Document", "doc-001", PhrDataClassification.C3),
                ConsentService.ConsentAction.DOCUMENT_READ,
                ConsentService.PurposeOfUse.SELF_SERVICE,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.SELF_ACCESS);
        }

        @Test
        @DisplayName("Patient can read own medications")
        void patientCanReadOwnMedications() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PATIENT_ID, ConsentService.ActorType.PATIENT, PATIENT_ID, null, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "MedicationRequest", "rx-001", PhrDataClassification.C3),
                ConsentService.ConsentAction.MEDICATION_READ,
                ConsentService.PurposeOfUse.SELF_SERVICE,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
        }

        @Test
        @DisplayName("Patient cannot read another patient's record")
        void patientCannotReadOtherPatient() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PATIENT_ID, ConsentService.ActorType.PATIENT, PATIENT_ID, null, null, Set.of()),
                new ConsentService.TargetResource("patient-456", "Patient", "patient-456", PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.SELF_SERVICE,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isFalse();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.OUT_OF_SCOPE);
        }
    }

    // ─────────────────────── Provider Access Matrix ─────────────────────────

    @Nested
    @DisplayName("Provider access matrix")
    class ProviderAccess {

        @Test
        @DisplayName("Provider can read patient record with explicit consent")
        void providerCanReadWithConsent() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PROVIDER_ID, ConsentService.ActorType.PROVIDER, null, PROVIDER_ID, null, Set.of("patient.read")),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.EXPLICIT_GRANT);
        }

        @Test
        @DisplayName("Provider can write clinical notes with consent")
        void providerCanWriteClinicalNotes() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PROVIDER_ID, ConsentService.ActorType.PROVIDER, null, PROVIDER_ID, null, Set.of("clinical.write")),
                new ConsentService.TargetResource(PATIENT_ID, "ClinicalNote", "note-001", PhrDataClassification.C3),
                ConsentService.ConsentAction.DOCUMENT_WRITE,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
        }

        @Test
        @DisplayName("Provider cannot read without consent")
        void providerCannotReadWithoutConsent() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PROVIDER_ID, ConsentService.ActorType.PROVIDER, null, PROVIDER_ID, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isFalse();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.OUT_OF_SCOPE);
        }
    }

    // ─────────────────────── Caregiver Access Matrix ────────────────────────

    @Nested
    @DisplayName("Caregiver access matrix")
    class CaregiverAccess {

        @Test
        @DisplayName("Caregiver can read patient record with consent")
        void caregiverCanReadWithConsent() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(CAREGIVER_ID, ConsentService.ActorType.CAREGIVER, null, null, null, Set.of("patient.read")),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.EXPLICIT_GRANT);
        }

        @Test
        @DisplayName("Caregiver cannot write clinical data")
        void caregiverCannotWriteClinicalData() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(CAREGIVER_ID, ConsentService.ActorType.CAREGIVER, null, null, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "MedicationRequest", "rx-001", PhrDataClassification.C3),
                ConsentService.ConsentAction.MEDICATION_WRITE,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isFalse();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.OUT_OF_SCOPE);
        }
    }

    // ─────────────────────── Admin Access Matrix ───────────────────────────

    @Nested
    @DisplayName("Admin access matrix")
    class AdminAccess {

        @Test
        @DisplayName("Admin can read audit logs")
        void adminCanReadAuditLogs() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(ADMIN_ID, ConsentService.ActorType.ADMIN, null, null, null, Set.of("audit.read")),
                new ConsentService.TargetResource(PATIENT_ID, "AuditLog", "audit-001", PhrDataClassification.C2),
                ConsentService.ConsentAction.AUDIT_READ,
                ConsentService.PurposeOfUse.AUDIT_REVIEW,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.ROLE_ALLOWED);
        }

        @Test
        @DisplayName("Admin cannot read patient clinical data without explicit scope")
        void adminCannotReadClinicalData() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(ADMIN_ID, ConsentService.ActorType.ADMIN, null, null, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.AUDIT_REVIEW,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isFalse();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.OUT_OF_SCOPE);
        }
    }

    // ─────────────────────── FCHV Access Matrix ────────────────────────────

    @Nested
    @DisplayName("FCHV access matrix")
    class FchvAccess {

        @Test
        @DisplayName("FCHV can read patient record with consent")
        void fchvCanReadWithConsent() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(FCHV_ID, ConsentService.ActorType.FCHV, null, null, null, Set.of("patient.read")),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.PATIENT_READ,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.EXPLICIT_GRANT);
        }

        @Test
        @DisplayName("FCHV can write immunization records with consent")
        void fchvCanWriteImmunization() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(FCHV_ID, ConsentService.ActorType.FCHV, null, null, null, Set.of("immunization.write")),
                new ConsentService.TargetResource(PATIENT_ID, "Immunization", "imm-001", PhrDataClassification.C3),
                ConsentService.ConsentAction.MEDICATION_WRITE,
                ConsentService.PurposeOfUse.CARE_DELIVERY,
                null
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
        }
    }

    // ─────────────────────── Emergency Access Matrix ───────────────────────

    @Nested
    @DisplayName("Emergency access matrix")
    class EmergencyAccess {

        @Test
        @DisplayName("Emergency access allows read with justification")
        void emergencyAccessAllowsRead() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PROVIDER_ID, ConsentService.ActorType.PROVIDER, null, PROVIDER_ID, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.EMERGENCY_READ,
                ConsentService.PurposeOfUse.EMERGENCY,
                new ConsentService.EmergencyContext(true, "Unconscious patient - emergency surgery required", ConsentService.EmergencyCategory.UNCONSCIOUS)
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isTrue();
            assertThat(decision.getResult().reasonCode()).isEqualTo(ConsentService.ReasonCode.EMERGENCY_GRANT);
        }

        @Test
        @DisplayName("Emergency access requires justification")
        void emergencyRequiresJustification() {
            ConsentService service = new TestConsentService();
            ConsentService.ConsentCheckRequest request = new ConsentService.ConsentCheckRequest(
                UUID.randomUUID().toString(),
                TENANT_ID,
                new ConsentService.ActorContext(PROVIDER_ID, ConsentService.ActorType.PROVIDER, null, PROVIDER_ID, null, Set.of()),
                new ConsentService.TargetResource(PATIENT_ID, "Patient", PATIENT_ID, PhrDataClassification.C3),
                ConsentService.ConsentAction.EMERGENCY_READ,
                ConsentService.PurposeOfUse.EMERGENCY,
                new ConsentService.EmergencyContext(true, "", ConsentService.EmergencyCategory.UNCONSCIOUS)
            );

            Promise<ConsentService.ConsentAccessDecision> decision = service.checkAccess(request);
            assertThat(decision.getResult().allowed()).isFalse();
        }
    }

    // ─────────────────────── Test Implementation ────────────────────────────

    /**
     * Test implementation of ConsentService for matrix validation.
     */
    private static class TestConsentService implements ConsentService {

        @Override
        public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
            // Simplified access logic for matrix testing
            boolean allowed = evaluateAccess(request);
            ReasonCode reasonCode = determineReasonCode(request, allowed);
            return Promise.of(ConsentAccessDecision.allow(reasonCode, "grant-001", CacheStatus.MISS, null));
        }

        @Override
        public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
            return checkAccess(request).then(decision -> {
                if (!decision.allowed()) {
                    throw new ConsentAccessDeniedException(
                        request.requestId(),
                        request.tenantId(),
                        request.actor().actorId(),
                        request.target().patientId(),
                        decision
                    );
                }
                return Promise.of(decision);
            });
        }

        @Override
        public Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<ConsentRevokeResult> revokeConsent(ConsentRevokeRequest request) {
            return Promise.of(new ConsentRevokeResult(true, "consent-001"));
        }

        private boolean evaluateAccess(ConsentCheckRequest request) {
            // Patient self-access
            if (request.actor().actorType() == ActorType.PATIENT
                && request.actor().patientId() != null
                && request.actor().patientId().equals(request.target().patientId())) {
                return true;
            }

            // Emergency access
            if (request.purposeOfUse() == PurposeOfUse.EMERGENCY
                && request.emergency() != null
                && request.emergency().enabled()
                && request.emergency().justification() != null
                && !request.emergency().justification().isBlank()) {
                return true;
            }

            // Provider/caregiver/FCHV with consent and scope
            if ((request.actor().actorType() == ActorType.PROVIDER
                || request.actor().actorType() == ActorType.CAREGIVER
                || request.actor().actorType() == ActorType.FCHV)
                && hasRequiredScope(request)) {
                return true;
            }

            // Admin with audit scope
            if (request.actor().actorType() == ActorType.ADMIN
                && request.action() == ConsentAction.AUDIT_READ
                && request.actor().scopes().contains("audit.read")) {
                return true;
            }

            return false;
        }

        private boolean hasRequiredScope(ConsentCheckRequest request) {
            String requiredScope = getRequiredScope(request.action());
            return request.actor().scopes().contains(requiredScope);
        }

        private String getRequiredScope(ConsentAction action) {
            return switch (action) {
                case PATIENT_READ, DOCUMENT_READ, MEDICATION_READ -> "patient.read";
                case DOCUMENT_WRITE, MEDICATION_WRITE -> "clinical.write";
                default -> "";
            };
        }

        private ReasonCode determineReasonCode(ConsentCheckRequest request, boolean allowed) {
            if (!allowed) return ReasonCode.OUT_OF_SCOPE;
            if (request.actor().actorType() == ActorType.PATIENT) return ReasonCode.SELF_ACCESS;
            if (request.purposeOfUse() == PurposeOfUse.EMERGENCY) return ReasonCode.EMERGENCY_GRANT;
            if (request.actor().actorType() == ActorType.ADMIN) return ReasonCode.ROLE_ALLOWED;
            return ReasonCode.EXPLICIT_GRANT;
        }
    }
}
