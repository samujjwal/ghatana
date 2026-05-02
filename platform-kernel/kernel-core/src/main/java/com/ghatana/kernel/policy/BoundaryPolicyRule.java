package com.ghatana.kernel.policy;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A single boundary policy rule that governs cross-scope access.
 *
 * <p>Rules are evaluated by {@link BoundaryPolicyResolver} implementations via
 * {@link BoundaryPolicyStore}. Each rule specifies the source and target scope
 * patterns, resource and action patterns, classification conditions, consent and
 * audit requirements, and the access effect.</p>
 *
 * <p>Pattern matching uses simple glob-style wildcards:
 * {@code *} matches any single segment; {@code **} matches any path.</p>
 *
 * @doc.type class
 * @doc.purpose Immutable rule descriptor for boundary policy evaluation
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyRule {

    /**
     * The effect of a boundary policy rule.
     */
    public enum Effect {
        /** Access is permitted. */
        ALLOW,
        /** Access is denied. */
        DENY,
        /** Access requires explicit human or workflow approval. */
        REQUIRE_APPROVAL
    }

    private final String ruleId;
    private final String sourceScopePattern;
    private final String targetScopePattern;
    private final String resourcePattern;
    private final Set<String> actions;
    private final String classificationCondition;
    private final Set<String> requiredFeatures;
    private final boolean requiresConsent;
    private final boolean requiresAudit;
    private final Effect effect;
    private final Map<String, String> metadata;

    private BoundaryPolicyRule(Builder builder) {
        this.ruleId = Objects.requireNonNull(builder.ruleId, "ruleId cannot be null");
        if (this.ruleId.isBlank()) throw new IllegalArgumentException("ruleId cannot be blank");
        this.sourceScopePattern = Objects.requireNonNull(builder.sourceScopePattern, "sourceScopePattern cannot be null");
        this.targetScopePattern = Objects.requireNonNull(builder.targetScopePattern, "targetScopePattern cannot be null");
        this.resourcePattern = Objects.requireNonNull(builder.resourcePattern, "resourcePattern cannot be null");
        this.actions = Collections.unmodifiableSet(Objects.requireNonNull(builder.actions, "actions cannot be null"));
        if (this.actions.isEmpty()) throw new IllegalArgumentException("actions must not be empty for rule: " + this.ruleId);
        this.classificationCondition = builder.classificationCondition != null ? builder.classificationCondition : "*";
        this.requiredFeatures = Collections.unmodifiableSet(builder.requiredFeatures != null ? builder.requiredFeatures : Set.of());
        this.requiresConsent = builder.requiresConsent;
        this.requiresAudit = builder.requiresAudit;
        this.effect = Objects.requireNonNull(builder.effect, "effect cannot be null");
        this.metadata = Collections.unmodifiableMap(builder.metadata != null ? builder.metadata : Map.of());
    }

    public String getRuleId() { return ruleId; }
    public String getSourceScopePattern() { return sourceScopePattern; }
    public String getTargetScopePattern() { return targetScopePattern; }
    public String getResourcePattern() { return resourcePattern; }
    public Set<String> getActions() { return actions; }
    public String getClassificationCondition() { return classificationCondition; }
    public Set<String> getRequiredFeatures() { return requiredFeatures; }
    public boolean isRequiresConsent() { return requiresConsent; }
    public boolean isRequiresAudit() { return requiresAudit; }
    public Effect getEffect() { return effect; }
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Returns a new builder pre-filled with this rule's values.
     */
    public Builder toBuilder() {
        return new Builder()
                .ruleId(ruleId)
                .sourceScopePattern(sourceScopePattern)
                .targetScopePattern(targetScopePattern)
                .resourcePattern(resourcePattern)
                .actions(actions)
                .classificationCondition(classificationCondition)
                .requiredFeatures(requiredFeatures)
                .requiresConsent(requiresConsent)
                .requiresAudit(requiresAudit)
                .effect(effect)
                .metadata(metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundaryPolicyRule that = (BoundaryPolicyRule) o;
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() { return Objects.hash(ruleId); }

    @Override
    public String toString() {
        return "BoundaryPolicyRule{ruleId='" + ruleId + "', effect=" + effect + '}';
    }

    /** Creates a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link BoundaryPolicyRule}.
     */
    public static final class Builder {
        private String ruleId;
        private String sourceScopePattern;
        private String targetScopePattern;
        private String resourcePattern;
        private Set<String> actions;
        private String classificationCondition;
        private Set<String> requiredFeatures;
        private boolean requiresConsent;
        private boolean requiresAudit;
        private Effect effect;
        private Map<String, String> metadata;

        private Builder() {}

        public Builder ruleId(String ruleId) { this.ruleId = ruleId; return this; }
        public Builder sourceScopePattern(String sourceScopePattern) { this.sourceScopePattern = sourceScopePattern; return this; }
        public Builder targetScopePattern(String targetScopePattern) { this.targetScopePattern = targetScopePattern; return this; }
        public Builder resourcePattern(String resourcePattern) { this.resourcePattern = resourcePattern; return this; }
        public Builder actions(Set<String> actions) { this.actions = Set.copyOf(actions); return this; }
        public Builder actions(String... actions) { this.actions = Set.of(actions); return this; }
        public Builder classificationCondition(String classificationCondition) { this.classificationCondition = classificationCondition; return this; }
        public Builder requiredFeatures(Set<String> requiredFeatures) { this.requiredFeatures = Set.copyOf(requiredFeatures); return this; }
        public Builder requiresConsent(boolean requiresConsent) { this.requiresConsent = requiresConsent; return this; }
        public Builder requiresAudit(boolean requiresAudit) { this.requiresAudit = requiresAudit; return this; }
        public Builder effect(Effect effect) { this.effect = effect; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = Map.copyOf(metadata); return this; }

        public BoundaryPolicyRule build() { return new BoundaryPolicyRule(this); }
    }
}
