package com.ghatana.aep.pattern.spec;

import java.util.Map;

/**
 * Typed model for PatternSpec governance section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern governance
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternGovernance(
        String commitSha,
        String approvalPolicy,
        String reviewPolicy,
        Map<String, Object> options) {

    public Map<String, Object> toMap() {
        return Map.of(
            "commitSha", commitSha != null ? commitSha : "",
            "approvalPolicy", approvalPolicy != null ? approvalPolicy : "",
            "reviewPolicy", reviewPolicy != null ? reviewPolicy : "",
            "options", options != null ? options : Map.of());
    }
}
