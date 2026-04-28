/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Readiness Report
 */
package com.ghatana.yappc.services.lifecycle.assessment;

import java.util.List;

/**
 * Immutable readiness assessment result for a YAPPC lifecycle phase transition.
 *
 * <p>Contains whether the project is ready to advance, the complete list of blockers
 * that must be resolved before the transition is allowed, and AI-generated
 * recommendations for how to address each blocker quickly.
 *
 * @param ready           {@code true} iff all gates are satisfied and the transition may proceed
 * @param fromPhase       phase the project is transitioning from
 * @param toPhase         phase the project is transitioning to
 * @param clarityScore    AI-estimated requirements clarity score [0.0, 1.0]; {@code -1.0} if
 *                        not evaluated (AI unavailable)
 * @param blockers        human-readable list of issues blocking the transition (empty = ready)
 * @param recommendations AI-generated suggestions for resolving blockers
 * @param assessmentNote  free-text summary (e.g., the AI reasoning or a hard-gate description)
 *
 * @doc.type class
 * @doc.purpose Immutable readiness assessment result for a lifecycle phase transition
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReadinessReport(
        boolean ready,
        String fromPhase,
        String toPhase,
        double clarityScore,
        List<String> blockers,
        List<String> recommendations,
        String assessmentNote) {

    /**
     * Creates a ReadinessReport indicating all gates passed.
     *
     * @param fromPhase    current phase
     * @param toPhase      requested target phase
     * @param clarityScore AI clarity score ([0.0, 1.0])
     * @param note         optional short description of the assessment
     */
    public static ReadinessReport ready(
            String fromPhase,
            String toPhase,
            double clarityScore,
            String note) {
        return new ReadinessReport(true, fromPhase, toPhase, clarityScore,
                List.of(), List.of(), note);
    }

    /**
     * Creates a ReadinessReport indicating the transition is blocked.
     *
     * @param fromPhase       current phase
     * @param toPhase         requested target phase
     * @param clarityScore    AI clarity score or {@code -1.0} if not evaluated
     * @param blockers        human-readable list of unmet gates
     * @param recommendations AI suggestions for unblocking
     * @param note            optional short description
     */
    public static ReadinessReport blocked(
            String fromPhase,
            String toPhase,
            double clarityScore,
            List<String> blockers,
            List<String> recommendations,
            String note) {
        return new ReadinessReport(false, fromPhase, toPhase, clarityScore,
                List.copyOf(blockers), List.copyOf(recommendations), note);
    }
}
