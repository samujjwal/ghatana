package com.ghatana.kernel.interaction;

import java.util.Objects;

/**
 * Immutable policy decision for a product interaction request.
 *
 * @doc.type record
 * @doc.purpose Capture allow/deny result and reason for product interaction policy
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public record ProductInteractionPolicyDecision(
        boolean allowed,
        String reasonCode
) {
    public static ProductInteractionPolicyDecision allow() {
        return new ProductInteractionPolicyDecision(true, null);
    }

    public static ProductInteractionPolicyDecision denied(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank when denying a product interaction");
        }
        return new ProductInteractionPolicyDecision(false, reasonCode);
    }

    public ProductInteractionPolicyDecision {
        if (allowed && reasonCode != null && !reasonCode.isBlank()) {
            throw new IllegalArgumentException("allowed product interaction policy decisions must not include a reasonCode");
        }
        if (!allowed) {
            Objects.requireNonNull(reasonCode, "reasonCode must not be null for denied policy decisions");
            if (reasonCode.isBlank()) {
                throw new IllegalArgumentException("denied product interaction policy decisions must include a reasonCode");
            }
        }
    }
}
