/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.plugin.consent.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a subject's consent is revoked or withdrawn.
 *
 * <p>Products should subscribe to the topic {@code "consent.revoked"} on the
 * {@link com.ghatana.platform.plugin.PluginInteractionBus} to receive these events
 * and propagate the revocation to domain-specific access controls.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * pluginContext.getInteractionBus().subscribe("consent.revoked", event -> {
 *     ConsentRevocationEvent revoked = (ConsentRevocationEvent) event;
 *     // invalidate cached access decisions for revoked.subjectId() + revoked.purpose()
 * });
 * }</pre>
 *
 * @param consentId   the ID of the revoked consent record
 * @param subjectId   the data subject whose consent was revoked
 * @param purpose     the purpose for which consent was revoked
 * @param reason      the revocation reason (EXPLICIT_REVOCATION or WITHDRAWAL)
 * @param revokedAt   the timestamp at which the revocation was recorded
 *
 * @doc.type class
 * @doc.purpose Cross-product event signalling consent revocation or withdrawal
 * @doc.layer platform
 * @doc.pattern Event
 * @since 1.1.0
 */
public record ConsentRevocationEvent(
        String consentId,
        String subjectId,
        String purpose,
        RevocationReason reason,
        Instant revokedAt
) {
    /**
     * Bus topic that subscribers must use to receive consent revocation events.
     */
    public static final String TOPIC = "consent.revoked";

    /**
     * Reason for the consent revocation.
     */
    public enum RevocationReason {
        /** The subject explicitly called {@code revokeConsent(consentId)}. */
        EXPLICIT_REVOCATION,
        /** The subject withdrew consent via {@code recordConsent(..., WITHDRAW)}. */
        WITHDRAWAL
    }

    public ConsentRevocationEvent {
        Objects.requireNonNull(consentId, "consentId cannot be null");
        Objects.requireNonNull(subjectId, "subjectId cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(revokedAt, "revokedAt cannot be null");
    }
}
