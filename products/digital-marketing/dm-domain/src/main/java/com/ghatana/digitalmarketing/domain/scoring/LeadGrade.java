package com.ghatana.digitalmarketing.domain.scoring;

/**
 * Lead grade derived from numeric score.
 *
 * @doc.type class
 * @doc.purpose Ordinal grade for prospect prioritization in F1-012 lead scoring
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum LeadGrade {
    /** Score 80–100: strong fit, high urgency, recommend immediate proposal. */
    A,
    /** Score 60–79: good fit, moderate urgency, recommend outreach. */
    B,
    /** Score 40–59: partial fit, low urgency, nurture. */
    C,
    /** Score 0–39: poor fit, deprioritise. */
    D
}
