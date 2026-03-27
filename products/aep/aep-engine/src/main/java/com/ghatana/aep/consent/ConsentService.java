/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Service for evaluating event-level consent decisions.
 *
 * <p>Implementations may delegate to external consent management platforms (e.g.
 * OneTrust, TrustArc) or apply in-process policy evaluation. The default
 * {@link DefaultConsentService} evaluates consent based on the event's own
 * {@link AepEngine.ConsentContext}.
 *
 * @doc.type interface
 * @doc.purpose Event consent evaluation with purpose-specific enforcement
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ConsentService {

    /**
     * Evaluate whether an event is allowed for processing.
     *
     * @param tenantId the owning tenant
     * @param event    the event to evaluate
     * @return promise of consent decision
     */
    Promise<ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event);

    /**
     * Return the allowed purposes for a user/purpose combination.
     *
     * @param tenantId the owning tenant
     * @param userId   the user identifier
     * @param purpose  the purpose to query
     * @return promise of allowed purposes
     */
    Promise<List<String>> getAllowedPurposes(String tenantId, String userId, String purpose);

    /**
     * Result of a consent evaluation.
     *
     * @param allowed        true if processing is permitted
     * @param reason         human-readable reason (always present)
     * @param allowedPurposes purposes the event may be used for; empty = no restriction
     * @param allowedFields  payload fields that may be processed; empty = no restriction
     */
    record ConsentDecision(
        boolean allowed,
        String reason,
        List<String> allowedPurposes,
        List<String> allowedFields
    ) {
        public ConsentDecision {
            allowedPurposes = allowedPurposes != null ? List.copyOf(allowedPurposes) : List.of();
            allowedFields   = allowedFields   != null ? List.copyOf(allowedFields)   : List.of();
        }

        /** Processing is allowed with no purpose or field restrictions. */
        public static ConsentDecision allow() {
            return new ConsentDecision(true, "Consent granted", List.of(), List.of());
        }

        /** Processing is allowed, restricted to specified purposes. */
        public static ConsentDecision allow(List<String> allowedPurposes) {
            return new ConsentDecision(true, "Consent granted with purpose restriction",
                allowedPurposes, List.of());
        }

        /** Processing is denied for the given reason. */
        public static ConsentDecision deny(String reason) {
            return new ConsentDecision(false, reason, List.of(), List.of());
        }
    }
}
