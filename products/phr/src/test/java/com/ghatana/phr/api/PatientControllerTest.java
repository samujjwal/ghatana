package com.ghatana.phr.api;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

    @Mock
    private PolicyEnforcementPoint policyEnforcementPoint;

    @Mock
    private KernelSecurityManager securityManager;

    @Mock
    private PatientService patientService;

    @Mock
    private SecurityContext securityContext;

    private PatientController controller;

    @BeforeEach
    void setUp() {
        controller = new PatientController(policyEnforcementPoint, securityManager, patientService);
    }

    @Test
    void getPatientRecordsReturnsUnauthorizedWhenContextMissing() {
        when(securityManager.getCurrentContext()).thenReturn(null);

        PatientController.Response response = controller.getPatientRecords("patient-1", "token");

        assertEquals(401, response.getStatusCode());
        assertFalse(response.isSuccess());
    }

    @Test
    void createPatientRecordInvokesServiceWhenAuthorized() {
        when(securityManager.getCurrentContext()).thenReturn(securityContext);
        when(policyEnforcementPoint.enforce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(securityContext)))
            .thenReturn(io.activej.promise.Promise.of(PolicyEnforcementPoint.EnforcementDecision.allow()));

        PatientController.Response response = controller.createPatientRecord(
            "patient-1",
            Map.of("diagnosis", "Hypertension"),
            "token"
        );

        assertEquals(200, response.getStatusCode());
        assertTrue(response.isSuccess());
        verify(patientService).createRecord("patient-1", Map.of("diagnosis", "Hypertension"));
    }

    @Test
    void getPatientRecordsReturnsForbiddenWhenPolicyDenies() {
        when(securityManager.getCurrentContext()).thenReturn(securityContext);
        when(policyEnforcementPoint.enforce(any(), org.mockito.ArgumentMatchers.same(securityContext)))
            .thenReturn(io.activej.promise.Promise.of(PolicyEnforcementPoint.EnforcementDecision.deny("Consent required")));

        PatientController.Response response = controller.getPatientRecords("patient-1", "token");

        assertEquals(403, response.getStatusCode());
        assertFalse(response.isSuccess());
        verify(patientService, never()).getRecords("patient-1");
    }

    @Test
    void createPatientRecordSanitizesStructuredPayload() {
        when(securityManager.getCurrentContext()).thenReturn(securityContext);
        when(policyEnforcementPoint.enforce(any(), org.mockito.ArgumentMatchers.same(securityContext)))
            .thenReturn(io.activej.promise.Promise.of(PolicyEnforcementPoint.EnforcementDecision.allow()));

        controller.createPatientRecord(
            "patient-1",
            Map.of("diagnosis", "<script>alert('xss')</script>", "nested", Map.of("note", "<b>Urgent</b>")),
            "token"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> recordCaptor = ArgumentCaptor.forClass(Map.class);
        verify(patientService).createRecord(org.mockito.ArgumentMatchers.eq("patient-1"), recordCaptor.capture());
        assertEquals("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;", recordCaptor.getValue().get("diagnosis"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) recordCaptor.getValue().get("nested");
        assertEquals("&lt;b&gt;Urgent&lt;/b&gt;", nested.get("note"));
    }

    @Test
    void rejectsUnsafePatientIdBeforePolicyEvaluation() {
        assertThrows(IllegalArgumentException.class, () -> controller.getPatientRecords("patient 1", "token"));
    }

    @Test
    void getPatientRecordsReturnsRecordsWhenAuthorized() {
        PatientRecords records = new PatientRecords();

        when(securityManager.getCurrentContext()).thenReturn(securityContext);
        when(policyEnforcementPoint.enforce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(securityContext)))
            .thenReturn(io.activej.promise.Promise.of(PolicyEnforcementPoint.EnforcementDecision.allow()));
        when(patientService.getRecords("patient-1")).thenReturn(records);

        PatientController.Response response = controller.getPatientRecords("patient-1", "token");

        assertEquals(200, response.getStatusCode());
        assertSame(records, response.getBody());
    }

    @Test
    void createPatientRecordPropagatesServiceFailure() {
        when(securityManager.getCurrentContext()).thenReturn(securityContext);
        when(policyEnforcementPoint.enforce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(securityContext)))
            .thenReturn(io.activej.promise.Promise.of(PolicyEnforcementPoint.EnforcementDecision.allow()));
        doThrow(new IllegalStateException("Failed to create patient record because audit logging failed"))
            .when(patientService)
            .createRecord("patient-1", Map.of("diagnosis", "Hypertension"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            controller.createPatientRecord("patient-1", Map.of("diagnosis", "Hypertension"), "token")
        );

        assertTrue(exception.getMessage().contains("audit logging failed"));
    }
}
