package com.ghatana.digitalmarketing.bridge;

/**
 * Event payload for PHR consent revocation.
 *
 * @doc.type record
 * @doc.purpose Event payload for PHR consent revocation events
 * @doc.layer digital-marketing
 * @doc.pattern Event
 */
public record ConsentRevokeEvent(
        String subjectId,
        String consentId,
        String status,
        String reasonCode
) {}
