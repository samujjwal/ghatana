package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;

/**
 * Kernel interaction broker port for PHR consent status checks.
 *
 * <p>This interface represents the Kernel's product interaction broker for the
 * {@code kernel://interactions/phr.consent-status.v1} contract. Digital Marketing
 * does not import PHR internals directly; instead, it uses this broker interface
 * which is wired through the Kernel's product interaction broker system.</p>
 *
 * @doc.type interface
 * @doc.purpose Provide Kernel-brokered access to PHR consent status checks
 * @doc.layer product
 * @doc.pattern Port
 */
public interface ConsentInteractionBroker {

    /**
     * Checks PHR consent status through the Kernel product interaction broker.
     *
     * <p>This method routes the consent check through the Kernel's product interaction
     * broker, which enforces policy (auth, tenant, purpose, consent, PII classification)
     * and generates interaction evidence. The actual consent decision is made by the PHR
     * product's consent handler, not by Digital Marketing.</p>
     *
     * @param ctx operation context carrying tenant/workspace and actor identity
     * @param request consent check request with tenant, subject, actor, and purpose
     * @return promise resolving to consent decision with granted status and evidence reference
     */
    Promise<ConsentDecision> checkConsentStatus(DmOperationContext ctx, ConsentCheckRequest request);

    /**
     * Consent check request for Kernel-brokered PHR consent status.
     *
     * @param tenantId tenant identifier for the consent check
     * @param subjectId subject/patient identifier for the consent check
     * @param actorId actor identifier requesting the consent check
     * @param purpose consent purpose (e.g., "campaign-activation", "consent-verification")
     * @param requestedAt timestamp when the consent check was requested
     */
    record ConsentCheckRequest(
        String tenantId,
        String subjectId,
        String actorId,
        String purpose,
        Instant requestedAt
    ) {
        /**
         * Validates consent check request invariants at construction time.
         */
        public ConsentCheckRequest {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (subjectId == null || subjectId.isBlank()) {
                throw new IllegalArgumentException("subjectId must not be blank");
            }
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("actorId must not be blank");
            }
            if (purpose == null || purpose.isBlank()) {
                throw new IllegalArgumentException("purpose must not be blank");
            }
            if (requestedAt == null) {
                throw new IllegalArgumentException("requestedAt must not be null");
            }
        }
    }

    /**
     * Consent decision returned by the Kernel-brokered PHR consent check.
     *
     * @param consentGranted whether consent was granted for the requested purpose
     * @param consentStatus detailed consent status (granted, denied, unknown, expired)
     * @param evaluatedAt timestamp when the consent decision was evaluated
     * @param evidenceRef reference to the interaction evidence record
     */
    record ConsentDecision(
        boolean consentGranted,
        String consentStatus,
        Instant evaluatedAt,
        String evidenceRef
    ) {
        /**
         * Validates consent decision invariants at construction time.
         */
        public ConsentDecision {
            if (consentStatus == null || consentStatus.isBlank()) {
                throw new IllegalArgumentException("consentStatus must not be blank");
            }
            if (evaluatedAt == null) {
                throw new IllegalArgumentException("evaluatedAt must not be null");
            }
            if (evidenceRef == null || evidenceRef.isBlank()) {
                throw new IllegalArgumentException("evidenceRef must not be blank");
            }
        }
    }
}
