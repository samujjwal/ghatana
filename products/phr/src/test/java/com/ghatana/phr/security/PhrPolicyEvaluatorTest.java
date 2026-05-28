package com.ghatana.phr.security;

import com.ghatana.phr.api.routes.PhrRouteSupport;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR Policy Evaluator Tests")
class PhrPolicyEvaluatorTest extends EventloopTestBase {

    private static PhrPolicyEvaluator createEvaluator() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        Mockito.when(treatmentRelationshipService.hasActiveTreatmentRelationship(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Promise.of(true));
        return new PhrPolicyEvaluator(consentService, treatmentRelationshipService, fchvAssignmentService);
    }

    @Test
    @DisplayName("Patient can access own record")
    void patientCanAccessOwnRecord() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-123", "patient", "corr-1", "patient", "core", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPatientRecordAsync(context, "patient-123"));

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Patient cannot access another record")
    void patientCannotAccessOtherPatientRecord() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-123", "patient", "corr-1", "patient", "core", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPatientRecordAsync(context, "patient-999"));

        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Clinician access requires treatment relationship")
    void clinicianCanAccessPatientRecordWithTreatmentRelationship() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "clinician-1", "clinician", "corr-1", "clinician", "core", null);

        PhrPolicyEvaluator.PolicyDecision decision = runPromise(
            () -> evaluator.canAccessPatientRecordAsync(context, "patient-999"));

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Emergency access is limited to clinicians and admins")
    void emergencyAccessIsRoleBound() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext clinicianContext = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "clinician-1", "clinician", "corr-1", "clinician", "core", null);
        PhrRouteSupport.PhrRequestContext patientContext = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-1", "patient", "corr-1", "patient", "core", null);

        PhrPolicyEvaluator.PolicyDecision clinicianDecision = runPromise(
            () -> evaluator.canAccessEmergency(clinicianContext, "patient-1", "Emergency treatment"));
        PhrPolicyEvaluator.PolicyDecision patientDecision = runPromise(
            () -> evaluator.canAccessEmergency(patientContext, "patient-1", "Emergency treatment"));

        assertThat(clinicianDecision.isAllowed()).isTrue();
        assertThat(patientDecision.isAllowed()).isFalse();
    }
}
