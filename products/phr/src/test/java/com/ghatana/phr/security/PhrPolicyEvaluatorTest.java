package com.ghatana.phr.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.api.routes.PhrRouteSupport;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ConsentManagementService.ConsentValidationResult;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR Policy Evaluator Tests")
class PhrPolicyEvaluatorTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private static PolicyHarness createHarness() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        CaregiverService caregiverService = Mockito.mock(CaregiverService.class);
        AuditTrailService auditTrailService = Mockito.mock(AuditTrailService.class);
        Mockito.lenient().when(treatmentRelationshipService.hasActiveTreatmentRelationship(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Promise.of(true));
        Mockito.lenient().when(consentService.validateAccess(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Promise.of(new ConsentValidationResult(true, "Valid grant", "grant-1")));
        Mockito.lenient().when(fchvAssignmentService.hasCommunityAccess(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Promise.of(true));
        Mockito.lenient().when(caregiverService.getPatientsForCaregiver("caregiver-1"))
            .thenReturn(Promise.of(List.of(activeCaregiverRelationship("caregiver-1", "patient-1", Set.of("*")))));
        return new PolicyHarness(
            new PhrPolicyEvaluator(
                consentService,
                treatmentRelationshipService,
                fchvAssignmentService,
                auditTrailService,
                caregiverService
            ),
            consentService,
            treatmentRelationshipService,
            fchvAssignmentService,
            caregiverService,
            auditTrailService
        );
    }

    private static PhrRouteSupport.PhrRequestContext context(
            String tenantId,
            String principalId,
            String role,
            String facilityId) {
        return new PhrRouteSupport.PhrRequestContext(
            tenantId, principalId, role, "corr-1", role, "core", facilityId);
    }

    private record PolicyHarness(
            PhrPolicyEvaluator evaluator,
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            CaregiverService caregiverService,
            AuditTrailService auditTrailService) {
    }

    private static CaregiverService.CaregiverRelationship activeCaregiverRelationship(
            String caregiverId,
            String patientId,
            Set<String> consentScope) {
        return new CaregiverService.CaregiverRelationship(
            "rel-" + caregiverId + "-" + patientId,
            caregiverId,
            patientId,
            CaregiverService.RelationshipType.LEGAL_GUARDIAN,
            consentScope,
            CaregiverService.RelationshipStatus.ACTIVE,
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600)
        );
    }

    @Test
    @DisplayName("Kernel policy dispatcher registrations match the canonical PHR policy registry")
    void kernelPolicyDispatcherRegistrationsMatchCanonicalRegistry() throws Exception {
        JsonNode registry = JSON.readTree(policyRegistryPath().toFile());
        Set<String> registryPolicyIds = new LinkedHashSet<>();
        Iterator<String> fieldNames = registry.path("policies").fieldNames();
        while (fieldNames.hasNext()) {
            registryPolicyIds.add(fieldNames.next());
        }

        assertThat(PhrPolicyEvaluator.registeredPolicyIdsForTest())
            .containsExactlyInAnyOrderElementsOf(registryPolicyIds);
    }

    private static Path policyRegistryPath() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("products/phr/config/policy-registry.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path moduleCandidate = current.resolve("config/policy-registry.json");
            if (Files.exists(moduleCandidate)) {
                return moduleCandidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate products/phr/config/policy-registry.json");
    }

    @Test
    @DisplayName("Route contract policy IDs have explicit evaluator mappings")
    void routeContractPolicyIdsHaveExplicitEvaluatorMappings() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext patientContext = context("tenant-1", "patient-1", "patient", null);
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");

        for (String policyId : new String[]{
            "phr.dashboard.view",
            "phr.settings.access",
            "phr.notifications.access",
            "phr.forbidden.access",
            "phr.not.found.access"
        }) {
            PhrPolicyEvaluator.PolicyDecision decision = runPromise(
                () -> harness.evaluator().evaluateByPolicyId(policyId, patientContext, null, null));
            assertThat(decision.isAllowed()).as(policyId).isTrue();
            assertThat(decision.getReasonCode()).as(policyId).isNotEqualTo("UNKNOWN_POLICY_ID");
        }

        for (String policyId : new String[]{
            "phr.records.view",
            "phr.consents.access",
            "phr.appointments.access",
            "phr.labs.access",
            "phr.medications.access",
            "phr.conditions.access",
            "phr.observations.access",
            "phr.immunizations.access",
            "phr.documents.access",
            "phr.documents.upload.access",
            "phr.documents.doc-id.ocr.access",
            "phr.timeline.access",
            "phr.profile.access",
            "phr.records.record-id.access"
        }) {
            PhrPolicyEvaluator.PolicyDecision decision = runPromise(
                () -> harness.evaluator().evaluateByPolicyId(policyId, patientContext, "patient-1", null));
            assertThat(decision.isAllowed()).as(policyId).isTrue();
            assertThat(decision.getReasonCode()).as(policyId).isNotEqualTo("UNKNOWN_POLICY_ID");
        }

        for (String policyId : new String[]{
            "phr.emergency.review",
            "phr.release.readiness.access",
            "phr.audit.access"
        }) {
            PhrPolicyEvaluator.PolicyDecision decision = runPromise(
                () -> harness.evaluator().evaluateByPolicyId(policyId, adminContext, null, null));
            assertThat(decision.isAllowed()).as(policyId).isTrue();
            assertThat(decision.getReasonCode()).as(policyId).isNotEqualTo("UNKNOWN_POLICY_ID");
        }
    }

    @Test
    @DisplayName("Hidden route policies are registered but fail closed")
    void hiddenRoutePoliciesFailClosed() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");

        for (String policyId : new String[]{
            "phr.provider.dashboard.view",
            "phr.provider.patients.view",
            "phr.caregiver.dependents.view",
            "phr.fchv.dashboard.view"
        }) {
            PhrPolicyEvaluator.PolicyDecision decision = runPromise(
                () -> harness.evaluator().evaluateByPolicyId(policyId, adminContext, null, null));
            assertThat(decision.isAllowed()).as(policyId).isFalse();
            assertThat(decision.getReasonCode()).as(policyId).isEqualTo("HIDDEN_ROUTE_NOT_MOUNTED");
        }
    }

    @Test
    @DisplayName("Patient can access own record")
    void patientCanAccessOwnRecord() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "patient-123", "patient", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPatientRecordAsync(context, "patient-123"));

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Patient cannot access another record")
    void patientCannotAccessOtherPatientRecord() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "patient-123", "patient", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPatientRecordAsync(context, "patient-999"));

        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Clinician access requires treatment relationship")
    void clinicianCanAccessPatientRecordWithTreatmentRelationship() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPatientRecordAsync(context, "patient-999"));

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Caregiver access uses resource and action scoped consent")
    void caregiverAccessUsesResourceAndActionScopedConsent() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "DocumentReference", "READ", "tenant-1", null));

        assertThat(decision.isAllowed()).isTrue();
        Mockito.verify(harness.consentService())
            .validateAccess("patient-1", "caregiver-1", "DocumentReference", "READ");
    }

    @Test
    @DisplayName("Caregiver resource or action mismatch is denied")
    void caregiverResourceOrActionMismatchIsDenied() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.consentService().validateAccess("patient-1", "caregiver-1", "Observation", "WRITE"))
            .thenReturn(Promise.of(new ConsentValidationResult(false, "Action not granted", null)));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "WRITE", "tenant-1", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CAREGIVER_CONSENT_DENIED");
    }

    @Test
    @DisplayName("Caregiver access requires active dependent relationship before consent lookup")
    void caregiverAccessRequiresDependentRelationshipBeforeConsentLookup() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.caregiverService().getPatientsForCaregiver("caregiver-1"))
            .thenReturn(Promise.of(List.of()));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CAREGIVER_RELATIONSHIP_REQUIRED");
        Mockito.verify(harness.consentService(), Mockito.never())
            .validateAccess("patient-1", "caregiver-1", "Observation", "READ");
    }

    @Test
    @DisplayName("Caregiver access requires relationship scope before consent lookup")
    void caregiverAccessRequiresRelationshipScopeBeforeConsentLookup() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.caregiverService().getPatientsForCaregiver("caregiver-1"))
            .thenReturn(Promise.of(List.of(
                activeCaregiverRelationship("caregiver-1", "patient-1", Set.of("Medication"))
            )));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CAREGIVER_RELATIONSHIP_REQUIRED");
        Mockito.verify(harness.consentService(), Mockito.never())
            .validateAccess("patient-1", "caregiver-1", "Observation", "READ");
    }

    @Test
    @DisplayName("PHI access denies tenant mismatch and facility mismatch")
    void deniesTenantAndFacilityMismatch() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision tenantDecision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-2", "facility-1"));
        PhrPolicyEvaluator.PolicyDecision facilityDecision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-2"));

        assertThat(tenantDecision.isAllowed()).isFalse();
        assertThat(tenantDecision.getReasonCode()).isEqualTo("TENANT_SCOPE_MISMATCH");
        assertThat(facilityDecision.isAllowed()).isFalse();
        assertThat(facilityDecision.getReasonCode()).isEqualTo("FACILITY_SCOPE_MISMATCH");
    }

    @Test
    @DisplayName("PHI access denies missing tenant or action")
    void deniesMissingTenantOrAction() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision tenantDecision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "", "facility-1"));
        PhrPolicyEvaluator.PolicyDecision actionDecision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "", "tenant-1", "facility-1"));

        assertThat(tenantDecision.getReasonCode()).isEqualTo("MISSING_TENANT");
        assertThat(actionDecision.getReasonCode()).isEqualTo("MISSING_ACTION");
    }

    @Test
    @DisplayName("Admin PHI access requires explicit route-level justification")
    void adminPhiAccessRequiresJustification() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_JUSTIFICATION_REQUIRED");
    }

    @Test
    @DisplayName("FCHV access requires community assignment")
    void fchvAccessRequiresCommunityAssignment() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("FCHV_NO_COMMUNITY_ACCESS");
    }

    @Test
    @DisplayName("Unknown role fails closed")
    void unknownRoleFailsClosed() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "actor-1", "researcher", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("UNKNOWN_ROLE");
    }

    @Test
    @DisplayName("Emergency access is limited to clinicians and admins")
    void emergencyAccessIsRoleBound() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");
        PhrRouteSupport.PhrRequestContext patientContext = context("tenant-1", "patient-1", "patient", null);

        PhrPolicyEvaluator.PolicyDecision clinicianDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(clinicianContext, "patient-1", "Emergency treatment"));
        PhrPolicyEvaluator.PolicyDecision patientDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(patientContext, "patient-1", "Emergency treatment"));

        assertThat(clinicianDecision.isAllowed()).isTrue();
        assertThat(patientDecision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Emergency access fails closed without patient ID or justification")
    void emergencyAccessRequiresPatientAndJustification() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision missingPatientDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(clinicianContext, "", "Emergency treatment"));
        PhrPolicyEvaluator.PolicyDecision missingJustificationDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(clinicianContext, "patient-1", ""));

        assertThat(missingPatientDecision.getReasonCode()).isEqualTo("MISSING_PATIENT_ID");
        assertThat(missingJustificationDecision.getReasonCode()).isEqualTo("MISSING_JUSTIFICATION");
    }

    @Test
    @DisplayName("Consent checks are limited to owner, admin, or same accessor")
    void consentChecksAreScopedToOwnerAdminOrSameAccessor() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext patientContext = context("tenant-1", "patient-1", "patient", null);
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");
        PhrRouteSupport.PhrRequestContext caregiverContext = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision patientDecision =
            harness.evaluator().canCheckConsent(patientContext, "patient-1", "clinician-1");
        PhrPolicyEvaluator.PolicyDecision adminDecision =
            harness.evaluator().canCheckConsent(adminContext, "patient-1", "clinician-1");
        PhrPolicyEvaluator.PolicyDecision sameAccessorDecision =
            harness.evaluator().canCheckConsent(clinicianContext, "patient-1", "clinician-1");
        PhrPolicyEvaluator.PolicyDecision differentAccessorDecision =
            harness.evaluator().canCheckConsent(caregiverContext, "patient-1", "clinician-1");

        assertThat(patientDecision.isAllowed()).isTrue();
        assertThat(adminDecision.isAllowed()).isTrue();
        assertThat(adminDecision.requiresAudit()).isTrue();
        assertThat(sameAccessorDecision.isAllowed()).isTrue();
        assertThat(differentAccessorDecision.isAllowed()).isFalse();
        assertThat(differentAccessorDecision.getReasonCode()).isEqualTo("CONSENT_CHECK_DENIED");
    }

    @Test
    @DisplayName("Audit event queries require role-appropriate scope")
    void auditEventQueriesRequireRoleAppropriateScope() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");
        PhrRouteSupport.PhrRequestContext patientContext = context("tenant-1", "patient-1", "patient", null);
        PhrRouteSupport.PhrRequestContext caregiverContext = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision adminDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(adminContext, null));
        PhrPolicyEvaluator.PolicyDecision scopedClinicianDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(clinicianContext, "patient-1"));
        PhrPolicyEvaluator.PolicyDecision unscopedClinicianDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(clinicianContext, null));
        PhrPolicyEvaluator.PolicyDecision selfPatientDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(patientContext, "patient-1"));
        PhrPolicyEvaluator.PolicyDecision otherPatientDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(patientContext, "patient-2"));
        PhrPolicyEvaluator.PolicyDecision caregiverDecision =
            runPromise(() -> harness.evaluator().canQueryAuditEventsAsync(caregiverContext, "patient-1"));

        assertThat(adminDecision.isAllowed()).isTrue();
        assertThat(adminDecision.requiresAudit()).isTrue();
        assertThat(scopedClinicianDecision.isAllowed()).isTrue();
        assertThat(scopedClinicianDecision.requiresAudit()).isTrue();
        assertThat(unscopedClinicianDecision.isAllowed()).isFalse();
        assertThat(unscopedClinicianDecision.getReasonCode()).isEqualTo("AUDIT_PATIENT_SCOPE_REQUIRED");
        assertThat(selfPatientDecision.isAllowed()).isTrue();
        assertThat(otherPatientDecision.isAllowed()).isFalse();
        assertThat(caregiverDecision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Clinician audit query is denied without treatment relationship or consent")
    void clinicianAuditQueryDeniedWithoutRelationshipOrConsent() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-2"))
            .thenReturn(Promise.of(false));
        Mockito.when(harness.consentService().validateAccess("patient-2", "clinician-1", "audit", "READ"))
            .thenReturn(Promise.of(new ConsentValidationResult(false, "No audit consent", null)));
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canQueryAuditEventsAsync(clinicianContext, "patient-2"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_SCOPE_REQUIRED");
    }

    @Test
    @DisplayName("Audit event detail access is scoped by role")
    void auditEventDetailAccessIsScopedByRole() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");
        PhrRouteSupport.PhrRequestContext patientContext = context("tenant-1", "patient-1", "patient", null);
        PhrRouteSupport.PhrRequestContext fchvContext = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision adminDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(adminContext, "user-1", "patient-2"));
        PhrPolicyEvaluator.PolicyDecision clinicianPatientScopedDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(clinicianContext, "user-1", "patient-2"));
        PhrPolicyEvaluator.PolicyDecision clinicianUnscopedDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(clinicianContext, "user-1", null));
        PhrPolicyEvaluator.PolicyDecision patientOwnEntityDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(patientContext, "user-1", "patient-1"));
        PhrPolicyEvaluator.PolicyDecision patientOtherEntityDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(patientContext, "user-1", "patient-2"));
        PhrPolicyEvaluator.PolicyDecision fchvDecision =
            runPromise(() -> harness.evaluator().canViewAuditEventAsync(fchvContext, "user-1", "patient-1"));

        assertThat(adminDecision.isAllowed()).isTrue();
        assertThat(adminDecision.requiresAudit()).isTrue();
        assertThat(clinicianPatientScopedDecision.isAllowed()).isTrue();
        assertThat(clinicianPatientScopedDecision.requiresAudit()).isTrue();
        assertThat(clinicianUnscopedDecision.isAllowed()).isFalse();
        assertThat(clinicianUnscopedDecision.getReasonCode()).isEqualTo("AUDIT_PATIENT_SCOPE_REQUIRED");
        assertThat(patientOwnEntityDecision.isAllowed()).isTrue();
        assertThat(patientOtherEntityDecision.isAllowed()).isFalse();
        assertThat(fchvDecision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Treatment relationship test matrix - clinician with relationship")
    void clinicianWithTreatmentRelationshipCanAccess() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(true));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_TREATMENT_RELATIONSHIP");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - clinician without relationship falls back to consent")
    void clinicianWithoutTreatmentRelationshipFallsBackToConsent() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(false));
        Mockito.when(harness.consentService().validateAccess("patient-1", "clinician-1", "Observation", "READ"))
            .thenReturn(Promise.of(new ConsentValidationResult(true, "Valid consent", "grant-1")));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_CONSENT_GRANTED");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - clinician without relationship and no consent denied")
    void clinicianWithoutRelationshipOrConsentDenied() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(false));
        Mockito.when(harness.consentService().validateAccess("patient-1", "clinician-1", "Observation", "READ"))
            .thenReturn(Promise.of(new ConsentValidationResult(false, "No consent", null)));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_SCOPE_REQUIRED");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - clinician without facility scope uses consent")
    void clinicianWithoutFacilityScopeUsesConsent() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", null);
        Mockito.when(harness.consentService().validateAccess("patient-1", "clinician-1", "Observation", "READ"))
            .thenReturn(Promise.of(new ConsentValidationResult(true, "Valid consent", "grant-1")));

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", null));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_CONSENT_GRANTED");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - multiple patients access")
    void clinicianCanAccessMultiplePatientsWithRelationships() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(true));
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-2"))
            .thenReturn(Promise.of(true));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision1 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));
        PhrPolicyEvaluator.PolicyDecision decision2 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-2", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision1.isAllowed()).isTrue();
        assertThat(decision2.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Treatment relationship test matrix - expired relationship denied")
    void clinicianWithExpiredRelationshipDenied() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(false));
        Mockito.when(harness.consentService().validateAccess("patient-1", "clinician-1", "Observation", "READ"))
            .thenReturn(Promise.of(new ConsentValidationResult(false, "No active consent", null)));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("CLINICIAN_SCOPE_REQUIRED");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - cross-tenant relationship denied")
    void clinicianCrossTenantRelationshipDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-2", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("TENANT_SCOPE_MISMATCH");
    }

    @Test
    @DisplayName("Treatment relationship test matrix - emergency override bypasses relationship")
    void emergencyOverrideBypassesTreatmentRelationship() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1", "Emergency treatment"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.isEmergencyOverride()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_BREAK_GLASS");
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV with community access allowed")
    void fchvWithCommunityAccessCanAccess() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(true));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("FCHV_COMMUNITY_ACCESS");
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV without community access denied")
    void fchvWithoutCommunityAccessDenied() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("FCHV_NO_COMMUNITY_ACCESS");
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV can access multiple patients in community")
    void fchvCanAccessMultiplePatientsInCommunity() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(true));
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-2"))
            .thenReturn(Promise.of(true));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision1 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));
        PhrPolicyEvaluator.PolicyDecision decision2 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-2", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision1.isAllowed()).isTrue();
        assertThat(decision2.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV denied access outside community")
    void fchvDeniedAccessOutsideCommunity() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(true));
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-2"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision1 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));
        PhrPolicyEvaluator.PolicyDecision decision2 = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-2", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision1.isAllowed()).isTrue();
        assertThat(decision2.isAllowed()).isFalse();
        assertThat(decision2.getReasonCode()).isEqualTo("FCHV_NO_COMMUNITY_ACCESS");
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV cross-tenant access denied")
    void fchvCrossTenantAccessDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-2", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("TENANT_SCOPE_MISMATCH");
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV without facility scope denied before community lookup")
    void fchvWithoutFacilityScopeDeniedBeforeCommunityLookup() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("FCHV_FACILITY_SCOPE_REQUIRED");
        Mockito.verify(harness.fchvAssignmentService(), Mockito.never())
            .hasCommunityAccess(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    @DisplayName("FCHV community assignment matrix - FCHV emergency override requires justification")
    void fchvEmergencyOverrideRequiresJustification() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.fchvAssignmentService().hasCommunityAccess("fchv-1", "patient-1"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1", "Emergency treatment"));

        // FCHV is not allowed emergency access (only clinician/admin)
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("Admin justification path - admin with valid justification allowed")
    void adminWithValidJustificationCanAccess() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ",
                "COMPLIANCE_AUDIT",
                "Compliance audit for patient record review - case #12345"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.requiresAudit()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_JUSTIFIED_PHI_ACCESS");
    }

    @Test
    @DisplayName("Admin justification path - audit stores protected reference only")
    void adminJustificationAuditStoresProtectedReferenceOnly() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        AuditTrailService auditTrailService = Mockito.mock(AuditTrailService.class);
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvAssignmentService,
            auditTrailService
        );
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");
        String rawJustification = "Compliance audit for patient record review - case #12345";

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ", "COMPLIANCE_AUDIT", rawJustification));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getReasonMessage()).doesNotContain(rawJustification);
        ArgumentCaptor<AuditTrailService.AuditTrailEvent> eventCaptor =
            ArgumentCaptor.forClass(AuditTrailService.AuditTrailEvent.class);
        Mockito.verify(auditTrailService).recordAuditEvent(eventCaptor.capture());
        AuditTrailService.AuditTrailEvent event = eventCaptor.getValue();
        assertThat(event.getData().values().toString()).doesNotContain(rawJustification);
        assertThat(event.getData()).containsEntry("justificationCaptured", true);
        assertThat(event.getData()).containsEntry("purpose", "COMPLIANCE_AUDIT");
        assertThat(event.getData()).containsEntry("postAccessReviewRequired", true);
        assertThat(event.getData()).containsEntry("postAccessReviewType", "ADMIN_PHI_ACCESS");
        assertThat(event.getData().get("justificationHash")).isInstanceOf(String.class);
        assertThat((String) event.getData().get("justificationHash"))
            .matches("[0-9a-f]{64}")
            .doesNotContain(rawJustification);
    }

    @Test
    @DisplayName("Policy telemetry uses safe dimensions without tenant identifiers")
    void policyTelemetryUsesSafeDimensionsWithoutTenantIdentifiers() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        AuditTrailService auditTrailService = Mockito.mock(AuditTrailService.class);
        KernelTelemetryManager telemetryManager = Mockito.mock(KernelTelemetryManager.class);
        Mockito.when(treatmentRelationshipService.hasActiveTreatmentRelationship(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Promise.of(true));
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvAssignmentService,
            auditTrailService,
            telemetryManager
        );
        PhrRouteSupport.PhrRequestContext context = context("tenant-secret", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessEmergency(context, "patient-1", "Emergency treatment - patient unconscious"));

        assertThat(decision.isAllowed()).isTrue();
        ArgumentCaptor<String[]> tagsCaptor = ArgumentCaptor.forClass(String[].class);
        Mockito.verify(telemetryManager)
            .incrementCounter(Mockito.eq("phr.emergency.access"), Mockito.eq(1L), tagsCaptor.capture());
        assertThat(tagsCaptor.getValue())
            .doesNotContain("tenant_id")
            .doesNotContain("tenant-secret");
    }

    @Test
    @DisplayName("Admin justification path - explicit purpose is required")
    void adminPurposeRequired() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ",
                "Compliance audit for patient record review"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_PURPOSE_REQUIRED");
    }

    @Test
    @DisplayName("Admin justification path - purpose must be authorized")
    void adminPurposeMustBeAuthorized() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ", "GENERAL_BROWSING",
                "Compliance audit for patient record review"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_PURPOSE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Audited PHI access fails closed when audit service is unavailable")
    void auditedPhiAccessFailsClosedWhenAuditServiceUnavailable() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        Mockito.when(treatmentRelationshipService.hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(true));
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvAssignmentService,
            null
        );
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("AUDIT_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("Audited PHI access fails closed when audit write fails")
    void auditedPhiAccessFailsClosedWhenAuditWriteFails() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        AuditTrailService auditTrailService = Mockito.mock(AuditTrailService.class);
        Mockito.when(treatmentRelationshipService.hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(true));
        Mockito.doThrow(new IllegalStateException("audit down"))
            .when(auditTrailService).recordAuditEvent(Mockito.any());
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvAssignmentService,
            auditTrailService
        );
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("AUDIT_WRITE_FAILED");
    }

    @Test
    @DisplayName("Admin justification path - non-admin denied")
    void nonAdminDeniedJustificationPath() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ",
                "Compliance audit for patient record review"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_REQUIRED");
    }

    @Test
    @DisplayName("Admin justification path - missing justification denied")
    void adminWithoutJustificationDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MISSING_JUSTIFICATION");
    }

    @Test
    @DisplayName("Admin justification path - blank justification denied")
    void adminWithBlankJustificationDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ", ""));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MISSING_JUSTIFICATION");
    }

    @Test
    @DisplayName("Fail-closed when consent service is unavailable")
    void failsClosedWhenConsentServiceUnavailable() {
        // Create evaluator with null consent service to simulate unavailability
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            null, // consent service unavailable
            Mockito.mock(TreatmentRelationshipService.class),
            Mockito.mock(FchvCommunityAssignmentService.class)
        );

        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", null));

        // Should fail closed (deny) when service is unavailable
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Fail-closed when treatment relationship service is unavailable")
    void failsClosedWhenTreatmentRelationshipServiceUnavailable() {
        // Create evaluator with null treatment service to simulate unavailability
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            Mockito.mock(ConsentManagementService.class),
            null, // treatment service unavailable
            Mockito.mock(FchvCommunityAssignmentService.class)
        );

        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        // Should fail closed (deny) when service is unavailable
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Fail-closed when FCHV community assignment service is unavailable")
    void failsClosedWhenFchvServiceUnavailable() {
        // Create evaluator with null FCHV service to simulate unavailability
        PhrPolicyEvaluator evaluator = new PhrPolicyEvaluator(
            Mockito.mock(ConsentManagementService.class),
            Mockito.mock(TreatmentRelationshipService.class),
            null // FCHV service unavailable
        );

        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPhiResourceAsync(
                context, "patient-1", "Observation", "READ", "tenant-1", "facility-1"));

        // Should fail closed (deny) when service is unavailable
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Admin justification path - short justification denied")
    void adminWithShortJustificationDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                context, "patient-1", "Observation", "READ", "Short"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("JUSTIFICATION_TOO_SHORT");
    }

    @Test
    @DisplayName("Admin justification path - null context denied")
    void adminJustificationWithNullContextDenied() {
        PolicyHarness harness = createHarness();

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessPhiWithAdminJustification(
                null, "patient-1", "Observation", "READ",
                "Compliance audit for patient record review"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("INVALID_CONTEXT");
    }

    @Test
    @DisplayName("Emergency policy test - clinician with justification allowed with audit")
    void clinicianEmergencyAccessWithJustification() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment - patient unconscious"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.isEmergencyOverride()).isTrue();
        assertThat(decision.requiresAudit()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_WITH_RELATIONSHIP");
    }

    @Test
    @DisplayName("Emergency policy test - admin with justification allowed with audit")
    void adminEmergencyAccessWithJustification() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency compliance audit - legal requirement"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.isEmergencyOverride()).isTrue();
        assertThat(decision.requiresAudit()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("ADMIN_EMERGENCY");
    }

    @Test
    @DisplayName("Emergency policy test - patient denied emergency access")
    void patientDeniedEmergencyAccess() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "patient-1", "patient", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("Emergency policy test - caregiver denied emergency access")
    void caregiverDeniedEmergencyAccess() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "caregiver-1", "caregiver", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("Emergency policy test - FCHV denied emergency access")
    void fchvDeniedEmergencyAccess() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "fchv-1", "fchv", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("Emergency policy test - missing patient ID denied")
    void emergencyAccessMissingPatientIdDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "",
                "Emergency treatment"));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MISSING_PATIENT_ID");
    }

    @Test
    @DisplayName("Emergency policy test - missing justification denied")
    void emergencyAccessMissingJustificationDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1", ""));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MISSING_JUSTIFICATION");
    }

    @Test
    @DisplayName("Emergency policy test - null justification denied")
    void emergencyAccessNullJustificationDenied() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1", null));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCode()).isEqualTo("MISSING_JUSTIFICATION");
    }

    @Test
    @DisplayName("Emergency policy test - clinician break-glass without relationship allowed with audit")
    void clinicianBreakGlassWithoutRelationshipAllowedWithAudit() {
        PolicyHarness harness = createHarness();
        Mockito.when(harness.treatmentRelationshipService().hasActiveTreatmentRelationship("clinician-1", "patient-1"))
            .thenReturn(Promise.of(false));
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment - no prior relationship"));

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.isEmergencyOverride()).isTrue();
        assertThat(decision.requiresAudit()).isTrue();
        assertThat(decision.getReasonCode()).isEqualTo("EMERGENCY_BREAK_GLASS");
    }

    @Test
    @DisplayName("Emergency policy test - emergency access always requires audit")
    void emergencyAccessAlwaysRequiresAudit() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext clinicianContext = context("tenant-1", "clinician-1", "clinician", "facility-1");
        PhrRouteSupport.PhrRequestContext adminContext = context("tenant-1", "admin-1", "admin", "facility-1");

        PhrPolicyEvaluator.PolicyDecision clinicianDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(clinicianContext, "patient-1",
                "Emergency treatment"));
        PhrPolicyEvaluator.PolicyDecision adminDecision = runPromise(
            () -> harness.evaluator().canAccessEmergency(adminContext, "patient-1",
                "Emergency compliance audit"));

        assertThat(clinicianDecision.requiresAudit()).isTrue();
        assertThat(adminDecision.requiresAudit()).isTrue();
    }

    @Test
    @DisplayName("Emergency policy test - emergency access marked as override")
    void emergencyAccessMarkedAsOverride() {
        PolicyHarness harness = createHarness();
        PhrRouteSupport.PhrRequestContext context = context("tenant-1", "clinician-1", "clinician", "facility-1");

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> harness.evaluator().canAccessEmergency(context, "patient-1",
                "Emergency treatment"));

        assertThat(decision.isEmergencyOverride()).isTrue();
    }
}
