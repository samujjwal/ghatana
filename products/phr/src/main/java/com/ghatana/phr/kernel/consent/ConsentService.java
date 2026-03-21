package com.ghatana.phr.kernel.consent;

import com.ghatana.phr.kernel.policy.PhrDataClassification;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Mandatory runtime interface for consent-aware access decisions.
 *
 * <p>All reads and writes that touch patient data MUST pass through this
 * interface before data access. Module-local permission helpers may exist,
 * but they cannot bypass or replace this contract.</p>
 *
 * <p>The service answers one question consistently:</p>
 * <blockquote>
 *   Can this actor perform this action on this patient-scoped resource in
 *   this tenant context right now?
 * </blockquote>
 *
 * <h3>Mandatory caller points</h3>
 * <ul>
 *   <li>Route guards for direct patient resource reads</li>
 *   <li>Timeline and dashboard aggregators before fan-out</li>
 *   <li>Document download handlers before storage lookup</li>
 *   <li>Provider clinical write flows before mutation</li>
 *   <li>Insurance eligibility checks before request submission</li>
 *   <li>Audit review endpoints before result projection</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Consent-aware access-decision control point for all PHR patient data paths
 * @doc.layer product
 * @doc.pattern Service
 * @see ConsentService.ConsentCheckRequest
 * @see ConsentService.ConsentAccessDecision
 * @see ConsentAccessDeniedException
 */
public interface ConsentService {

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * The action being requested on a patient-scoped resource.
     */
    enum ConsentAction {
        PATIENT_READ, PATIENT_WRITE,
        DOCUMENT_READ, DOCUMENT_WRITE,
        MEDICATION_READ, MEDICATION_WRITE,
        TIMELINE_READ,
        INSURANCE_READ, INSURANCE_CHECK,
        AUDIT_READ,
        EMERGENCY_READ
    }

    /**
     * The reason code explaining why access was allowed or denied.
     * This code is included in audit records and deny responses.
     */
    enum ReasonCode {
        /** Actor is the patient themselves. */
        SELF_ACCESS,
        /** A valid explicit consent grant covers this access. */
        EXPLICIT_GRANT,
        /** The actor's role unconditionally allows this access. */
        ROLE_ALLOWED,
        /** Break-the-glass emergency access grant is active. */
        EMERGENCY_GRANT,
        /** A grant exists but has expired. */
        GRANT_EXPIRED,
        /** A grant exists but was explicitly revoked. */
        GRANT_REVOKED,
        /** Actor tenant does not match target patient tenant. */
        TENANT_MISMATCH,
        /** Requested action is not within the actor's allowed scopes. */
        OUT_OF_SCOPE,
        /** The resource classification requires secondary approval not present. */
        RESTRICTED_RESOURCE,
        /** System failure prevented a policy decision — fail closed. */
        SYSTEM_DENY
    }

    /** Cache hit/miss/bypass status for the access decision. */
    enum CacheStatus { HIT, MISS, BYPASS }

    /** Actor type making the access request. */
    enum ActorType { PATIENT, PROVIDER, CAREGIVER, ADMIN, FCHV }

    /** Purpose of use for the access request — drives audit verbosity. */
    enum PurposeOfUse {
        SELF_SERVICE, CARE_DELIVERY, ELIGIBILITY_CHECK,
        EMERGENCY, AUDIT_REVIEW, ASSISTED_REGISTRATION
    }

    /** Emergency category requiring secondary policy approval. */
    enum EmergencyCategory { TRAUMA, UNCONSCIOUS, MINOR_WITHOUT_GUARDIAN }

    /**
     * Reason why cache was invalidated.
     */
    enum CacheInvalidationReason {
        GRANT_CREATED, GRANT_REVOKED, GRANT_EXPIRED, DOCUMENT_VISIBILITY_CHANGED
    }

    // =========================================================================
    // Request / response records
    // =========================================================================

    /**
     * Actor context within a consent check request.
     *
     * @param actorId        unique identifier of the actor
     * @param actorType      role category of the actor
     * @param patientId      populated when actor is the patient themselves
     * @param practitionerId populated when actor is a provider/practitioner
     * @param organizationId populated when actor acts on behalf of an org
     * @param scopes         OAuth-style scopes the actor holds in this session
     */
    record ActorContext(
            String actorId,
            ActorType actorType,
            String patientId,
            String practitionerId,
            String organizationId,
            Set<String> scopes) {

        public ActorContext {
            if (actorId == null || actorId.isBlank())
                throw new IllegalArgumentException("actorId must not be blank");
            if (actorType == null)
                throw new IllegalArgumentException("actorType must not be null");
            if (scopes == null) scopes = Set.of();
        }
    }

    /**
     * Target resource descriptor within a consent check request.
     *
     * @param patientId      patient whose data is being accessed
     * @param resourceType   FHIR resource type or logical domain name
     * @param resourceId     specific resource identifier, or null for type-level checks
     * @param classification PHR data classification of the target resource
     */
    record TargetResource(
            String patientId,
            String resourceType,
            String resourceId,
            PhrDataClassification classification) {

        public TargetResource {
            if (patientId == null || patientId.isBlank())
                throw new IllegalArgumentException("patientId must not be blank");
            if (resourceType == null || resourceType.isBlank())
                throw new IllegalArgumentException("resourceType must not be blank");
            if (classification == null)
                throw new IllegalArgumentException("classification must not be null");
        }
    }

