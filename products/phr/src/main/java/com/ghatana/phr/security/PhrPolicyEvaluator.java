package com.ghatana.phr.security;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.kernel.policy.KernelPolicyPlugin;
import com.ghatana.kernel.security.FieldClassificationRegistry;
import com.ghatana.phr.api.routes.PhrRouteSupport.PhrRequestContext;
import com.ghatana.phr.kernel.service.CaregiverService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    private static final Map<String, RegisteredPolicy> POLICY_REGISTRY = createPolicyRegistry();
    private static final java.util.Set<String> ALLOWED_ADMIN_PHI_PURPOSES = java.util.Set.of(
        "COMPLIANCE_AUDIT",
        "PATIENT_SUPPORT",
        "LEGAL_REQUEST",
        "SECURITY_REVIEW"
    );

    private final ConsentManagementService consentService;
    private final TreatmentRelationshipService treatmentRelationshipService;
    private final FchvCommunityAssignmentService fchvAssignmentService;
    private final AuditTrailService auditTrailService;
    private final KernelTelemetryManager telemetryManager;
    private final CaregiverService caregiverService;
    private final KernelPolicyPlugin<PolicyEvaluationRequest, Promise<PolicyDecision>> policyPlugin;

    /**
     * Constructs a policy evaluator with required services.
     *
     * @param consentService the consent management service
     * @param treatmentRelationshipService the treatment relationship service
     * @param fchvAssignmentService the FCHV community assignment service
     * @param auditTrailService the audit trail service for logging audited policy decisions (required for audited allows)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, null, (KernelTelemetryManager) null);
    }

    /**
     * Constructs a policy evaluator with required services and optional audit service.
     *
     * @param consentService the consent management service (must not be null)
     * @param treatmentRelationshipService the treatment relationship service (must not be null)
     * @param fchvAssignmentService the FCHV community assignment service (must not be null)
     * @param auditTrailService the audit trail service for logging audited policy decisions (required for audited allows)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     * @throws IllegalArgumentException if any required service is null
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, auditTrailService, (KernelTelemetryManager) null);
    }

    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService,
            CaregiverService caregiverService) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, auditTrailService, null, caregiverService);
    }

    /**
     * Constructs a policy evaluator with required services and optional audit/telemetry services.
     *
     * @param consentService the consent management service (must not be null)
     * @param treatmentRelationshipService the treatment relationship service (must not be null)
     * @param fchvAssignmentService the FCHV community assignment service (must not be null)
     * @param auditTrailService the audit trail service for logging audited policy decisions (required for audited allows)
     * @param telemetryManager the telemetry manager for metrics (may be null)
     * @throws IllegalArgumentException if any required service is null
     */
    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService,
            KernelTelemetryManager telemetryManager) {
        this(consentService, treatmentRelationshipService, fchvAssignmentService, auditTrailService, telemetryManager, null);
    }

    public PhrPolicyEvaluator(
            ConsentManagementService consentService,
            TreatmentRelationshipService treatmentRelationshipService,
            FchvCommunityAssignmentService fchvAssignmentService,
            AuditTrailService auditTrailService,
            KernelTelemetryManager telemetryManager,
            CaregiverService caregiverService) {
        this.consentService = consentService;
        this.treatmentRelationshipService = treatmentRelationshipService;
        this.fchvAssignmentService = fchvAssignmentService;
        this.auditTrailService = auditTrailService;
        this.telemetryManager = telemetryManager; // Optional - null is allowed
        this.caregiverService = caregiverService;
        this.policyPlugin = createPolicyPlugin();
    }

    private KernelPolicyPlugin<PolicyEvaluationRequest, Promise<PolicyDecision>> createPolicyPlugin() {
        KernelPolicyPlugin.Builder<PolicyEvaluationRequest, Promise<PolicyDecision>> builder =
            KernelPolicyPlugin.<PolicyEvaluationRequest, Promise<PolicyDecision>>builder()
                .unknownPolicyProvider(this::unknownPolicyDecision);

        POLICY_REGISTRY.forEach((policyId, policy) -> builder.register(policyId, request -> switch (policy.category()) {
            case UI, SYSTEM -> evaluateUiPolicy(request);
            case PHI -> evaluatePhiPolicy(request);
            case EMERGENCY -> evaluateEmergencyPolicy(request);
            case ADMIN -> evaluateAdminPolicy(request);
            case HIDDEN -> evaluateHiddenRoutePolicy(request);
        }));
        return builder.build();
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
    private PolicyDecision emitAuditEventIfNeeded(PolicyDecision decision, PhrRequestContext context,
                                         String resourceType, String action, String patientId) {
        return emitAuditEventIfNeeded(decision, context, resourceType, action, patientId, Map.of());
    }

    private PolicyDecision emitAuditEventIfNeeded(PolicyDecision decision, PhrRequestContext context,
                                         String resourceType, String action, String patientId,
                                         Map<String, Object> additionalData) {
        if (!decision.requiresAudit() && !decision.isEmergencyOverride()) {
            return decision;
        }

        if (auditTrailService == null) {
            LOG.error("Audited PHI policy decision denied because audit service is unavailable. reasonCode={}, correlationId={}",
                decision.getReasonCode(), context != null ? context.correlationId() : "unknown");
            return PolicyDecision.denied("AUDIT_SERVICE_UNAVAILABLE",
                "Audit service is required for this PHI access");
        }

        try {
            String eventType = decision.isEmergencyOverride() ? "EMERGENCY_OVERRIDE" : "POLICY_AUDIT";
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("reasonCode", decision.getReasonCode());
            data.put("reasonMessage", decision.getReasonMessage());
            data.put("resourceType", resourceType != null ? resourceType : "unknown");
            data.put("action", action != null ? action : "unknown");
            data.put("patientId", patientId != null ? patientId : "unknown");
            data.put("isEmergencyOverride", decision.isEmergencyOverride());
            data.put("requiresAudit", decision.requiresAudit());
            data.putAll(additionalData);

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
            return decision;
        } catch (Exception e) {
            LOG.error("Failed to emit audit event for policy decision. reasonCode={}, correlationId={}",
                decision.getReasonCode(), context.correlationId(), e);
            return PolicyDecision.denied("AUDIT_WRITE_FAILED",
                "Audit event could not be recorded for this PHI access");
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
                "reason_code", decision.getReasonCode() != null ? decision.getReasonCode() : "POLICY_DENIED"
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
                "reason_code", decision.getReasonCode() != null ? decision.getReasonCode() : "UNKNOWN"
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
                "success", String.valueOf(success)
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
            return Promise.of(emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId));
        }

        // Caregiver role: requires active consent grant with proper scope
        if ("caregiver".equals(role)) {
            if (consentService == null) {
                return Promise.of(PolicyDecision.denied("CONSENT_SERVICE_UNAVAILABLE",
                    "Consent service is unavailable"));
            }
            if (caregiverService == null) {
                return Promise.of(PolicyDecision.denied("CAREGIVER_RELATIONSHIP_SERVICE_UNAVAILABLE",
                    "Caregiver relationship service is unavailable"));
            }
            return caregiverService.getPatientsForCaregiver(principalId)
                .then(relationships -> {
                    boolean hasRelationshipScope = relationships.stream()
                        .filter(CaregiverService.CaregiverRelationship::isActive)
                        .filter(relationship -> patientId.equals(relationship.patientId()))
                        .anyMatch(relationship -> relationship.consentScope() != null
                            && (relationship.consentScope().contains(resourceType)
                                || relationship.consentScope().contains("*")));
                    if (!hasRelationshipScope) {
                        return Promise.of(PolicyDecision.denied("CAREGIVER_RELATIONSHIP_REQUIRED",
                            "Caregiver PHI access requires an active patient relationship with matching scope"));
                    }
                    return consentService.validateAccess(patientId, principalId, resourceType, normalizedAction)
                        .map(result -> {
                            PolicyDecision decision = result.isAllowed()
                                ? PolicyDecision.allowedWithAudit("CAREGIVER_CONSENT_GRANTED",
                                    "Valid caregiver relationship and consent grant exist with appropriate scope")
                                : PolicyDecision.denied("CAREGIVER_CONSENT_DENIED",
                                    "No valid caregiver consent grant or insufficient scope");
                            return emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                        });
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
                        return emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
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
                        return Promise.of(emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId));
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
                            return emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                        });
                });
        }

        // Admin role: requires audit justification and proper authorization
        if ("admin".equals(role)) {
            LOG.warn("Admin PHI access denied without route-level justification. correlationId={}",
                context.correlationId());
            PolicyDecision decision = PolicyDecision.denied("ADMIN_JUSTIFICATION_REQUIRED",
                "Admin PHI access requires explicit justification");
            return Promise.of(emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId));
        }

        // FCHV role: requires community assignment
        if ("fchv".equals(role)) {
            if (context.facilityId() == null || context.facilityId().isBlank()) {
                return Promise.of(PolicyDecision.denied("FCHV_FACILITY_SCOPE_REQUIRED",
                    "FCHV PHI access requires facility-scoped community assignment context"));
            }
            if (fchvAssignmentService == null) {
                return Promise.of(PolicyDecision.denied("FCHV_ASSIGNMENT_SERVICE_UNAVAILABLE",
                    "FCHV community assignment service is unavailable"));
            }
            return fchvAssignmentService.hasCommunityAccess(principalId, patientId)
                .map(hasAccess -> {
                    PolicyDecision decision = hasAccess
                        ? PolicyDecision.allowedWithAudit("FCHV_COMMUNITY_ACCESS", "FCHV has community assignment to patient")
                        : PolicyDecision.denied("FCHV_NO_COMMUNITY_ACCESS", "FCHV not assigned to patient's community");
                    return emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId);
                });
        }

        // Unknown role: fail closed
        LOG.warn("Unknown role attempted PHI access - denying. role={}, correlationId={}",
            role, context.correlationId());
        PolicyDecision decision = PolicyDecision.denied("UNKNOWN_ROLE", "Unknown role: " + role);
        return Promise.of(emitAuditEventIfNeeded(decision, context, resourceType, normalizedAction, patientId));
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
        return canAccessPhiWithAdminJustification(context, patientId, resourceType, action, null, justification);
    }

    public Promise<PolicyDecision> canAccessPhiWithAdminJustification(
            PhrRequestContext context,
            String patientId,
            String resourceType,
            String action,
            String purpose,
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

        if (purpose == null || purpose.isBlank()) {
            return Promise.of(PolicyDecision.denied("ADMIN_PURPOSE_REQUIRED",
                "Admin PHI access requires an explicit support, compliance, legal, or security purpose"));
        }
        String normalizedPurpose = purpose.strip().toUpperCase();
        if (!ALLOWED_ADMIN_PHI_PURPOSES.contains(normalizedPurpose)) {
            return Promise.of(PolicyDecision.denied("ADMIN_PURPOSE_NOT_ALLOWED",
                "Admin PHI access purpose is not authorized"));
        }

        String normalizedAction = action != null ? action.strip().toUpperCase() : "UNKNOWN";

        String justificationHash = hashJustification(justification);
        LOG.warn("Admin PHI access with protected justification reference - allowing with strict audit. correlationId={}, purpose={}, justificationHash={}",
            context.correlationId(), normalizedPurpose, justificationHash);

        PolicyDecision decision = PolicyDecision.allowedWithAudit("ADMIN_JUSTIFIED_PHI_ACCESS",
            "Admin PHI access with explicit justification");
        PolicyDecision auditedDecision = emitAuditEventIfNeeded(
            decision,
            context,
            resourceType,
            normalizedAction,
            patientId,
            Map.of(
                "purpose", normalizedPurpose,
                "justificationHash", justificationHash,
                "justificationCaptured", true,
                "postAccessReviewRequired", true,
                "postAccessReviewType", "ADMIN_PHI_ACCESS"
            )
        );
        return Promise.of(auditedDecision);
    }

    private static String hashJustification(String justification) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(justification.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    static Set<String> registeredPolicyIdsForTest() {
        return POLICY_REGISTRY.keySet();
    }

    private static Map<String, RegisteredPolicy> createPolicyRegistry() {
        Map<String, RegisteredPolicy> policies = new LinkedHashMap<>();
        register(policies, "phr.dashboard.view", PolicyCategory.UI);
        register(policies, "phr.settings.access", PolicyCategory.UI);
        register(policies, "phr.notifications.access", PolicyCategory.UI);
        register(policies, "phr.forbidden.access", PolicyCategory.SYSTEM);
        register(policies, "phr.not.found.access", PolicyCategory.SYSTEM);

        register(policies, "phr.records.view", PolicyCategory.PHI);
        register(policies, "phr.consents.access", PolicyCategory.PHI);
        register(policies, "phr.appointments.access", PolicyCategory.PHI);
        register(policies, "phr.labs.access", PolicyCategory.PHI);
        register(policies, "phr.medications.access", PolicyCategory.PHI);
        register(policies, "phr.conditions.access", PolicyCategory.PHI);
        register(policies, "phr.observations.access", PolicyCategory.PHI);
        register(policies, "phr.immunizations.access", PolicyCategory.PHI);
        register(policies, "phr.documents.access", PolicyCategory.PHI);
        register(policies, "phr.documents.upload.access", PolicyCategory.PHI);
        register(policies, "phr.documents.doc-id.ocr.access", PolicyCategory.PHI);
        register(policies, "phr.timeline.access", PolicyCategory.PHI);
        register(policies, "phr.profile.access", PolicyCategory.PHI);
        register(policies, "phr.records.record-id.access", PolicyCategory.PHI);

        register(policies, "phr.emergency.break-glass", PolicyCategory.EMERGENCY);
        register(policies, adminPolicy(
            "phr.emergency.review",
            "ADMIN_EMERGENCY_REVIEW",
            "Admin can review emergency access requests"));
        register(policies, adminPolicy(
            "phr.release.readiness.access",
            "ADMIN_RELEASE_READINESS",
            "Admin can view release readiness"));
        register(policies, adminPolicy(
            "phr.audit.access",
            "ADMIN_AUDIT_ACCESS",
            "Admin can view audit trails"));

        register(policies, "phr.provider.dashboard.view", PolicyCategory.HIDDEN);
        register(policies, "phr.provider.patients.view", PolicyCategory.HIDDEN);
        register(policies, "phr.caregiver.dependents.view", PolicyCategory.HIDDEN);
        register(policies, "phr.fchv.dashboard.view", PolicyCategory.HIDDEN);
        return Map.copyOf(policies);
    }

    private static RegisteredPolicy adminPolicy(String policyId, String reasonCode, String reasonMessage) {
        return new RegisteredPolicy(policyId, PolicyCategory.ADMIN, reasonCode, reasonMessage);
    }

    private static void register(Map<String, RegisteredPolicy> policies, String policyId, PolicyCategory category) {
        register(policies, new RegisteredPolicy(policyId, category, null, null));
    }

    private static void register(Map<String, RegisteredPolicy> policies, RegisteredPolicy policy) {
        if (policies.putIfAbsent(policy.policyId(), policy) != null) {
            throw new IllegalStateException("Duplicate PHR policy registration: " + policy.policyId());
        }
    }

    private enum PolicyCategory {
        UI,
        SYSTEM,
        PHI,
        EMERGENCY,
        ADMIN,
        HIDDEN
    }

    private record RegisteredPolicy(
        String policyId,
        PolicyCategory category,
        String allowReasonCode,
        String allowReasonMessage
    ) {
        private RegisteredPolicy {
            if (policyId == null || policyId.isBlank()) {
                throw new IllegalArgumentException("policyId cannot be blank");
            }
            if (category == null) {
                throw new IllegalArgumentException("policy category cannot be null");
            }
        }
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
                    PolicyDecision auditedDecision = emitAuditEventIfNeeded(
                        decision, context, "patient-record", "EMERGENCY_ACCESS", patientId);
                    emitEmergencyAccessMetrics(auditedDecision, context, patientId);
                    return auditedDecision;
                });
        }

        // Admin emergency access - allowed with audit
        LOG.warn("Admin emergency access - allowing with strict audit. correlationId={}",
            context.correlationId());
        PolicyDecision decision = PolicyDecision.emergencyOverride("ADMIN_EMERGENCY",
            "Admin emergency access - requires post-hoc justification");
        PolicyDecision auditedDecision = emitAuditEventIfNeeded(
            decision, context, "patient-record", "EMERGENCY_ACCESS", patientId);
        emitEmergencyAccessMetrics(auditedDecision, context, patientId);
        return Promise.of(auditedDecision);
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
        return policyPlugin.evaluate(policyId, new PolicyEvaluationRequest(policyId, context, patientId, justification));
    }

    private Promise<PolicyDecision> evaluateUiPolicy(PolicyEvaluationRequest request) {
        return Promise.of(evaluateUiAccess(request.context(), request.policyId()));
    }

    private Promise<PolicyDecision> evaluatePhiPolicy(PolicyEvaluationRequest request) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID",
                "Patient ID required for policy: " + request.policyId()));
        }
        if (request.context() == null) {
            return Promise.of(PolicyDecision.denied("INVALID_CONTEXT", "Request context is null"));
        }
        return canAccessPhiResourceAsync(
            request.context(), request.patientId(), "phi-resource", "READ",
            request.context().tenantId(), request.context().facilityId()
        );
    }

    private Promise<PolicyDecision> evaluateEmergencyPolicy(PolicyEvaluationRequest request) {
        if (request.patientId() == null || request.patientId().isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_PATIENT_ID",
                "Patient ID required for emergency access"));
        }
        return canAccessEmergency(request.context(), request.patientId(), request.justification());
    }

    private Promise<PolicyDecision> evaluateAdminPolicy(PolicyEvaluationRequest request) {
        RegisteredPolicy policy = POLICY_REGISTRY.get(request.policyId());
        if (policy == null || policy.category() != PolicyCategory.ADMIN) {
            return Promise.of(PolicyDecision.denied("UNKNOWN_ADMIN_POLICY",
                "Unknown admin policy: " + request.policyId()));
        }
        return Promise.of(evaluateAdminAccess(request.context(), policy));
    }

    private Promise<PolicyDecision> evaluateHiddenRoutePolicy(PolicyEvaluationRequest request) {
        return Promise.of(PolicyDecision.denied("HIDDEN_ROUTE_NOT_MOUNTED",
            "Policy is registered for a hidden route that is not mounted: " + request.policyId()));
    }

    private Promise<PolicyDecision> unknownPolicyDecision(String policyId, PolicyEvaluationRequest request) {
        if (policyId == null || policyId.isBlank()) {
            return Promise.of(PolicyDecision.denied("MISSING_POLICY_ID", "Policy ID is required"));
        }
        LOG.warn("Unknown policyId requested - denying. policyId={}, correlationId={}",
            policyId, request != null && request.context() != null ? request.context().correlationId() : "unknown");
        return Promise.of(PolicyDecision.denied("UNKNOWN_POLICY_ID",
            "Unknown policy ID: " + policyId));
    }

    private record PolicyEvaluationRequest(
        String policyId,
        PhrRequestContext context,
        String patientId,
        String justification
    ) {}

    private PolicyDecision evaluateUiAccess(PhrRequestContext context, String policyId) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }

        return switch (context.role()) {
            case "patient", "caregiver", "clinician", "admin", "fchv" ->
                PolicyDecision.allowed("UI_ROUTE_ACCESS", "Role can access UI route for policy: " + policyId);
            default -> PolicyDecision.denied("ROLE_NOT_ALLOWED", "Role not authorized for policy: " + policyId);
        };
    }

    /**
     * Evaluates admin-only access for specific policies.
     *
     * @param context the PHR request context
     * @param policyId the policy ID being evaluated
     * @return policy decision
     */
    private PolicyDecision evaluateAdminAccess(PhrRequestContext context, RegisteredPolicy policy) {
        if (context == null) {
            return PolicyDecision.denied("INVALID_CONTEXT", "Request context is null");
        }

        if (!"admin".equals(context.role())) {
            return PolicyDecision.denied("ADMIN_REQUIRED",
                "Admin role required for policy: " + policy.policyId());
        }

        if (policy.allowReasonCode() == null || policy.allowReasonMessage() == null) {
            return PolicyDecision.denied("ADMIN_POLICY_NOT_EVALUABLE",
                "Admin policy is missing allow metadata: " + policy.policyId());
        }
        return PolicyDecision.allowedWithAudit(policy.allowReasonCode(), policy.allowReasonMessage());
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
            return PolicyDecision.denied("AUDIT_SCOPE_ASYNC_AUTH_REQUIRED",
                "Clinician audit queries require treatment relationship or consent authorization");
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
     * Evaluates whether a principal can query audit events, including async PHI-scope checks.
     *
     * @param context the PHR request context
     * @param patientId optional patient scope requested by the caller
     * @return policy decision promise
     */
    public Promise<PolicyDecision> canQueryAuditEventsAsync(PhrRequestContext context, String patientId) {
        PolicyDecision baseDecision = canQueryAuditEvents(context, patientId);
        if (context == null || !"clinician".equals(context.role())) {
            return Promise.of(baseDecision);
        }
        if (patientId == null || patientId.isBlank()) {
            return Promise.of(baseDecision);
        }
        return canAccessPhiResourceAsync(
            context,
            patientId,
            "audit",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).map(scopeDecision -> scopeDecision.isAllowed()
            ? PolicyDecision.allowedWithAudit("CLINICIAN_AUDIT_QUERY",
                "Clinician can query patient-scoped audit events with treatment relationship or consent")
            : scopeDecision);
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
            return PolicyDecision.denied("AUDIT_SCOPE_ASYNC_AUTH_REQUIRED",
                "Clinician audit detail access requires treatment relationship or consent authorization");
        }
        if ("patient".equals(context.role())
                && (context.principalId().equals(eventUserId) || context.principalId().equals(eventEntityId))) {
            return PolicyDecision.allowed("SELF_AUDIT_DETAIL", "Patient can view their own audit detail");
        }
        return PolicyDecision.denied("AUDIT_EVENT_ACCESS_DENIED", "Role not authorized to view this audit event");
    }

    /**
     * Evaluates whether a principal can view one audit event, including async PHI-scope checks.
     *
     * @param context the PHR request context
     * @param eventUserId the user ID recorded on the event
     * @param eventEntityId the entity ID recorded on the event
     * @return policy decision promise
     */
    public Promise<PolicyDecision> canViewAuditEventAsync(PhrRequestContext context, String eventUserId, String eventEntityId) {
        PolicyDecision baseDecision = canViewAuditEvent(context, eventUserId, eventEntityId);
        if (context == null || !"clinician".equals(context.role())) {
            return Promise.of(baseDecision);
        }
        if (eventEntityId == null || eventEntityId.isBlank()) {
            return Promise.of(baseDecision);
        }
        return canAccessPhiResourceAsync(
            context,
            eventEntityId,
            "audit",
            "READ",
            context.tenantId(),
            context.facilityId()
        ).map(scopeDecision -> scopeDecision.isAllowed()
            ? PolicyDecision.allowedWithAudit("CLINICIAN_AUDIT_DETAIL",
                "Clinician can view patient-scoped audit detail with treatment relationship or consent")
            : scopeDecision);
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
