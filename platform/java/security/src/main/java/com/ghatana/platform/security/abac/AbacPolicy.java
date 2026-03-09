package com.ghatana.platform.security.abac;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * An ABAC policy rule that evaluates attribute conditions to produce an authorization decision.
 *
 * <p>Policies are composable conditions: each policy tests a predicate against the
 * request attributes and returns PERMIT, DENY, or NOT_APPLICABLE.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * AbacPolicy ownerOnlyEdit = AbacPolicy.builder("owner-edit")
 *     .description("Only resource owners can edit")
 *     .target(req -> "write".equals(req.action()))
 *     .condition(req -> {
 *         Object owner = req.resource().get("ownerId");
 *         Object userId = req.subject().get("userId");
 *         return owner != null && owner.equals(userId);
 *     })
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ABAC policy rule
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class AbacPolicy {
    private final String id;
    private final String description;
    private final Predicate<AbacRequest> target;
    private final Predicate<AbacRequest> condition;
    private final Effect effect;

    /**
     * Policy effect.
     */
    public enum Effect {
        PERMIT,
        DENY
    }

    private AbacPolicy(String id, String description,
                       Predicate<AbacRequest> target,
                       Predicate<AbacRequest> condition,
                       Effect effect) {
        this.id = id;
        this.description = description;
        this.target = target;
        this.condition = condition;
        this.effect = effect;
    }

    /**
     * Evaluates this policy against the given request.
     *
     * @return PERMIT, DENY, or null if not applicable.
     */
    public AbacDecision evaluate(@NotNull AbacRequest request) {
        // Check if this policy applies to the request
        if (!target.test(request)) {
            return null; // Not applicable
        }

        // Evaluate the condition
        if (condition.test(request)) {
            return effect == Effect.PERMIT
                ? AbacDecision.permit(description, id)
                : AbacDecision.deny(description, id);
        }

        // Condition not met — inverse effect
        return effect == Effect.PERMIT
            ? AbacDecision.deny("Condition not met: " + description, id)
            : AbacDecision.permit("Deny condition not met: " + description, id);
    }

    public String getId() { return id; }
    public String getDescription() { return description; }

    /**
     * Creates a new policy builder.
     */
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String description = "";
        private Predicate<AbacRequest> target = req -> true;
        private Predicate<AbacRequest> condition = req -> true;
        private Effect effect = Effect.PERMIT;

        private Builder(String id) { this.id = id; }

        public Builder description(String desc) { this.description = desc; return this; }
        public Builder target(Predicate<AbacRequest> t) { this.target = t; return this; }
        public Builder condition(Predicate<AbacRequest> c) { this.condition = c; return this; }
        public Builder effect(Effect e) { this.effect = e; return this; }

        public AbacPolicy build() {
            return new AbacPolicy(id, description, target, condition, effect);
        }
    }
}
