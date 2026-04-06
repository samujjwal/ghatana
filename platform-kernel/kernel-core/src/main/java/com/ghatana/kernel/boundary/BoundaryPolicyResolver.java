package com.ghatana.kernel.boundary;

import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;

/**
 * Resolves boundary access policies from scope and classification metadata.
 *
 * <p>Replaces the hardcoded product allowlists in {@link ProductBoundaryEnforcer}
 * with a policy-driven contract. Implementations determine whether a given
 * source scope can perform a given action on a resource in a target scope,
 * based on scope types, classification, and compliance metadata rather than
 * product id strings.</p>
 *
 * @doc.type interface
 * @doc.purpose Policy contract for scope-aware boundary enforcement
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface BoundaryPolicyResolver {

    /**
     * Resolves a boundary access decision.
     *
     * @param source         the scope requesting access
     * @param target         the scope being accessed
     * @param resource       the resource being accessed
     * @param action         the action being performed (e.g., "read", "write", "execute")
     * @param classification classification metadata for the access context
     * @return the access decision
     */
    BoundaryDecision resolve(ScopeDescriptor source, ScopeDescriptor target,
                             String resource, String action,
                             ClassificationDescriptor classification);

    /**
     * An access decision from boundary policy evaluation.
     *
     * @param allowed          whether the access is permitted
     * @param requiresAudit    whether an audit trail is required for this access
     * @param requiresConsent  whether explicit consent is needed
     * @param denialReason     reason for denial (null if allowed)
     */
    record BoundaryDecision(
            boolean allowed,
            boolean requiresAudit,
            boolean requiresConsent,
            String denialReason
    ) {
        public static BoundaryDecision allow(boolean requiresAudit) {
            return new BoundaryDecision(true, requiresAudit, false, null);
        }

        public static BoundaryDecision allowWithConsent(boolean requiresAudit) {
            return new BoundaryDecision(true, requiresAudit, true, null);
        }

        public static BoundaryDecision deny(String reason) {
            return new BoundaryDecision(false, false, false, reason);
        }
    }
}