    /**
     * Emergency access context — present only when {@link PurposeOfUse#EMERGENCY} is set.
     *
     * @param enabled       whether emergency access is being claimed
     * @param justification free-text clinical justification for audit trail
     * @param category      emergency category (determines secondary-approval policy)
     */
    record EmergencyContext(
            boolean enabled,
            String justification,
            EmergencyCategory category) {}

    /**
     * Full consent check request.
     *
     * @param requestId    unique correlation identifier (UUID recommended)
     * @param tenantId     tenant that owns the patient record
     * @param actor        the actor requesting access
     * @param target       the target patient resource
     * @param action       the action being requested
     * @param purposeOfUse the stated purpose driving this access
     * @param emergency    optional emergency context; must be non-null when purpose is EMERGENCY
     */
    record ConsentCheckRequest(
            String requestId,
            String tenantId,
            ActorContext actor,
            TargetResource target,
            ConsentAction action,
            PurposeOfUse purposeOfUse,
            EmergencyContext emergency) {

        public ConsentCheckRequest {
            if (requestId == null || requestId.isBlank())
                throw new IllegalArgumentException("requestId must not be blank");
            if (tenantId == null || tenantId.isBlank())
                throw new IllegalArgumentException("tenantId must not be blank");
            if (actor == null) throw new IllegalArgumentException("actor must not be null");
            if (target == null) throw new IllegalArgumentException("target must not be null");
            if (action == null) throw new IllegalArgumentException("action must not be null");
            if (purposeOfUse == null)
                throw new IllegalArgumentException("purposeOfUse must not be null");
            if (purposeOfUse == PurposeOfUse.EMERGENCY && emergency == null)
                throw new IllegalArgumentException(
                        "emergency context must be provided when purposeOfUse is EMERGENCY");
        }
    }

    /**
     * Access decision returned by {@link #checkAccess} or {@link #assertAccess}.
     *
     * @param allowed       whether the access is permitted
     * @param reasonCode    machine-readable reason for the decision
     * @param grantId       the grant that authorised access, if applicable
     * @param cacheStatus   whether this decision was served from cache
     * @param auditRequired whether this decision must be written to the audit trail
     * @param expiresAt     when this decision expires; null means no expiry
     * @param obligations   post-access obligations the caller must fulfil
     */
    record ConsentAccessDecision(
            boolean allowed,
            ReasonCode reasonCode,
            String grantId,
            CacheStatus cacheStatus,
            boolean auditRequired,
            Instant expiresAt,
            List<String> obligations) {

        public ConsentAccessDecision {
            if (reasonCode == null)
                throw new IllegalArgumentException("reasonCode must not be null");
            if (cacheStatus == null)
                throw new IllegalArgumentException("cacheStatus must not be null");
            if (obligations == null) obligations = List.of();
        }

        /** Convenience factory for an allow decision. */
        public static ConsentAccessDecision allow(
                ReasonCode reasonCode,
                String grantId,
                CacheStatus cacheStatus,
                Instant expiresAt) {
            return new ConsentAccessDecision(
                    true, reasonCode, grantId, cacheStatus, true, expiresAt, List.of());
        }

        /** Convenience factory for a deny decision. */
        public static ConsentAccessDecision deny(
                ReasonCode reasonCode,
                CacheStatus cacheStatus) {
            return new ConsentAccessDecision(
                    false, reasonCode, null, cacheStatus, true, null, List.of());
        }
    }

    /**
     * Input to {@link #invalidatePatientAccessCache}.
     *
     * @param tenantId  tenant that owns the patient record
     * @param patientId patient whose cached decisions must be invalidated
     * @param reason    the event that triggered invalidation
     */
    record CacheInvalidationRequest(
            String tenantId,
            String patientId,
            CacheInvalidationReason reason) {}

    // =========================================================================
    // Contract methods
    // =========================================================================

    /**
     * Evaluates whether the actor may perform the requested action.
     *
     * <p>This method returns a structured allow or deny decision and never
     * throws for an expected deny path. It may throw only for system failures,
     * corrupted policy state, or unavailable dependencies.</p>
     *
     * @param request the access check request
     * @return a Promise resolving to the access decision
     */
    Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request);

    /**
     * Evaluates access and throws {@link ConsentAccessDeniedException} on deny.
     *
     * <p>This is the primary entry point for route guards and service-layer
     * access enforcement. It wraps {@link #checkAccess} and re-throws on deny,
     * always including {@code reasonCode}, {@code tenantId}, {@code actorId},
     * {@code patientId}, and {@code requestId} in the exception.</p>
     *
     * <p>Security rule: deny responses must never leak the existence of
     * tenant-external resources. Callers should map {@link ReasonCode#TENANT_MISMATCH}
     * and {@link ReasonCode#OUT_OF_SCOPE} to HTTP 404 where resource existence must
     * be concealed, and to HTTP 403 only where concealment is not required.</p>
     *
     * @param request the access check request
     * @return a Promise resolving to the allow decision
     * @throws ConsentAccessDeniedException if access is denied
     */
    Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request);

    /**
     * Invalidates all cached access decisions for the given patient in the given tenant.
     *
     * <p>Must be called on any of: grant creation, grant revocation, grant expiry,
     * or document-level visibility change.</p>
     *
     * @param request the invalidation request
     * @return a Promise completing when the cache has been cleared
     */
    Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request);
}
