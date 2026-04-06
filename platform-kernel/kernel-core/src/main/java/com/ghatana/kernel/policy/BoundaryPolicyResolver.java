package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Map;
import java.util.Set;

/**
 * Resolves boundary access policies from scope and classification metadata.
 *
 * <p>Replaces the hardcoded product allowlists used in ProductBoundaryEnforcer with
 * policy-driven evaluation that considers scope types, resource classifications,
 * and compliance requirements. Implementations must be stateless and safe for
 * concurrent use.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §5.1, boundary policies are resolved
 * from scope and classification metadata rather than product string checks.</p>
 *
 * @doc.type interface
 * @doc.purpose Policy-driven boundary access resolution
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface BoundaryPolicyResolver {

    /**
     * Resolves boundary access policy for a cross-scope access request.
     *
     * @param source         the scope requesting access
     * @param target         the scope being accessed
     * @param resource       the resource being accessed
     * @param action         the action being performed (read, write, execute)
     * @param classification classification metadata for the access context
     * @return the boundary decision
     */
    BoundaryDecision resolve(ScopeDescriptor source, ScopeDescriptor target,
                             String resource, String action,
                             ClassificationDescriptor classification);

    /**
     * Boundary decision result.
     */
    record BoundaryDecision(
            boolean allowed,
            boolean requiresConsent,
            boolean requiresAudit,
            Set<String> requiredFeatures,
            Map<String, String> decisionMetadata
    ) {
        /**
         * Creates a denial decision.
         */
        public static BoundaryDecision deny(String reason) {
            return new BoundaryDecision(false, false, false, Set.of(),
                    Map.of("reason", reason));
        }

        /**
         * Creates an approval decision.
         */
        public static BoundaryDecision approve() {
            return new BoundaryDecision(true, false, true, Set.of(), Map.of());
        }

        /**
         * Creates an approval with consent requirement.
         */
        public static BoundaryDecision approveWithConsent(Set<String> requiredFeatures) {
            return new BoundaryDecision(true, true, true, requiredFeatures, Map.of());
        }
    }
}
