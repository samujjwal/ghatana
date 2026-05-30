package com.ghatana.phr.security;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.kernel.security.FieldClassificationRegistry;
import com.ghatana.phr.api.routes.PhrRouteSupport.PhrRequestContext;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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
    private final AuditTrailService auditTrailService;
    private final KernelTelemetryManager telemetryManager;

    /**
     * Constructs a policy evaluator with required services.
     *
     * @param consentService the consent management service
     * @param treatmentRelationshipService the treatment relationship service
     * @param fchvAssignmentService the FCHV community assignment service
     * @param auditTrailService the audit trail service for logging policy decisions (may be null)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, null, null);
    }

    /**
     * Constructs a policy evaluator with required services and optional audit service.
     *
     * @param consentService the consent management service (must not be null)
     * @param treatmentRelationshipService the treatment relationship service (must not be null)
     * @param fchvAssignmentService the FCHV community assignment service (must not be null)
     * @param auditTrailService the audit trail service for logging policy decisions (may be null)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     * @throws IllegalArgumentException if any required service is null
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, auditTrailService, null);
    }

    /**
     * Constructs a policy evaluator with required services and optional audit/telemetry services.
     *
     * @param consentService the consent management service (must not be null)
     * @param treatmentRelationshipService the treatment relationship service (must not be null)
     * @param fchvAssignmentService the FCHV community assignment service (must not be null)
     * @param auditTrailService the audit trail service for logging policy decisions (may be null)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     * @throws IllegalArgumentException if any required service is null
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService,
            KernelTelemetryManager telemetryManager) {
        this.consentService = consentService;
        this.treatmentRelationshipService = treatmentRelationshipService;
        this.fchvAssignmentService = fchvAssignmentService;
        this.auditTrailService = auditTrailService; // Optional - null is allowed
        this.telemetryManager = telemetryManager; // Optional - null is allowed
    }

    /**
     * Emits an audit event for policy decisions that require audit or are emergency overrides.
     *
     * <p>G3-011: Emit audit event whenever PolicyDecision.requiresAudit() or isEmergencyOverride() is true.</p>
     *
     * @param decision the policy decision
     * @param context the request context
     * @param resourceType the resource type being accessed
     * @param action the action being performed
     * @param patientId the target patient ID (if applicable)
     */
    private void emitAuditEventIfNeeded(PolicyDecision decision, PhrRequestContext context,
                                         String resourceType, String action, String patientId) {
        if (auditTrailService == null) {
            return; // No audit service configured
        }

        if (!decision.requiresAudit() && !decision.isEmergencyOverride()) {
            return; // No audit required
        }

        try {
            String eventType = decision.isEmergencyOverride() ? "EMERGENCY_OVERRIDE" : "POLICY_AUDIT";
            Map<String, Object> data = Map.of(
                "reasonCode", decision.getReasonCode(),
                "reasonMessage", decision.getReasonMessage(),
                "resourceType", resourceType != null ? resourceType : "unknown",
                "action", action != null ? action : "unknown",
                "patientId", patientId != null ? patientId : "unknown",
                "isEmergencyOverride", decision.isEmergencyOverride(),
                "requiresAudit", decision.requiresAudit()
            );

            AuditTrailService.AuditTrailEvent event = AuditTrailService.AuditTrailEvent.builder()
                .eventType(eventType)
                .entityId(patientId != null ? patientId : context.principalId())
                .userId(context.principalId())
                .tenantId(context.tenantId())
                .action(action != null ? action : "POLICY_EVALUATION")
                .data(data)
                .build();

            auditTrailService.recordAuditEvent(event);
            LOG.info("Audit event emitted for policy decision. eventType={}, reasonCode={}, correlationId={}",
                eventType, decision.getReasonCode(), context.correlationId());
        } catch (Exception e) {
            LOG.error("Failed to emit audit event for policy decision. reasonCode={}, correlationId={}",
                decision.getReasonCode(), context.correlationId(), e);
        }
    }

    /**
     * Emits policy denied metrics without PHI.
     *
     * <p>G11-T04: Add policy denied metrics without PHI.</p>
     *
     * @param decision the policy decision
     * @param context the request context
     * @param resourceType the resource type being accessed
     */
    private void emitPolicyDeniedMetrics(PolicyDecision decision, PhrRequestContext context, String resourceType) {
        if (telemetryManager == null) {
            return; // No telemetry service configured
        }

        try {
            // Emit counter for policy denial without PHI
            telemetryManager.incrementCounter(
                "phr.policy.denied",
                1,
                "resource_type", resourceType != null ? resourceType : "unknown",
                "role", context.role(),
                "reason_code", decision.getReasonCode() != null ? decision.getReasonCode() : "POLICY_DENIED",
                "tenant_id", context.tenantId()
            );
            LOG.debug("Policy denied metric emitted. resourceType={}, reasonCode={}, correlationId={}",
                resourceType, decision.getReasonCode(), context.correlationId());
        } catch (Exception e) {
            LOG.error("Failed to emit policy denied metric. resourceType={}, correlationId={}",
                resourceType, context.correlationId(), e);
        }
    }

    /**
     * Emits emergency access metrics without PHI.
     *
     * <p>G11-T05: Add emergency access metrics without PHI.</p>
     *
     * @param decision the policy decision
     * @param context the request context
     * @param patientId the target patient ID (used only for metric tag, not logged)
     */
    private void emitEmergencyAccessMetrics(PolicyDecision decision, PhrRequestContext context, String patientId) {
        if (telemetryManager == null) {
            return; // No telemetry service configured
        }

        try {
            // Emit counter for emergency access request without PHI
            telemetryManager.incrementCounter(
                "phr.emergency.access",
                1,
                "role", context.role(),
                "decision", decision.isAllowed() ? "allowed" : "denied",
                "reason_code", decision.getReasonCode() != null ? decision.getReasonCode() : "UNKNOWN",
                "tenant_id", context.tenantId()
            );
            LOG.debug("Emergency access metric emitted. decision={}, reasonCode={}, correlationId={}",
                decision.isAllowed(), decision.getReasonCode(), context.correlationId());
        } catch (Exception e) {
            LOG.error("Failed to emit emergency access metric. correlationId={}",
                context.correlationId(), e);
        }
    }

    /**
     * Emits consent operation metrics without PHI.
     *
     * <p>G11-T06: Add consent create/revoke/check metrics without PHI.</p>
     *
     * @param operation the consent operation (create, revoke, check)
     * @param context the request context
     * @param success whether the operation succeeded
     */
    private void emitConsentMetrics(String operation, PhrRequestContext context, boolean success) {
        if (telemetryManager == null) {
            return; // No telemetry service configured
        }

        try {
            // Emit counter for consent operation without PHI
            telemetryManager.incrementCounter(
                "phr.consent.operation",
                1,
                "operation", operation,
                "role", context.role(),
                "success", String.valueOf(success),
                "tenant_id", context.tenantId()
            );
            LOG.debug("Consent operation metric emitted. operation={}, success={}, correlationId={}",
                operation, success, context.correlationId());
        } catch (Exception e) {
            LOG.error("Failed to emit consent operation metric. operation={}, correlationId={}",
                operation, context.correlationId(), e);
        }
    }

    /**
     * Evaluates whether a principal can access a patient's PHI record.
     *
     * <p>The evaluation checks consent for caregivers and treatment relationships
     * for clinicians.</p>
     *
     * <p>Policy is fail-closed: if required services are unavailable, access is denied.
     * This is a security-critical decision - we never allow access without proper validation.</p>
     *
     * @param context the PHR request context
     * @param patientId the target patient ID
     * @return Promise resolving to policy decision with detailed reason
     */
    public Promise<PolicyDecision> canAccessPatientRecordAsync(PhrRequestContext context, String patientId) {
        return canAccessPhiResourceAsync(
            context,
            patientId,
            PATIENT_RECORD_RESOURCE,
            "READ",
            context != null ? context.tenantId() : null,
            context != null ? context.facilityId() : null
        );
    }

    public Promise<PolicyDecision> canAccessPhiResourceAsync(
            PhrRequestContext context,
            String patientId,
            String resourceType,
            String action,
            String tenantId,
            String facilityId) {
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
        if (action == null || action.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_ACTION", "Action is null or blank"));
        }
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_TENANT", "Tenant ID is null or blank"));
        }
        if (!context.tenantId().equals(tenantId)) {
            return Promise.of(PolicyDecision.denied("TENANT_SCOPE_MISMATCH", "Requested tenant does not match request context"));
        }
        if (facilityId != null && !facilityId.isBlank()
                && context.facilityId() != null && !context.facilityId().equals(facilityId)) {
            return Promise.of(PolicyDecision.denied("FACILITY_SCOPE_MISMATCH", "Requested facility does not match request context"));
        }

        String role = context.role();
        String normalizedAction = action.strip().toUpperCase();

        // Patient role: can only access own records
        if ("patient".equals(role)) {
            boolean allowed = principalId.equals(patientId);
            PolicyDecision decision = allowed
                ? PolicyDecision.allowedWithAudit("SELF_ACCESS", "Patient accessing own record")
                : PolicyDecision.denied("SELF_ACCESS_DENIED", "Patient can only access own record");
            emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
            return Promise.of(decision);
        }

        // Caregiver role: requires active consent grant with proper scope
        if ("caregiver".equals(role)) {
            if (consentService == null) {
                return Promise.of(PolicyDecision.denied("CONSENT_SERVICE_UNAVAILABLE",
                    "Consent service is unavailable"));
            }
            return consentService.validateAccess(patientId, principalId, resourceType, normalizedAction)
                .map(result -> {
                    PolicyDecision decision = result.isAllowed()
                        ? PolicyDecision.allowedWithAudit("CAREGIVER_CONSENT_GRANTED",
                            "Valid caregiver consent grant exists with appropriate scope")
                        : PolicyDecision.denied("CAREGIVER_CONSENT_DENIED",
                            "No valid caregiver consent grant or insufficient scope");
                    emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                    return decision;
                });
        }

        // Clinician role: requires treatment relationship
        if ("clinician".equals(role)) {
            if (context.facilityId() == null || context.facilityId().isBlank()) {
                if (consentService == null) {
                    return Promise.of(PolicyDecision.denied("CONSENT_SERVICE_UNAVAILABLE",
                        "Consent service is unavailable"));
                }
                return consentService.validateAccess(patientId, principalId, resourceType, normalizedAction)
                    .map(result -> {
                        PolicyDecision decision = result.isAllowed()
                            ? PolicyDecision.allowedWithAudit("CLINICIAN_CONSENT_GRANTED",
                                "Valid clinician consent grant exists with appropriate scope")
                            : PolicyDecision.denied("CLINICIAN_SCOPE_REQUIRED",
                                "Clinician PHI access requires facility-scoped treatment relationship or explicit consent");
                        emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                        return decision;
                    });
            }
            if (treatmentRelationshipService == null) {
                return Promise.of(PolicyDecision.denied("TREATMENT_RELATIONSHIP_SERVICE_UNAVAILABLE",
                    "Treatment relationship service is unavailable"));
            }
            return treatmentRelationshipService.hasActiveTreatmentRelationship(principalId, patientId)
                .then(hasRelationship -> {
                    if (hasRelationship) {
                        PolicyDecision decision = PolicyDecision.allowedWithAudit(
                            "CLINICIAN_TREATMENT_RELATIONSHIP",
                            "Active treatment relationship exists");
                        emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                        return Promise.of(decision);
                    }
                    if (consentService == null) {
                        return Promise.of(PolicyDecision.denied("CONSENT_SERVICE_UNAVAILABLE",
                            "Consent service is unavailable"));
                    }
                    return consentService.validateAccess(patientId, principalId, resourceType, normalizedAction)
                        .map(result -> {
                            PolicyDecision decision = result.isAllowed()
                                ? PolicyDecision.allowedWithAudit("CLINICIAN_CONSENT_GRANTED",
                                    "Valid clinician consent grant exists with appropriate scope")
                                : PolicyDecision.denied("CLINICIAN_SCOPE_REQUIRED",
                                    "Clinician PHI access requires facility-scoped treatment relationship or explicit consent");
                            emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                            return decision;
                        });
                });
        }

        // Admin role: requires audit justification and proper authorization
        if ("admin".equals(role)) {
            LOG.warn("Admin PHI access denied without route-level justification. correlationId={}",
                context.correlationId());
            PolicyDecision decision = PolicyDecision.denied("ADMIN_JUSTIFICATION_REQUIRED",
                "Admin PHI access requires explicit justification");
            emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
            return Promise.of(decision);
        }

        // FCHV role: requires community assignment
        if ("fchv".equals(role)) {
            if (fchvAssignmentService == null) {
                return Promise.of(PolicyDecision.denied("FCHV_ASSIGNMENT_SERVICE_UNAVAILABLE",
                    "FCHV community assignment service is unavailable"));
            }
            return fchvAssignmentService.hasCommunityAccess(principalId, patientId)
                .map(hasAccess -> {
                    PolicyDecision decision = hasAccess
                        ? PolicyDecision.allowedWithAudit("FCHV_COMMUNITY_ACCESS", "FCHV has community assignment to patient")
                        : PolicyDecision.denied("FCHV_NO_COMMUNITY_ACCESS", "FCHV not assigned to patient's community");
                    emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                    return decision;
                });
        }

        // Unknown role: fail closed
        LOG.warn("Unknown role attempted PHI access - denying. role={}, correlationId={}",
            role, context.correlationId());
        PolicyDecision decision = PolicyDecision.denied("UNKNOWN_ROLE", "Unknown role: " + role);
        emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
        return Promise.of(decision);
    }

    /**
     * Evaluates whether an admin can access PHI with explicit justification.
     *
     * <p>Admin PHI access requires explicit justification and is fully audited.
     * This is a security-sensitive operation that should only be used for legitimate
     * administrative purposes (e.g., compliance audits, patient support).</p>
     *
     * @param context the PHR request context
     * @param patientId the target patient ID
     * @param resourceType the resource type being accessed
     * @param action the action being performed
     * @param justification the admin justification for PHI access
     * @return Promise resolving to policy decision
     */
    public Promise<PolicyDecision> canAccessPhiWithAdminJustification(
            PhrRequestContext context,
            String patientId,
            String resourceType,
            String action,
            String justification) {
        if (context == null) {
            return Promise.of(PolicyDecision.denied("INVALID_CONTEXT", "Request context is null"));
        }

        if (!"admin".equals(context.role())) {
            return Promise.of(PolicyDecision.denied("ADMIN_REQUIRED",
                "Admin justification path is only available to admin role"));
        }

        if (justification == null || justification.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_JUSTIFICATION",
                "Admin PHI access requires explicit justification"));
        }

        if (justification.length() < 20) {
            return Promise.of(PolicyDecision.denied("JUSTIFICATION_TOO_SHORT",
                "Admin justification must be at least 20 characters"));
        }

        String normalizedAction = action != null ? action.strip().toUpperCase() : "UNKNOWN";

        LOG.warn("Admin PHI access with justification - allowing with strict audit. correlationId={}, justification={}",
            context.correlationId(), justification.substring(0, Math.min(50, justification.length())));

        PolicyDecision decision = PolicyDecision.allowedWithAudit("ADMIN_JUSTIFIED_PHI_ACCESS",
            "Admin PHI access with explicit justification: " + justification);
        emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
        return Promise.of(decision);
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
                    PolicyDecision decision = hasRelationship
                        ? PolicyDecision.emergencyOverride("EMERGENCY_WITH_RELATIONSHIP",
                            "Emergency access with treatment relationship - requires post-hoc justification")
                        : PolicyDecision.emergencyOverride("EMERGENCY_BREAK_GLASS",
                            "Emergency break-glass access - requires post-hoc justification and patient notification");
                    if (!hasRelationship) {
                        LOG.warn("Emergency break-glass without treatment relationship - allowing with strict audit. correlationId={}",
                            context.correlationId());
                    }
                    emitAuditEventIfNeeded(decision, context, "patient-record", "EMERGENCY_ACCESS", patientId);
                    emitEmergencyAccessMetrics(decision, context, patientId);
                    return decision;
                });
        }

        // Admin emergency access - allowed with audit
        LOG.warn("Admin emergency access - allowing with strict audit. correlationId={}",
            context.correlationId());
        PolicyDecision decision = PolicyDecision.emergencyOverride("ADMIN_EMERGENCY",
            "Admin emergency access - requires post-hoc justification");
        emitAuditEventIfNeeded(decision, context, "patient-record", "EMERGENCY_ACCESS", patientId);
        emitEmergencyAccessMetrics(decision, context, patientId);
        return Promise.of(decision);
    }

    /**
     * Evaluates policy based on route contract policyId.
     *
     * <p>This dispatch layer maps policyIds from the route contract to the appropriate
     * evaluation logic. This enables policy decisions to be driven by the canonical
     * route contract rather than hardcoded in route adapters.</p>
     *
     * <p>Supported policyIds include:</p>
     * <ul>
     *   <li>phr.dashboard.view - Dashboard access</li>
     *   <li>phr.records.view - Patient records access</li>
     *   <li>phr.consents.access - Consent management access</li>
     *   <li>phr.appointments.access - Appointment access</li>
     *   <li>phr.settings.access - Settings access</li>
     *   <li>phr.labs.access - Lab results access</li>
     *   <li>phr.medications.access - Medication access</li>
     *   <li>phr.conditions.access - Conditions access</li>
     *   <li>phr.observations.access - Observations access</li>
     *   <li>phr.immunizations.access - Immunization access</li>
     *   <li>phr.documents.access - Document access</li>
     *   <li>phr.timeline.access - Timeline access</li>
     *   <li>phr.profile.access - Profile access</li>
     *   <li>phr.notifications.access - Notifications access</li>
     *   <li>phr.emergency.break-glass - Emergency break-glass access</li>
     *   <li>phr.emergency.review - Emergency review access</li>
     *   <li>phr.release.readiness.access - Release readiness access</li>
     *   <li>phr.audit.access - Audit trail access</li>
     * </ul>
     *
     * @param policyId the policy ID from the route contract
     * @param context the PHR request context
     * @param patientId the target patient ID (if applicable)
     * @param justification emergency access justification (if applicable)
     * @return Promise resolving to policy decision
     */
    public Promise<PolicyDecision> evaluateByPolicyId(String policyId, PhrRequestContext context,
                                                        String patientId, String justification) {
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_POLICY_ID", "Policy ID is required"));
        }

        // Dispatch based on policyId
        switch (policyId) {
            case "phr.dashboard.view":
            case "phr.records.view":
            case "phr.consents.access":
            case "phr.appointments.access":
            case "phr.settings.access":
            case "phr.labs.access":
            case "phr.medications.access":
            case "phr.conditions.access":
            case "phr.observations.access":
            case "phr.immunizations.access":
            case "phr.documents.access":
            case "phr.timeline.access":
            case "phr.profile.access":
            case "phr.notifications.access":
                // PHI resource access - requires patientId
                if (patientId == null || patientId.isBlank()) {
                    return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID",
                        "Patient ID required for policy: " + policyId));
                }
                return canAccessPhiResourceAsync(
                    context, patientId, "phi-resource", "READ",
                    context.tenantId(), context.facilityId()
                );

            case "phr.emergency.break-glass":
                // Emergency access - requires patientId and justification
                if (patientId == null || patientId.isBlank()) {
                    return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID",
                        "Patient ID required for emergency access"));
                }
                return canAccessEmergency(context, patientId, justification);

            case "phr.emergency.review":
            case "phr.release.readiness.access":
            case "phr.audit.access":
                // Admin-only access - no patientId required
                return Promise.of(evaluateAdminAccess(context, policyId));

            default:
                LOG.warn("Unknown policyId requested - denying. policyId={}, correlationId={}",
                    policyId, context != null ? context.correlationId() : "unknown");
                return Promise.of(PolicyDecision.denied("UNKNOWN_POLICY_ID",
                    "Unknown policy ID: " + policyId));
        }
    }

    /**
     * Evaluates admin-only access for specific policies.
     *
     * @param context the PHR request context
     * @param policyId the policy ID being evaluated
     * @return policy decision
     */
    private PolicyDecision evaluateAdminAccess(PhrRequestContext context, String policyId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }

        if (!"admin".equals(context.role())) {
            return PolicyDecision.denied("ADMIN_REQUIRED",
                "Admin role required for policy: " + policyId);
        }

        switch (policyId) {
            case "phr.emergency.review":
                return PolicyDecision.allowedWithAudit("ADMIN_EMERGENCY_REVIEW",
                    "Admin can review emergency access requests");
            case "phr.release.readiness.access":
                return PolicyDecision.allowedWithAudit("ADMIN_RELEASE_READINESS",
                    "Admin can view release readiness");
            case "phr.audit.access":
                return PolicyDecision.allowedWithAudit("ADMIN_AUDIT_ACCESS",
                    "Admin can view audit trails");
            default:
                return PolicyDecision.denied("UNKNOWN_ADMIN_POLICY",
                    "Unknown admin policy: " + policyId);
        }
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
     * Evaluates whether a principal can query audit events for the requested patient scope.
     *
     * @param context the PHR request context
     * @param patientId optional patient scope requested by the caller
     * @return policy decision with detailed reason
     */
    public PolicyDecision canQueryAuditEvents(PhrRequestContext context, String patientId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }
        if ("admin".equals(context.role())) {
            return PolicyDecision.allowedWithAudit("ADMIN_AUDIT_QUERY", "Admin can query audit events");
        }
        if ("clinician".equals(context.role())) {
            if (patientId == null || patientId.isBlank()) {
                return PolicyDecision.denied("AUDIT_PATIENT_SCOPE_REQUIRED",
                    "Clinician audit queries require an explicit patient scope");
            }
            return PolicyDecision.allowedWithAudit("CLINICIAN_AUDIT_QUERY", "Clinician can query patient-scoped audit events");
        }
        if ("patient".equals(context.role())) {
            if (patientId == null || patientId.isBlank() || context.principalId().equals(patientId)) {
                return PolicyDecision.allowed("SELF_AUDIT_QUERY", "Patient can query their own audit events");
            }
            return PolicyDecision.denied("AUDIT_SELF_SCOPE_REQUIRED", "Patient can only query their own audit events");
        }
        return PolicyDecision.denied("AUDIT_ACCESS_DENIED", "Role not authorized to query audit events");
    }

    /**
     * Evaluates whether a principal can view one audit event after it has been fetched.
     *
     * @param context the PHR request context
     * @param eventUserId the user ID recorded on the event
     * @param eventEntityId the entity ID recorded on the event
     * @return policy decision with detailed reason
     */
    public PolicyDecision canViewAuditEvent(PhrRequestContext context, String eventUserId, String eventEntityId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }
        if ("admin".equals(context.role())) {
            return PolicyDecision.allowedWithAudit("ADMIN_AUDIT_DETAIL", "Admin can view audit event detail");
        }
        if ("clinician".equals(context.role())) {
            if (eventEntityId == null || eventEntityId.isBlank()) {
                return PolicyDecision.denied("AUDIT_PATIENT_SCOPE_REQUIRED",
                    "Clinician audit detail access requires a patient-scoped event");
            }
            return PolicyDecision.allowedWithAudit("CLINICIAN_AUDIT_DETAIL", "Clinician can view patient-scoped audit detail");
        }
        if ("patient".equals(context.role())
                && (context.principalId().equals(eventUserId) || context.principalId().equals(eventEntityId))) {
            return PolicyDecision.allowed("SELF_AUDIT_DETAIL", "Patient can view their own audit detail");
        }
        return PolicyDecision.denied("AUDIT_EVENT_ACCESS_DENIED", "Role not authorized to view this audit event");
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
     * Evaluates whether a principal can check a consent grant for a patient/accessor pair.
     *
     * @param context the PHR request context
     * @param patientId the patient whose consent grant is being checked
     * @param accessorId the principal whose access is being checked
     * @return policy decision with detailed reason
     */
    public PolicyDecision canCheckConsent(PhrRequestContext context, String patientId, String accessorId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }
        if (patientId == null || patientId.isBlank()) {
            return PolicyDecision.denied("MISSING_PATIENT_ID", "Patient ID is null or blank");
        }
        if (accessorId == null || accessorId.isBlank()) {
            return PolicyDecision.denied("MISSING_ACCESSOR_ID", "Accessor ID is null or blank");
        }

        String principalId = context.principalId();
        if (principalId == null || principalId.isBlank()) {
            return PolicyDecision.denied("MISSING_PRINCIPAL", "Principal ID is null or blank");
        }

        String role = context.role();
        if ("patient".equals(role)) {
            return principalId.equals(patientId)
                ? PolicyDecision.allowed("SELF_CONSENT_CHECK", "Patient checking consent for own record")
                : PolicyDecision.denied("CONSENT_OWNER_REQUIRED", "Patient can only check consent for their own record");
        }
        if ("admin".equals(role)) {
            return PolicyDecision.allowedWithAudit("ADMIN_CONSENT_CHECK", "Admin checking consent grant");
        }
        if (principalId.equals(accessorId)
                && ("clinician".equals(role) || "caregiver".equals(role) || "fchv".equals(role))) {
            return PolicyDecision.allowed("ACCESSOR_CONSENT_CHECK", "Accessor checking their own consent grant");
        }

        return PolicyDecision.denied("CONSENT_CHECK_DENIED", "Role not authorized to check this consent grant");
    }

    /**
     * Checks if a PHI field is restricted and requires explicit policy.
     *
     * <p>Restricted fields include mental health, substance use, genetic info,
     * reproductive health, HIV status, and psychiatric history. These fields
     * should never be cached, exported, or displayed without explicit policy.</p>
     *
     * <p>G3-015: Uses canonical FieldClassificationRegistry instead of substring matching.</p>
     *
     * @param fieldName the field name to check
     * @return true if the field is restricted
     */
    public static boolean isRestrictedField(String fieldName) {
        return FieldClassificationRegistry.isRestricted(fieldName);
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

        public static PolicyDecision allowed(String reasonCode, String reasonMessage) {
            return new PolicyDecision(true, reasonCode, reasonMessage, false, false);
        }

        public static PolicyDecision allowedWithAudit(String reasonCode, String reasonMessage) {
            return new PolicyDecision(true, reasonCode, reasonMessage, true, false);
        }

        public static PolicyDecision denied(String reasonCode, String reasonMessage) {
            return new PolicyDecision(false, reasonCode, reasonMessage, false, false);
        }

        public static PolicyDecision emergencyOverride(String reasonCode, String reasonMessage) {
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
