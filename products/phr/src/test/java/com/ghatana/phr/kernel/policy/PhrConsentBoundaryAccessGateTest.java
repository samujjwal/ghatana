package com.ghatana.phr.kernel.policy;

import com.ghatana.phr.healthcare.domain.ConsentAction;
import com.ghatana.phr.healthcare.domain.ConsentRecord;
import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.port.ConsentStore;
import com.ghatana.phr.healthcare.service.ConsentEnforcementService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhrConsentBoundaryAccessGate")
class PhrConsentBoundaryAccessGateTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-hospital-1";
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final String PROVIDER_ID = "provider-dr-sharma";

    private StubConsentStore consentStore;
    private List<PhrConsentBoundaryAccessGate.AuditEvent> auditEvents;
    private PhrConsentBoundaryAccessGate gate;

    @BeforeEach
    void setUp() {
        consentStore = new StubConsentStore();
        auditEvents = new ArrayList<>();
        ConsentEnforcementService consentEnforcementService = new ConsentEnforcementService(
            consentStore,
            Executors.newSingleThreadExecutor(),
            new SimpleMeterRegistry()
        );
        gate = new PhrConsentBoundaryAccessGate(consentEnforcementService, auditEvents::add);
    }

    @Test
    @DisplayName("phr-003: Consent grant lifecycle - subject-record read is allowed when boundary and consent both allow")
    void phr003_consentGrantLifecycle_allowsSubjectRecordReadWithConsent() {
        consentStore.activeConsent = Optional.of(activeConsent(ConsentAction.PATIENT_READ, DataClassification.C3));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(false)));

        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.reasonCode()).isEqualTo("EXPLICIT_GRANT");
        assertThat(outcome.boundaryDecision().requiresConsent()).isTrue();
        assertThat(outcome.consentDecision()).isNotNull();
        assertThat(auditEvents).hasSize(1);
        assertThat(auditEvents.get(0).allowed()).isTrue();
        assertThat(auditEvents.get(0).matchedRuleId()).isEqualTo("PHR-BP-001");
    }

    @Test
    @DisplayName("phr-003: Consent grant lifecycle - revoked consent fails closed even when boundary allows the subject-record read")
    void phr003_consentGrantLifecycle_deniesRevokedConsent() {
        consentStore.activeConsent = Optional.of(
            activeConsent(ConsentAction.PATIENT_READ, DataClassification.C3)
                .revoked("Patient withdrew consent", Instant.now().minusSeconds(60))
        );

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(false)));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("GRANT_REVOKED");
        assertThat(auditEvents.get(0).allowed()).isFalse();
        assertThat(auditEvents.get(0).auditRequired()).isTrue();
    }

    @Test
    @DisplayName("phr-003: Consent grant lifecycle - expired consent is reported as GRANT_EXPIRED instead of generic system deny")
    void phr003_consentGrantLifecycle_deniesExpiredConsentExplicitly() {
        consentStore.activeConsent = Optional.of(activeConsent(
            ConsentAction.PATIENT_READ,
            DataClassification.C3,
            Instant.now().minusSeconds(30)
        ));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(false)));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("GRANT_EXPIRED");
        assertThat(outcome.consentDecision()).isNotNull();
        assertThat(outcome.consentDecision().reasonCode())
            .isEqualTo(ConsentEnforcementService.AccessDecision.ReasonCode.GRANT_EXPIRED);
    }

    @Test
    @DisplayName("phr-006: Consent-based access control - subject-record read is allowed when boundary and consent both allow")
    void phr006_consentBasedAccessControl_allowsSubjectRecordReadWithConsent() {
        consentStore.activeConsent = Optional.of(activeConsent(ConsentAction.PATIENT_READ, DataClassification.C3));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(false)));

        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.reasonCode()).isEqualTo("EXPLICIT_GRANT");
        assertThat(outcome.boundaryDecision().requiresConsent()).isTrue();
        assertThat(outcome.consentDecision()).isNotNull();
    }

    @Test
    @DisplayName("phr-006: Consent-based access control - revoked consent fails closed even when boundary allows the subject-record read")
    void phr006_consentBasedAccessControl_deniesRevokedConsent() {
        consentStore.activeConsent = Optional.of(
            activeConsent(ConsentAction.PATIENT_READ, DataClassification.C3)
                .revoked("Patient withdrew consent", Instant.now().minusSeconds(60))
        );

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(false)));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("GRANT_REVOKED");
        assertThat(auditEvents.get(0).allowed()).isFalse();
        assertThat(auditEvents.get(0).auditRequired()).isTrue();
    }

    @Test
    @DisplayName("phr-008: Break-glass emergency access - emergency override allows subject-record read and emits audited access")
    void phr008_breakGlassEmergencyAccess_allowsEmergencyOverride() {
        consentStore.activeConsent = Optional.empty();

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(subjectRecordReadRequest(true)));

        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.reasonCode()).isEqualTo("EMERGENCY_GRANT");
        assertThat(outcome.requiresAudit()).isTrue();
        assertThat(auditEvents.get(0).emergencyOverride()).isTrue();
    }

    @Test
    @DisplayName("interop read requires the boundary feature flag before consent can authorize it")
    void deniesInteropReadWithoutFeature() {
        consentStore.activeConsent = Optional.of(activeConsent(ConsentAction.DOCUMENT_READ, DataClassification.C3));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(new PhrConsentBoundaryAccessGate.AccessRequest(
            "req-interop-deny",
            TENANT_ID,
            PATIENT_ID,
            PROVIDER_ID,
            ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
            com.ghatana.kernel.scope.ScopeDescriptor.product("external-service"),
            com.ghatana.kernel.scope.ScopeDescriptor.domainPack("phr.interop"),
            "phr:interop/patient-summary-1",
            "read",
            ConsentAction.DOCUMENT_READ,
            PhrDataClassification.C3,
            "CARE_DELIVERY",
            Set.of(),
            false,
            null
        )));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("MISSING_REQUIRED_FEATURE");
        assertThat(auditEvents.get(0).allowed()).isFalse();
    }

    @Test
    @DisplayName("interop read is allowed when feature flag and consent are both present")
    void allowsInteropReadWithFeatureAndConsent() {
        consentStore.activeConsent = Optional.of(activeConsent(ConsentAction.DOCUMENT_READ, DataClassification.C3));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(new PhrConsentBoundaryAccessGate.AccessRequest(
            "req-interop-allow",
            TENANT_ID,
            PATIENT_ID,
            PROVIDER_ID,
            ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
            com.ghatana.kernel.scope.ScopeDescriptor.product("external-service"),
            com.ghatana.kernel.scope.ScopeDescriptor.domainPack("phr.interop"),
            "phr:interop/patient-summary-1",
            "read",
            ConsentAction.DOCUMENT_READ,
            PhrDataClassification.C3,
            "CARE_DELIVERY",
            Set.of("phr.interop.enabled"),
            false,
            null
        )));

        assertThat(outcome.allowed()).isTrue();
        assertThat(outcome.boundaryDecision().requiredFeatures()).containsExactly("phr.interop.enabled");
        assertThat(auditEvents.get(0).matchedRuleId()).isEqualTo("PHR-BP-003");
    }

    @Test
    @DisplayName("external direct-bypass attempt is denied by boundary rules even if consent exists")
    void deniesDirectBypassAttempt() {
        consentStore.activeConsent = Optional.of(activeConsent(ConsentAction.PATIENT_READ, DataClassification.C3));

        PhrConsentBoundaryAccessGate.AccessOutcome outcome = runPromise(() -> gate.authorize(new PhrConsentBoundaryAccessGate.AccessRequest(
            "req-bypass",
            TENANT_ID,
            PATIENT_ID,
            PROVIDER_ID,
            ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
            com.ghatana.kernel.scope.ScopeDescriptor.product("external-service"),
            com.ghatana.kernel.scope.ScopeDescriptor.domainPack("phr.clinical"),
            "phr:subject-records/patient-1",
            "read",
            ConsentAction.PATIENT_READ,
            PhrDataClassification.C3,
            "CARE_DELIVERY",
            Set.of(),
            false,
            null
        )));

        assertThat(outcome.allowed()).isFalse();
        assertThat(outcome.reasonCode()).isEqualTo("BOUNDARY_DENY");
        assertThat(outcome.consentDecision()).isNull();
        assertThat(auditEvents.get(0).allowed()).isFalse();
    }

    private PhrConsentBoundaryAccessGate.AccessRequest subjectRecordReadRequest(boolean emergencyOverride) {
        return new PhrConsentBoundaryAccessGate.AccessRequest(
            emergencyOverride ? "req-emergency" : "req-subject-read",
            TENANT_ID,
            PATIENT_ID,
            PROVIDER_ID,
            ConsentEnforcementService.ConsentCheckRequest.ActorType.PROVIDER,
            com.ghatana.kernel.scope.ScopeDescriptor.domainPack("phr.clinical"),
            com.ghatana.kernel.scope.ScopeDescriptor.domainPack("phr.clinical"),
            "phr:subject-records/patient-1",
            "read",
            emergencyOverride ? ConsentAction.EMERGENCY_READ : ConsentAction.PATIENT_READ,
            PhrDataClassification.C3,
            emergencyOverride ? "EMERGENCY" : "CARE_DELIVERY",
            Set.of(),
            emergencyOverride,
            emergencyOverride ? "Trauma emergency" : null
        );
    }

    private ConsentRecord activeConsent(ConsentAction action, DataClassification classification) {
        return activeConsent(action, classification, Instant.now().plusSeconds(3600));
    }

    private ConsentRecord activeConsent(ConsentAction action, DataClassification classification, Instant expiresAt) {
        return ConsentRecord.newGrant(
            TENANT_ID,
            PATIENT_ID,
            PATIENT_ID.toString(),
            "PATIENT",
            PROVIDER_ID,
            "PROVIDER",
            List.of(action),
            classification,
            "CARE_DELIVERY",
            expiresAt,
            "system"
        );
    }

    private static final class StubConsentStore implements ConsentStore {
        private Optional<ConsentRecord> activeConsent = Optional.empty();

        @Override
        public void append(ConsentRecord record) {}

        @Override
        public Optional<ConsentRecord> findActiveConsent(
                String tenantId,
                UUID patientId,
                String granteeId,
                ConsentAction action) {
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
