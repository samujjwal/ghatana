package com.ghatana.digitalmarketing.domain.content;

/**
 * Segments of a Google Search responsive search ad content version.
 *
 * <p>Each segment corresponds to a distinct content block generated
 * deterministically by the ad copy generator. Google Ads platform limits:
 * headlines max 30 chars each, descriptions max 90 chars each.</p>
 *
 * @doc.type class
 * @doc.purpose Enumerates the required segments of a Google Search ad copy content version.
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
public enum GoogleAdCopySection {

    /** Up to 15 headline variants (≤30 chars each) for responsive search ads. */
    HEADLINES,

    /** Up to 4 description variants (≤90 chars each). */
    DESCRIPTIONS,

    /** Themed keyword groups with match types. */
    KEYWORD_THEMES,

    /** Negative keyword suggestions to reduce irrelevant spend. */
    NEGATIVE_KEYWORDS,

    /** Call-to-action phrase aligned with landing page. */
    CALL_TO_ACTION,

    /**
     * Compliance notes, including forbidden-claim flags and disclosure requirements.
     * Must be reviewed before ad activation.
     */
    COMPLIANCE_NOTES
}
