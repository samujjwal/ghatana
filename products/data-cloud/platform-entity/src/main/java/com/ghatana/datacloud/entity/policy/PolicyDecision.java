package com.ghatana.datacloud.entity.policy;

import java.util.*;

/**
 * Result of a policy evaluation.
 *
 * <p><b>Purpose</b><br>
 * Represents the outcome of evaluating a policy against input data.
 * Contains allow/deny decision, reason, and optional metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyDecision allowed = PolicyDecision.allow("User has required permission");
 * PolicyDecision denied = PolicyDecision.deny("Schema change requires approval",
 *     Map.of("approval_required", true, "approvers", List.of("admin-1", "admin-2")));
 *
 * if (decision.isAllowed()) {
 *     // Proceed with operation
 * } else {
 *     logger.warn("Operation denied: {}", decision.getReason());
 *     // Optionally access decision metadata
 *     Map<String, Object> meta = decision.getMetadata();
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @doc.type record
 * @doc.purpose Policy evaluation result
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PolicyDecision(
    boolean allowed,
    String reason,
    Map<String, Object> metadata
) {
    /**
     * Creates a policy decision.
     *
     * @param allowed whether the operation is allowed
     * @param reason human-readable reason for decision (required)
     * @param metadata additional metadata (optional, can be null)
     * @throws NullPointerException if reason is null
     */
    public PolicyDecision {
        Objects.requireNonNull(reason, "Reason must not be null");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Checks if operation is allowed.
     *
     * @return true if allowed, false if denied
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Gets the decision reason.
     *
     * @return the reason (never null)
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets decision metadata.
     *
     * @return immutable metadata map (never null, may be empty)
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Creates an allowed decision.
     *
     * @param reason the reason (required)
     * @return PolicyDecision with allowed=true
     */
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, reason, Map.of());
    }

    /**
     * Creates an allowed decision with metadata.
     *
     * @param reason the reason (required)
     * @param metadata additional metadata (optional)
     * @return PolicyDecision with allowed=true
     */
    public static PolicyDecision allow(String reason, Map<String, Object> metadata) {
        return new PolicyDecision(true, reason, metadata);
    }

    /**
     * Creates a denied decision.
     *
     * @param reason the reason (required)
     * @return PolicyDecision with allowed=false
     */
    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason, Map.of());
    }

    /**
     * Creates a denied decision with metadata.
     *
     * @param reason the reason (required)
     * @param metadata additional metadata (optional)
     * @return PolicyDecision with allowed=false
     */
    public static PolicyDecision deny(String reason, Map<String, Object> metadata) {
        return new PolicyDecision(false, reason, metadata);
    }

    @Override
    public String toString() {
        return String.format("PolicyDecision{allowed=%s, reason='%s', metadata=%s}",
            allowed, reason, metadata);
    }
}
