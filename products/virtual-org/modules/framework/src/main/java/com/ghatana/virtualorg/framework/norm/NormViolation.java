package com.ghatana.virtualorg.framework.norm;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a violation of an organizational norm.
 *
 * <p><b>Purpose</b><br>
 * Captures details about a norm violation for auditing, reporting,
 * and potential automated remediation.
 *
 * @doc.type record
 * @doc.purpose Norm violation event
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record NormViolation(
        String id,
        Norm norm,
        String agentId,
        String departmentId,
        ViolationType violationType,
        String description,
        Map<String, Object> context,
        Instant occurredAt,
        Optional<String> remediationAction
) {

    /**
     * Compact constructor with defaults.
     */
    public NormViolation {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        context = context != null ? Map.copyOf(context) : Map.of();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
        remediationAction = remediationAction != null ? remediationAction : Optional.empty();
    }

    /**
     * Creates a violation for a missed obligation.
     */
    public static NormViolation missedObligation(Norm norm, String agentId, String departmentId) {
        return new NormViolation(
                null,
                norm,
                agentId,
                departmentId,
                ViolationType.MISSED_DEADLINE,
                "Obligation '" + norm.action() + "' was not fulfilled within deadline",
                Map.of(),
                Instant.now(),
                Optional.empty()
        );
    }

    /**
     * Creates a violation for a broken prohibition.
     */
    public static NormViolation brokenProhibition(Norm norm, String agentId, String departmentId) {
        return new NormViolation(
                null,
                norm,
                agentId,
                departmentId,
                ViolationType.FORBIDDEN_ACTION,
                "Prohibited action '" + norm.action() + "' was performed",
                Map.of(),
                Instant.now(),
                Optional.empty()
        );
    }

    /**
     * Violation types.
     */
    public enum ViolationType {
        MISSED_DEADLINE,      // Obligation not fulfilled in time
        FORBIDDEN_ACTION,     // Prohibition was violated
        UNAUTHORIZED_ACTION,  // Action without permission
        CONDITION_BREACH      // Norm condition was violated
    }
}
