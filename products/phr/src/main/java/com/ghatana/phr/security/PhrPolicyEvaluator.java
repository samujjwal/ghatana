package com.ghatana.phr.security;

import com.ghatana.phr.api.routes.PhrRouteSupport.PhrRequestContext;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kernel-backed PHI policy evaluator for PHR product.
 *
 * <p>Replaces role-based PHI access shortcuts with policy-based evaluation.
 * This evaluator integrates with consent management, treatment relationship services,
 * and emergency access workflows to provide proper policy enforcement.</p>
 *
 * <p>The decision considers:</p>
 * <ul>
 *   <li>Patient role: access only own records</li>
 *   <li>Caregiver role: access requires active consent grant</li>
 *   <li>Clinician role: access requires treatment relationship or emergency override</li>
 *   <li>Admin role: access requires audit justification and proper authorization</li>
 *   <li>FCHV role: access requires community assignment</li>
 *   <li>Emergency access: requires break-glass authorization with justification</li>
 * </ul>
 *
 * <p>Policy evaluator is now an injected service (not static) to ensure proper
 * dependency injection and fail-closed behavior when services are unavailable.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel-backed PHI policy evaluation for PHR
 * @doc.layer product
 * @doc.pattern Service
 * @since 1.0.0
 */
public final class PhrPolicyEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(PhrPolicyEvaluator.class);
    private static final String PATIENT_RECORD_RESOURCE = "patient-record";

    private final ConsentManagementService consentService;
    private final TreatmentRelationshipService treatmentRelationshipService;
    private final FchvCommunityAssignmentService fchvAssignmentService;

    /**
     * Constructs a policy evaluator with required services.
     *
     * @param consentService the consent management service (must not be null)
     * @param treatmentRelationshipService the treatment relationship service (must not be null)
     * @param fchvAssignmentService the FCHV community assignment service (must not be null)
     * @throws IllegalArgumentException if any service is null
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService) {
        this.consentService = requireNonNull(consentService, "consentService must not be null");
        this.treatmentRelationshipService = requireNonNull(treatmentRelationshipService, "treatmentRelationshipService must not be null");
        this.fchvAssignmentService = requireNonNull(fchvAssignmentService, "fchvAssignmentService must not be null");
    }

    private static <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Evaluates whether a principal can access a patient's PHI record.
     *
     * <p>This replaces the legacy role-based shortcut with policy-based evaluation.
     * The evaluation checks consent for caregivers and treatment relationships for clinicians.</p>
     *
     * <p>Policy is fail-closed: if required services are unavailable, access is denied.
     * This is a security-critical decision - we never allow access without proper validation.</p>
     *
     * @param context the PHR request context
     * @param patientId the target patient ID
     * @return Promise resolving to policy decision with detailed reason
     */
    public Promise<PolicyDecision> canAccessPatientRecordAsync(PhrRequestContext context, String patientId) {
        return canAccessPhiResourceAsync(context, patientId, PATIENT_RECORD_RESOURCE);
    }

    public Promise<PolicyDecision> canAccessPhiResourceAsync(PhrRequestContext context, String patientId, String resourceType) {
        if (context == null) {
            return Promise.of(PolicyDecision.denied("INVALID_CONTEXT", "Request context is null"));
        }

        String principalId = context.principalId();
        if (principalId == null) {
            return Promise.of(PolicyDecision.denied("MISSING_PRINCIPAL", "Principal ID is null"));
        }

        if (patientId == null || patientId.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID", "Patient ID is null or blank"));
        }
        if (resourceType == null || resourceType.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_RESOURCE_TYPE", "Resource type is null or blank"));
        }

        String role = context.role();

        // Patient role: can only access own records
        if ("patient".equals(role)) {
            boolean allowed = principalId.equals(patientId);
            return Promise.of(allowed 
                ? PolicyDecision.allowed("SELF_ACCESS", "Patient accessing own record")
                : PolicyDecision.denied("SELF_ACCESS_DENIED", "Patient can only access own record"));
        }

        // Caregiver role: requires active consent grant with proper scope
        if ("caregiver".equals(role)) {
            return consentService.validateAccess(patientId, principalId, resourceType)
                .map(result -> {
                    if (result.isAllowed()) {
                        return PolicyDecision.allowed("CAREGIVER_CONSENT_GRANTED", 
                            "Valid caregiver consent grant exists with appropriate scope");
                    }
                    return PolicyDecision.denied("CAREGIVER_CONSENT_DENIED", 
                        "No valid caregiver consent grant or insufficient scope");
                });
        }

        // Clinician role: requires treatment relationship
        if ("clinician".equals(role)) {
            return treatmentRelationshipService.hasActiveTreatmentRelationship(principalId, patientId)
                .map(hasRelationship -> hasRelationship
                    ? PolicyDecision.allowed("TREATMENT_RELATIONSHIP", "Active treatment relationship exists")
                    : PolicyDecision.denied("NO_TREATMENT_RELATIONSHIP", "No active treatment relationship"));
        }

        // Admin role: requires audit justification and proper authorization
        if ("admin".equals(role)) {
            LOG.warn("Admin PHI access denied without route-level justification. correlationId={}",
                context.correlationId());
            return Promise.of(PolicyDecision.denied("ADMIN_JUSTIFICATION_REQUIRED",
                "Admin PHI access requires explicit justification"));
        }

        // FCHV role: requires community assignment
        if ("fchv".equals(role)) {
            return fchvAssignmentService.hasCommunityAccess(principalId, patientId)
                .map(hasAccess -> hasAccess
                    ? PolicyDecision.allowed("FCHV_COMMUNITY_ACCESS", "FCHV has community assignment to patient")
                    : PolicyDecision.denied("FCHV_NO_COMMUNITY_ACCESS", "FCHV not assigned to patient's community"));
        }

        // Unknown role: fail closed
        LOG.warn("Unknown role attempted PHI access - denying. role={}, correlationId={}",
            role, context.correlationId());
        return Promise.of(PolicyDecision.denied("UNKNOWN_ROLE", "Unknown role: " + role));
    }

    /**
     * Evaluates whether emergency break-glass access is permitted.
     *
     * <p>Emergency access requires explicit justification and is fully audited.
     * This method checks if the context has the clinician or admin role.</p>
     *
     * <p>Emergency access is a security-sensitive operation that requires:
     * <ul>
     *   <li>Valid role (clinician or admin)</li>
     *   <li>Explicit justification (provided separately via emergency request flow)</li>
     *   <li>Audit logging (handled by emergency service)</li>
     *   <li>Patient notification (handled by emergency service)</li>
     * </ul></p>
     *
     * @param context the PHR request context
     * @param patientId the target patient ID
     * @param justification the emergency access justification
     * @return policy decision with detailed reason
     */
    public Promise<PolicyDecision> canAccessEmergency(PhrRequestContext context, String patientId, String justification) {
        if (context == null) {
            return Promise.of(PolicyDecision.denied("INVALID_CONTEXT", "Request context is null"));
        }

        if (patientId == null || patientId.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID", "Patient ID is null or blank"));
        }

        if (justification == null || justification.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_JUSTIFICATION", "Emergency access requires justification"));
        }

        String role = context.role();
        String principalId = context.principalId();

        // Only clinicians and admins can request emergency access
        if (!("clinician".equals(role) || "admin".equals(role))) {
            LOG.warn("Emergency access attempted by non-authorized role - denying. role={}, correlationId={}",
                role, context.correlationId());
            return Promise.of(PolicyDecision.denied("EMERGENCY_NOT_ELIGIBLE", "Only clinicians and admins can request emergency access"));
        }

        // For clinicians, verify treatment relationship (emergency override allowed)
        if ("clinician".equals(role)) {
            return treatmentRelationshipService.hasActiveTreatmentRelationship(principalId, patientId)
                .map(hasRelationship -> {
                    if (hasRelationship) {
                        return PolicyDecision.emergencyOverride("EMERGENCY_WITH_RELATIONSHIP", 
                            "Emergency access with treatment relationship - requires post-hoc justification");
                    }
                    // Emergency break-glass without treatment relationship - allowed but requires stricter audit
                    LOG.warn("Emergency break-glass without treatment relationship - allowing with strict audit. correlationId={}",
                        context.correlationId());
                    return PolicyDecision.emergencyOverride("EMERGENCY_BREAK_GLASS", 
                        "Emergency break-glass access - requires post-hoc justification and patient notification");
                });
        }

        // Admin emergency access - allowed with audit
        LOG.warn("Admin emergency access - allowing with strict audit. correlationId={}",
            context.correlationId());
        return Promise.of(PolicyDecision.emergencyOverride("ADMIN_EMERGENCY", 
            "Admin emergency access - requires post-hoc justification"));
    }

    /**
     * Evaluates whether a principal can view audit trails.
     *
     * <p>Audit trail access is restricted to admin roles.</p>
     *
     * @param context the PHR request context
     * @return policy decision with detailed reason
     */
    public PolicyDecision canViewAuditTrail(PhrRequestContext context) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }

        if ("admin".equals(context.role())) {
            return PolicyDecision.allowed("ADMIN_AUDIT_ACCESS", "Admin can view audit trails");
        }
        
        return PolicyDecision.denied("AUDIT_ACCESS_DENIED", "Only admins can view audit trails");
    }

    /**
     * Evaluates whether a principal can manage consent grants.
     *
     * <p>Consent management is restricted to patients (own consent) and
     * clinicians (with proper authorization).</p>
     *
     * @param context the PHR request context
     * @param patientId the patient whose consent is being managed
     * @return policy decision with detailed reason
     */
    public PolicyDecision canManageConsent(PhrRequestContext context, String patientId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }

        String principalId = context.principalId();
        if (principalId == null) {
            return PolicyDecision.denied("MISSING_PRINCIPAL", "Principal ID is null");
        }

        String role = context.role();

        // Patients can manage their own consent
        if ("patient".equals(role) && principalId.equals(patientId)) {
            return PolicyDecision.allowed("SELF_CONSENT_MANAGEMENT", "Patient managing own consent");
        }
        if ("patient".equals(role)) {
            return PolicyDecision.denied("CONSENT_OWNER_REQUIRED", "Patient can only manage their own consent grants");
        }

        if ("clinician".equals(role)) {
            return PolicyDecision.denied("CLINICIAN_CONSENT_MANAGEMENT_DENIED",
                "Clinician consent management requires a delegated patient authorization flow");
        }

        // Admins can manage consent
        if ("admin".equals(role)) {
            return PolicyDecision.allowed("ADMIN_CONSENT_MANAGEMENT", "Admin managing consent");
        }

        return PolicyDecision.denied("CONSENT_MANAGEMENT_DENIED", "Role not authorized to manage consent");
    }

    /**
     * Checks if a PHI field is restricted and requires explicit policy.
     *
     * <p>Restricted fields include mental health, substance use, genetic info,
     * reproductive health, HIV status, and psychiatric history. These fields
     * should never be cached, exported, or displayed without explicit policy.</p>
     *
     * @param fieldName the field name to check
     * @return true if the field is restricted
     */
    public static boolean isRestrictedField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.contains("mental") || lower.contains("psychiatric") ||
               lower.contains("substance") || lower.contains("abuse") ||
               lower.contains("genetic") || lower.contains("reproductive") ||
               lower.contains("hiv") || lower.contains("std");
    }

    /**
     * Policy decision result with detailed reason and audit metadata.
     *
     * <p>This replaces simple boolean returns to provide:
     * <ul>
     *   <li>Explicit allowed/denied status</li>
     *   <li>Machine-readable reason code</li>
     *   <li>Human-readable reason message</li>
     *   <li>Audit requirement flag</li>
     *   <li>Emergency override flag</li>
     * </ul></p>
     */
    public static final class PolicyDecision {
        private final boolean allowed;
        private final String reasonCode;
        private final String reasonMessage;
        private final boolean requiresAudit;
        private final boolean emergencyOverride;

        private PolicyDecision(boolean allowed, String reasonCode, String reasonMessage, 
                            boolean requiresAudit, boolean emergencyOverride) {
            this.allowed = allowed;
            this.reasonCode = reasonCode;
            this.reasonMessage = reasonMessage;
            this.requiresAudit = requiresAudit;
            this.emergencyOverride = emergencyOverride;
        }

        static PolicyDecision allowed(String reasonCode, String reasonMessage) {
            return new PolicyDecision(true, reasonCode, reasonMessage, false, false);
        }

        static PolicyDecision allowedWithAudit(String reasonCode, String reasonMessage) {
            return new PolicyDecision(true, reasonCode, reasonMessage, true, false);
        }

        static PolicyDecision denied(String reasonCode, String reasonMessage) {
            return new PolicyDecision(false, reasonCode, reasonMessage, false, false);
        }

        static PolicyDecision emergencyOverride(String reasonCode, String reasonMessage) {
            return new PolicyDecision(true, reasonCode, reasonMessage, true, true);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getReasonMessage() {
            return reasonMessage;
        }

        public boolean requiresAudit() {
            return requiresAudit;
        }

        public boolean isEmergencyOverride() {
            return emergencyOverride;
        }
    }
}
