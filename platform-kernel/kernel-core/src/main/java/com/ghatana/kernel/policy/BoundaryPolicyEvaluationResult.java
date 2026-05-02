package com.ghatana.kernel.policy;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The result of boundary policy rule evaluation.
 *
 * <p>Carries the matched rule's effect, denial reasoning, consent and audit flags,
 * required features, and decision metadata. This enriched result allows callers to
 * log, audit, and act on the decision without re-deriving context from the raw
 * decision flag.</p>
 *
 * @doc.type class
 * @doc.purpose Enriched boundary policy evaluation result
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyEvaluationResult {

    private final boolean allowed;
    private final boolean requiresConsent;
    private final boolean requiresAudit;
    private final boolean requiresApproval;
    private final String denialReason;
    private final Set<String> requiredFeatures;
    private final String matchedRuleId;
    private final Map<String, String> decisionMetadata;

    private BoundaryPolicyEvaluationResult(Builder builder) {
        this.allowed = builder.allowed;
        this.requiresConsent = builder.requiresConsent;
        this.requiresAudit = builder.requiresAudit;
        this.requiresApproval = builder.requiresApproval;
        this.denialReason = builder.denialReason;
        this.requiredFeatures = builder.requiredFeatures != null ? Set.copyOf(builder.requiredFeatures) : Set.of();
        this.matchedRuleId = builder.matchedRuleId;
        this.decisionMetadata = builder.decisionMetadata != null ? Map.copyOf(builder.decisionMetadata) : Map.of();
    }

    public boolean isAllowed() { return allowed; }
    public boolean isRequiresConsent() { return requiresConsent; }
    public boolean isRequiresAudit() { return requiresAudit; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public String getDenialReason() { return denialReason; }
    public Set<String> getRequiredFeatures() { return requiredFeatures; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public Map<String, String> getDecisionMetadata() { return decisionMetadata; }

    /** Factory: access denied with explicit reason. */
    public static BoundaryPolicyEvaluationResult deny(String reason, String matchedRuleId) {
        return new Builder()
                .allowed(false)
                .denialReason(Objects.requireNonNull(reason, "reason cannot be null"))
                .matchedRuleId(matchedRuleId)
                .requiresAudit(true)
                .build();
    }

    /** Factory: access denied because no rule matched (default-deny). */
    public static BoundaryPolicyEvaluationResult defaultDeny(BoundaryPolicyEvaluationRequest request) {
        return new Builder()
                .allowed(false)
                .denialReason("No matching allow rule for " + request.getSource().getScopeId()
                        + " → " + request.getTarget().getScopeId()
                        + " [" + request.getResource() + ":" + request.getAction() + "]")
                .requiresAudit(false)
                .build();
    }

    /** Factory: access allowed without consent or approval requirement. */
    public static BoundaryPolicyEvaluationResult allow(String matchedRuleId,
                                                        boolean requiresAudit,
                                                        Set<String> requiredFeatures) {
        return new Builder()
                .allowed(true)
                .matchedRuleId(matchedRuleId)
                .requiresAudit(requiresAudit)
                .requiredFeatures(requiredFeatures)
                .build();
    }

    /** Factory: access allowed but consent is required. */
    public static BoundaryPolicyEvaluationResult allowWithConsent(String matchedRuleId,
                                                                   Set<String> requiredFeatures) {
        return new Builder()
                .allowed(true)
                .requiresConsent(true)
                .requiresAudit(true)
                .matchedRuleId(matchedRuleId)
                .requiredFeatures(requiredFeatures)
                .build();
    }

    /** Factory: access requires explicit approval workflow. */
    public static BoundaryPolicyEvaluationResult requireApproval(String matchedRuleId) {
        return new Builder()
                .allowed(false)
                .requiresApproval(true)
                .matchedRuleId(matchedRuleId)
                .requiresAudit(true)
                .denialReason("Access requires explicit approval via approval workflow")
                .build();
    }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link BoundaryPolicyEvaluationResult}. */
    public static final class Builder {
        private boolean allowed;
        private boolean requiresConsent;
        private boolean requiresAudit;
        private boolean requiresApproval;
        private String denialReason;
        private Set<String> requiredFeatures;
        private String matchedRuleId;
        private Map<String, String> decisionMetadata;

        private Builder() {}

        public Builder allowed(boolean allowed) { this.allowed = allowed; return this; }
        public Builder requiresConsent(boolean requiresConsent) { this.requiresConsent = requiresConsent; return this; }
        public Builder requiresAudit(boolean requiresAudit) { this.requiresAudit = requiresAudit; return this; }
        public Builder requiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; return this; }
        public Builder denialReason(String denialReason) { this.denialReason = denialReason; return this; }
        public Builder requiredFeatures(Set<String> requiredFeatures) { this.requiredFeatures = requiredFeatures; return this; }
        public Builder matchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; return this; }
        public Builder decisionMetadata(Map<String, String> decisionMetadata) { this.decisionMetadata = decisionMetadata; return this; }

        public BoundaryPolicyEvaluationResult build() { return new BoundaryPolicyEvaluationResult(this); }
    }
}
