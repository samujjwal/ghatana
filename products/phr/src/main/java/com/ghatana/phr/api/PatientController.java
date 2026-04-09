package com.ghatana.phr.api;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.phr.kernel.service.PhrInputSanitizationUtils;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.service.PatientService;

import java.util.HashMap;
import java.util.Map;

/**
 * Component for PatientController
 *
 * @doc.type class
 * @doc.purpose Component for PatientController
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatientController {
    private final PolicyEnforcementPoint policyEnforcementPoint;
    private final KernelSecurityManager securityManager;
    private final PatientService patientService;

    public PatientController(PolicyEnforcementPoint policyEnforcementPoint,
                            KernelSecurityManager securityManager,
                            PatientService patientService) {
        this.policyEnforcementPoint = policyEnforcementPoint;
        this.securityManager = securityManager;
        this.patientService = patientService;
    }

    public Response getPatientRecords(String patientId, String authToken) {
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        SecurityContext context = securityManager.getCurrentContext();

        if (context == null) {
            return Response.error(401, "Not authenticated");
        }

        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder()
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .dataType("patient-health-records")
            .purpose("treatment")
            .requiresConsent(true)
            .metadata(Map.of("patient_id", sanitizedPatientId))
            .build();

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context);

        if (!decision.isAllowed()) {
            return Response.error(403, decision.getReason());
        }

        PatientRecords records = patientService.getRecords(sanitizedPatientId);
        return Response.success(records);
    }

    public Response createPatientRecord(String patientId, Map<String, Object> recordData, String authToken) {
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        Map<String, Object> sanitizedRecordData = PhrInputSanitizationUtils.sanitizeStructuredData(recordData, "recordData");
        SecurityContext context = securityManager.getCurrentContext();

        if (context == null) {
            return Response.error(401, "Not authenticated");
        }

        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder()
            .resource("patient-records")
            .operation("create")
            .scope("phr")
            .dataType("patient-health-records")
            .purpose("treatment")
            .requiresConsent(true)
            .metadata(Map.of("patient_id", sanitizedPatientId))
            .build();

        PolicyEnforcementPoint.EnforcementDecision decision =
            policyEnforcementPoint.enforce(request, context);

        if (!decision.isAllowed()) {
            return Response.error(403, decision.getReason());
        }

        patientService.createRecord(sanitizedPatientId, sanitizedRecordData);
        return Response.success(Map.of("message", "Record created successfully"));
    }

    public static class Response {
        private final int statusCode;
        private final Object body;
        private final boolean success;

        private Response(int statusCode, Object body, boolean success) {
            this.statusCode = statusCode;
            this.body = body;
            this.success = success;
        }

        public static Response success(Object body) {
            return new Response(200, body, true);
        }

        public static Response error(int statusCode, String message) {
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("error", message);
            return new Response(statusCode, errorBody, false);
        }

        public int getStatusCode() { return statusCode; }
        public Object getBody() { return body; }
        public boolean isSuccess() { return success; }
    }
}
