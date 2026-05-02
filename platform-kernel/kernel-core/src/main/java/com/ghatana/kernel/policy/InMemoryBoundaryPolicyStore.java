package com.ghatana.kernel.policy;

import java.util.List;
import java.util.Objects;

/**
 * In-memory implementation of {@link BoundaryPolicyStore}.
 *
 * <p>Holds a fixed, pre-validated list of rules supplied at construction time.
 * Suitable for unit tests, bootstrapped kernel configurations, and policy packs
 * that load rules from code or static configuration at startup.</p>
 *
 * <p>Rules are validated at construction. Any violation causes an immediate
 * {@link BoundaryPolicyStore.BoundaryPolicyStoreException} — no deferred failure.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory BoundaryPolicyStore for startup-time or test policy configurations
 * @doc.layer core
 * @doc.pattern Repository
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class InMemoryBoundaryPolicyStore implements BoundaryPolicyStore {

    private final List<BoundaryPolicyRule> rules;

    /**
     * Creates a store from a pre-parsed, validated list of rules.
     *
     * @param rules the rules to serve; must not be null and will be validated
     */
    public InMemoryBoundaryPolicyStore(List<BoundaryPolicyRule> rules) {
        Objects.requireNonNull(rules, "rules cannot be null");
        BoundaryPolicyRuleValidator.validate(rules);
        this.rules = List.copyOf(rules);
    }

    /**
     * Creates a store using a {@link BoundaryPolicyRuleParser} for inline rule composition.
     *
     * @param parser the parser whose rules will be built and validated
     */
    public static InMemoryBoundaryPolicyStore fromParser(BoundaryPolicyRuleParser parser) {
        return new InMemoryBoundaryPolicyStore(
                Objects.requireNonNull(parser, "parser cannot be null").buildAndValidate());
    }

    /**
     * Returns the standard platform rules appropriate for a generic kernel deployment.
     *
     * <p>Standard rules encode the following access policy:</p>
     * <ul>
     *   <li>Any scope may read {@code INTERNAL}-classified resources cross-scope (audit required).</li>
     *   <li>Cross-scope reads of {@code CONFIDENTIAL} resources require audit but not consent.</li>
     *   <li>Cross-scope reads of {@code RESTRICTED} resources require consent and audit.</li>
     *   <li>All writes require audit.</li>
     *   <li>Data-residency-restricted resources are denied cross-scope regardless of classification.</li>
     * </ul>
     *
     * <p>These rules are generic. Products supply domain-specific overrides via their own
     * policy packs registered through the {@link BoundaryPolicyStore} SPI.</p>
     *
     * @return store pre-loaded with standard platform rules
     */
    public static InMemoryBoundaryPolicyStore withStandardRules() {
        BoundaryPolicyRuleParser parser = BoundaryPolicyRuleParser.create()

            // DENY: data-residency-restricted cross-scope reads — no exceptions
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-001-residency-restricted-deny")
                    .sourceScopePattern("*")
                    .targetScopePattern("*")
                    .resourcePattern("*")
                    .actions("read", "write", "execute")
                    .classificationCondition("compliance-tag:data-residency-restricted")
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .requiresAudit(true)
                    .metadata(java.util.Map.of("reason", "Data residency restriction prohibits cross-scope access"))
                    .build())

            // ALLOW: same-scope access is always permitted
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-002-same-scope-allow")
                    .sourceScopePattern("*")
                    .targetScopePattern("same")
                    .resourcePattern("*")
                    .actions("read", "write", "execute")
                    .classificationCondition("*")
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .requiresAudit(false)
                    .build())

            // ALLOW: cross-scope read of INTERNAL resources — audit required
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-003-internal-cross-scope-read")
                    .sourceScopePattern("*")
                    .targetScopePattern("*")
                    .resourcePattern("*")
                    .actions("read")
                    .classificationCondition("sensitivity:INTERNAL")
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .requiresAudit(true)
                    .requiresConsent(false)
                    .build())

            // ALLOW: cross-scope read of CONFIDENTIAL resources — audit required, no consent
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-004-confidential-cross-scope-read")
                    .sourceScopePattern("*")
                    .targetScopePattern("*")
                    .resourcePattern("*")
                    .actions("read")
                    .classificationCondition("sensitivity:CONFIDENTIAL")
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .requiresAudit(true)
                    .requiresConsent(false)
                    .build())

            // ALLOW: cross-scope read of RESTRICTED resources — consent + audit required
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-005-restricted-cross-scope-read")
                    .sourceScopePattern("*")
                    .targetScopePattern("*")
                    .resourcePattern("*")
                    .actions("read")
                    .classificationCondition("sensitivity:RESTRICTED")
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .requiresAudit(true)
                    .requiresConsent(true)
                    .build())

            // ALLOW: cross-scope write — always requires audit
            .add(BoundaryPolicyRule.builder()
                    .ruleId("PLATFORM-006-cross-scope-write")
                    .sourceScopePattern("*")
                    .targetScopePattern("*")
                    .resourcePattern("*")
                    .actions("write")
                    .classificationCondition("*")
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .requiresAudit(true)
                    .requiresConsent(false)
                    .build());

        return new InMemoryBoundaryPolicyStore(parser.buildAndValidate());
    }

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        // In-memory store returns the same set regardless of context.
        // Subclasses or decorators may filter by context.
        return rules;
    }
}
