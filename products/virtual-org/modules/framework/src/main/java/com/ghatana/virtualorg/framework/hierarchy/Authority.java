package com.ghatana.virtualorg.framework.hierarchy;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents decision-making authority for a role.
 *
 * <p><b>Purpose</b><br>
 * Defines which types of decisions a role can make autonomously
 * vs. which require escalation. Enables fine-grained authorization
 * for organizational workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Authority auth = Authority.builder()
 *     .addDecision("code_review")
 *     .addDecision("merge_pr")
 *     .build();
 *
 * if (auth.canDecide("code_review")) {
 *     // Agent can make this decision
 * } else {
 *     // Escalate to higher authority
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework organizational hierarchy system.
 * Used by OrganizationalAgent to determine decision authority.
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - thread-safe.
 *
 * @param decisions set of decision types this authority covers (never null)
 * @see Role
 * @see EscalationPath
 * @doc.type record
 * @doc.purpose Decision authority value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Authority(Set<String> decisions) {
    
    /**
     * Compact constructor with defensive copy.
     *
     * @param decisions set of decision types (copied to prevent external modification)
     */
    public Authority {
        decisions = Set.copyOf(decisions);
    }
    
    /**
     * Checks if this authority covers a decision type.
     *
     * @param decisionType the decision type to check
     * @return true if covered by this authority
     */
    public boolean canDecide(String decisionType) {
        return decisions.contains(decisionType);
    }
    
    /**
     * Gets the number of decisions covered by this authority.
     *
     * @return number of decision types
     */
    public int getDecisionCount() {
        return decisions.size();
    }
    
    /**
     * Checks if this authority is empty (no decisions).
     *
     * @return true if no decisions covered
     */
    public boolean isEmpty() {
        return decisions.isEmpty();
    }
    
    /**
     * Creates a builder for Authority.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for Authority.
     *
     * <p><b>Usage</b><br>
     * <pre>{@code
     * Authority auth = Authority.builder()
     *     .addDecision("code_review")
     *     .addDecision("merge_pr")
     *     .addDecision("deploy")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private final Set<String> decisions = new HashSet<>();
        
        /**
         * Adds a decision type to this authority.
         *
         * @param decisionType the decision type (not null, not empty)
         * @return this builder for chaining
         * @throws IllegalArgumentException if decisionType is null or empty
         */
        public Builder addDecision(String decisionType) {
            if (decisionType == null || decisionType.isBlank()) {
                throw new IllegalArgumentException("Decision type cannot be null or empty");
            }
            decisions.add(decisionType);
            return this;
        }
        
        /**
         * Adds multiple decision types to this authority.
         *
         * @param decisionTypes the decision types to add
         * @return this builder for chaining
         */
        public Builder addDecisions(String... decisionTypes) {
            for (String type : decisionTypes) {
                addDecision(type);
            }
            return this;
        }
        
        /**
         * Builds the Authority.
         *
         * @return new Authority with configured decisions
         */
        public Authority build() {
            return new Authority(decisions);
        }
    }
}
