package com.ghatana.phr.security;

import com.ghatana.phr.api.routes.PhrRouteSupport;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR Policy Evaluator Tests")
class PhrPolicyEvaluatorTest {

    private static PhrPolicyEvaluator createEvaluator() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        FchvCommunityAssignmentService fchvAssignmentService = Mockito.mock(FchvCommunityAssignmentService.class);
        return new PhrPolicyEvaluator(consentService, treatmentRelationshipService, fchvAssignmentService);
    }

    @Test
    @DisplayName("Patient can access own record")
    void patientCanAccessOwnRecord() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-123", "patient", "corr-1", "patient", "core", null);

        assertThat(evaluator.canAccessPatientRecord(context, "patient-123")).isTrue();
    }

    @Test
    @DisplayName("Patient cannot access another record")
    void patientCannotAccessOtherPatientRecord() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-123", "patient", "corr-1", "patient", "core", null);

        assertThat(evaluator.canAccessPatientRecord(context, "patient-999")).isFalse();
    }

    @Test
    @DisplayName("Clinician is provisionally allowed by the policy evaluator")
    void clinicianCanAccessPatientRecordProvisional() {
        PhrRouteSupport.PhrRequestContext context = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "clinician-1", "clinician", "corr-1", "clinician", "core", null);

        assertThat(createEvaluator().canAccessPatientRecord(context, "patient-999")).isTrue();
    }

    @Test
    @DisplayName("Emergency access is limited to clinicians and admins")
    void emergencyAccessIsRoleBound() {
        PhrPolicyEvaluator evaluator = createEvaluator();
        PhrRouteSupport.PhrRequestContext clinicianContext = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "clinician-1", "clinician", "corr-1", "clinician", "core", null);
        PhrRouteSupport.PhrRequestContext patientContext = new PhrRouteSupport.PhrRequestContext(
            "tenant-1", "patient-1", "patient", "corr-1", "patient", "core", null);

        assertThat(evaluator.canAccessEmergency(clinicianContext).isAllowed()).isTrue();
        assertThat(evaluator.canAccessEmergency(patientContext).isAllowed()).isFalse();
    }
}
