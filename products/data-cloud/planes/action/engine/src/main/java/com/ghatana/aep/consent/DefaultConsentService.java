/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Default consent service that evaluates consent based on the event's own
 * {@link AepEngine.ConsentContext} with purpose-specific enforcement.
 *
 * <p>This implementation handles all four consent states:
 * <ul>
 *   <li>{@code DENIED} — always reject</li>
 *   <li>{@code EXPIRED} — reject; consent must be refreshed</li>
 *   <li>{@code GRANTED} — allow, enforce allowed-purposes list if present</li>
 *   <li>{@code UNKNOWN} — permissive default: allow with purpose enforcement</li>
 * </ul>
 *
 * <p>For production use in regulated environments, replace with an implementation
 * that delegates to an external consent management platform via the {@link ConsentProvider}
 * SPI. {@code DefaultConsentService} intentionally does NOT implement {@link ConsentProvider}
 * — it is an internal fallback, not a named SPI provider eligible for {@link java.util.ServiceLoader}
 * discovery (AEP-004).
 *
 * @doc.type class
 * @doc.purpose Default event consent evaluation using ConsentContext
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultConsentService implements ConsentService {

    /** The canonical event-processing purpose identifier. */
    public static final String EVENT_PROCESSING_PURPOSE = "event_processing";

    @Override
    public Promise<ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event) {
        AepEngine.ConsentContext consent = event.consentContext();

        // Hard deny — no further evaluation
        if (consent.status() == AepEngine.ConsentStatus.DENIED) {
            return Promise.of(ConsentDecision.deny("Consent explicitly denied"));
        }

        // Expired consent — must be re-obtained
        if (consent.status() == AepEngine.ConsentStatus.EXPIRED) {
            return Promise.of(ConsentDecision.deny("Consent has expired and must be renewed"));
        }

        // Purpose-specific enforcement when purposes are explicitly listed
        List<String> allowedPurposes = consent.allowedPurposes();
        if (!allowedPurposes.isEmpty() && !allowedPurposes.contains(EVENT_PROCESSING_PURPOSE)) {
            return Promise.of(ConsentDecision.deny(
                "Event processing is not in the allowed purposes list: " + allowedPurposes));
        }

        // GRANTED or UNKNOWN — allow with any purpose restrictions observed
        return Promise.of(ConsentDecision.allow(allowedPurposes));
    }

    @Override
    public Promise<List<String>> getAllowedPurposes(String tenantId, String userId, String purpose) {
        // Default: return the requested purpose plus the standard event-processing purpose.
        // Override with an external consent platform query for production deployments.
        return Promise.of(List.of(EVENT_PROCESSING_PURPOSE, purpose));
    }
}
