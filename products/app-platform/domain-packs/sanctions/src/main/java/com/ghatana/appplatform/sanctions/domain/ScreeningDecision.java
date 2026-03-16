package com.ghatana.appplatform.sanctions.domain;

/**
 * @doc.type    Enum
 * @doc.purpose Decision classification based on match confidence score (D14-003).
 * <ul>
 *   <li>AUTO_BLOCK  — score ≥ 0.95: immediate block, no human review needed</li>
 *   <li>HIGH        — 0.85 ≤ score < 0.95: requires compliance review</li>
 *   <li>MEDIUM      — 0.70 ≤ score < 0.85: requires review</li>
 *   <li>LOW         — score < 0.70: auto-dismissed, no block</li>
 * </ul>
 * @doc.layer   Domain
 */
public enum ScreeningDecision {
    AUTO_BLOCK,
    HIGH,
    MEDIUM,
    LOW;

    public static ScreeningDecision fromScore(double score) {
        if (score >= 0.95) return AUTO_BLOCK;
        if (score >= 0.85) return HIGH;
        if (score >= 0.70) return MEDIUM;
        return LOW;
    }

    public boolean requiresBlock() {
        return this == AUTO_BLOCK;
    }

    public boolean requiresReview() {
        return this == HIGH || this == MEDIUM;
    }
}
