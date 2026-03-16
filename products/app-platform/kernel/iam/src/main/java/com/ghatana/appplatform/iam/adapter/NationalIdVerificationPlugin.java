/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.adapter;

import java.util.Objects;

/**
 * Port interface for T3 national ID verification plugins (STORY-K01-013).
 *
 * <p>Implementors connect to a national ID authority (government API, offline lookup,
 * etc.) and verify an individual's identity claim. The T3 plugin contract is:
 * <ul>
 *   <li>No additional network capabilities beyond the declared authority endpoint.</li>
 *   <li>Caching of positive verifications is encouraged (reduces API load).</li>
 *   <li>Offline/air-gap support via signed verification bundles where available.</li>
 * </ul>
 *
 * <p>Concrete implementations are registered in the K-04 plugin registry.
 *
 * @doc.type  interface
 * @doc.purpose T3 plugin interface for national ID verification providers (K01-013)
 * @doc.layer product
 * @doc.pattern Plugin
 */
public interface NationalIdVerificationPlugin {

    /**
     * Supported ID types declared by this plugin (e.g. {@code "NP_NATIONAL_ID"},
     * {@code "NP_PASSPORT"}).
     *
     * @return non-null, non-empty array of supported ID type codes
     */
    String[] supportedIdTypes();

    /**
     * Verifies a national ID claim.
     *
     * @param idNumber  the ID number to verify
     * @param idType    one of the types declared in {@link #supportedIdTypes()}
     * @param fullName  declared full name (used for name-matching)
     * @param dateOfBirth declared date of birth in {@code YYYY-MM-DD} format, or {@code null}
     * @return verification result (never null)
     */
    VerificationResult verify(String idNumber, String idType, String fullName, String dateOfBirth);

    // ── Result type ──────────────────────────────────────────────────────────

    /**
     * Result of a national ID verification attempt.
     *
     * @param verified   whether the identity claim was confirmed
     * @param confidence confidence score 0.0–1.0 (1.0 = certain match)
     * @param details    additional details from the authority (may be empty)
     * @param source     identifier of the authority that produced the result
     */
    record VerificationResult(
            boolean verified,
            double confidence,
            String details,
            String source
    ) {
        public VerificationResult {
            Objects.requireNonNull(details, "details");
            Objects.requireNonNull(source,  "source");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be 0.0–1.0, got: " + confidence);
            }
        }

        /** Unverified result with zero confidence. */
        public static VerificationResult unverified(String reason, String source) {
            return new VerificationResult(false, 0.0, reason, source);
        }

        /** Verified result with full confidence. */
        public static VerificationResult verified(String details, String source) {
            return new VerificationResult(true, 1.0, details, source);
        }

        /** Result with partial confidence (e.g. name match only). */
        public static VerificationResult partial(double confidence, String details, String source) {
            return new VerificationResult(confidence >= 0.5, confidence, details, source);
        }
    }
}
