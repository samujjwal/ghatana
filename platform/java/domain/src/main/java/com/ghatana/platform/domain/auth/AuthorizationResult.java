/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Detailed result of an authorization evaluation.
 *
 * <p>Encapsulates the complete outcome of an authorization decision:
 * whether access was granted, the reason, evidence used, and evaluation time.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AuthorizationResult result = evaluator.evaluate(tenantId, user, "documents", "delete");
 * if (result.isGranted()) {
 *     deleteDocument(docId);
 * } else {
 *     log.info("Access denied: {}", result.getReason());
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Authorization evaluation result with decision and rationale
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class AuthorizationResult {

    private final boolean granted;
    private final String reason;
    private final Set<String> evidence;
    private final long evaluationTimeMs;

    /**
     * Full constructor.
     *
     * @param granted whether access was granted
     * @param reason human-readable reason for the decision
     * @param evidence set of matching permissions/roles/policies
     * @param evaluationTimeMs evaluation time in milliseconds
     */
    public AuthorizationResult(boolean granted, String reason, Set<String> evidence, long evaluationTimeMs) {
        this.granted = granted;
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.evidence = Set.copyOf(Objects.requireNonNull(evidence, "evidence cannot be null"));
        this.evaluationTimeMs = evaluationTimeMs;
    }

    /**
     * Constructor without evidence.
     */
    public AuthorizationResult(boolean granted, String reason, long evaluationTimeMs) {
        this(granted, reason, Set.of(), evaluationTimeMs);
    }

    public boolean isGranted() { return granted; }
    public boolean isDenied() { return !granted; }
    public String getReason() { return reason; }
    public Set<String> getEvidence() { return evidence; }
    public long getEvaluationTimeMs() { return evaluationTimeMs; }

    /** @return true if evaluation completed within the 50ms SLA */
    public boolean isWithinSla() { return evaluationTimeMs <= 50; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean granted;
        private String reason;
        private final Set<String> evidence = new HashSet<>();
        private long evaluationTimeMs;

        public Builder granted(boolean granted) { this.granted = granted; return this; }
        public Builder reason(String reason) { this.reason = Objects.requireNonNull(reason); return this; }
        public Builder addEvidence(String item) { this.evidence.add(Objects.requireNonNull(item)); return this; }
        public Builder evaluationTimeMs(long ms) { this.evaluationTimeMs = ms; return this; }

        public AuthorizationResult build() {
            if (reason == null) throw new NullPointerException("reason must be set before building");
            return new AuthorizationResult(granted, reason, evidence, evaluationTimeMs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthorizationResult that)) return false;
        return granted == that.granted && evaluationTimeMs == that.evaluationTimeMs
                && Objects.equals(reason, that.reason) && Objects.equals(evidence, that.evidence);
    }

    @Override
    public int hashCode() { return Objects.hash(granted, reason, evidence, evaluationTimeMs); }

    @Override
    public String toString() {
        return "AuthorizationResult{granted=" + granted + ", reason='" + reason + '\''
                + ", evidenceCount=" + evidence.size() + ", evaluationTimeMs=" + evaluationTimeMs + '}';
    }
}
