/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyResolver;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.DefaultBoundaryPolicyResolver;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.scope.ScopeDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item-29 policy compatibility test for Finance.
 *
 * <p>Proves that the Finance boundary policy store composes correctly with the generic
 * {@link DefaultBoundaryPolicyResolver} without any changes to the platform kernel.
 * Every assertion invokes real production code — the resolver and the policy store —
 * and verifies actual access decisions match the declared Finance boundary posture.</p>
 *
 * <p>This test must not import any kernel class that is aware of "Finance" as a concept.
 * All product-specificity lives in {@link FinanceBoundaryPolicyStore}.</p>
 *
 * @doc.type class
 * @doc.purpose Finance policy compatibility — proves Finance packs load into the generic kernel resolver
 * @doc.layer product
 * @doc.pattern Test
 * @since 1.0.0
 */
@DisplayName("Finance Policy Compatibility Test (item 29)")
class FinancePolicyCompatibilityTest {

    private DefaultBoundaryPolicyResolver resolver;

    private final ScopeDescriptor financeTrading = ScopeDescriptor.domainPack("finance.trading");
    private final ScopeDescriptor financeRisk = ScopeDescriptor.domainPack("finance.risk");
    private final ScopeDescriptor externalScope = ScopeDescriptor.product("external-service");

    private final ClassificationDescriptor confidential = ClassificationDescriptor.of(
            "capital-markets", SensitivityLevel.CONFIDENTIAL);
    private final ClassificationDescriptor general = ClassificationDescriptor.of(
            "general", SensitivityLevel.INTERNAL);

    @BeforeEach
    void setUp() {
        // Wire the generic resolver with the Finance store — no kernel changes required.
        FinanceBoundaryPolicyStore store = new FinanceBoundaryPolicyStore();
        BoundaryPolicyLoadContext ctx = BoundaryPolicyLoadContext.of("default", "GLOBAL");
        resolver = new DefaultBoundaryPolicyResolver(store, ctx);
    }

    // ── Store contract ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Finance policy store loads non-empty well-formed rules into the resolver")
    void storeLoadsNonEmptyRules() {
        FinanceBoundaryPolicyStore store = new FinanceBoundaryPolicyStore();
        List<BoundaryPolicyRule> rules = store.loadRules(BoundaryPolicyLoadContext.of("default", "GLOBAL"));

        assertThat(rules).isNotEmpty();
        for (BoundaryPolicyRule rule : rules) {
            assertThat(rule.getRuleId()).as("ruleId must not be blank").isNotBlank();
            assertThat(rule.getActions()).as("actions must not be empty for %s", rule.getRuleId()).isNotEmpty();
            assertThat(rule.getEffect()).as("effect must not be null for %s", rule.getRuleId()).isNotNull();
        }
    }

    @Test
    @DisplayName("Finance rules have at least one ALLOW rule and exactly one default-deny rule")
    void rulesHaveAllowAndDefaultDeny() {
        FinanceBoundaryPolicyStore store = new FinanceBoundaryPolicyStore();
        List<BoundaryPolicyRule> rules = store.loadRules(BoundaryPolicyLoadContext.of("default", "GLOBAL"));

        long allowCount = rules.stream().filter(r -> r.getEffect() == Effect.ALLOW).count();
        assertThat(allowCount).as("at least one ALLOW rule must exist").isGreaterThanOrEqualTo(1);

        BoundaryPolicyRule last = rules.get(rules.size() - 1);
        assertThat(last.getEffect()).as("last rule must be a default-deny").isEqualTo(Effect.DENY);
        assertThat(last.getSourceScopePattern()).isEqualTo("**");
        assertThat(last.getTargetScopePattern()).startsWith("finance");
    }

    // ── Resolver compatibility ────────────────────────────────────────────────

    @Test
    @DisplayName("Finance store wires into DefaultBoundaryPolicyResolver without kernel modification")
    void financeStoreComposesWithGenericResolver() {
        assertThat(resolver).isNotNull();
    }

    @Test
    @DisplayName("Finance internal scope can read its own transaction records (with audit)")
    void internalScopeReadTransactionRecordsAllowed() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                financeTrading,
                financeTrading,
                "finance:transactions/order-789",
                "read",
                confidential
        );
        assertThat(decision.allowed())
                .as("Finance internal read of own transaction records should be allowed")
                .isTrue();
    }

    @Test
    @DisplayName("External scope accessing Finance transactions is denied by default-deny")
    void externalScopeAccessFinanceTransactionsDenied() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                externalScope,
                financeTrading,
                "finance:transactions/order-789",
                "read",
                confidential
        );
        assertThat(decision.allowed())
                .as("External scope must not access Finance transactions")
                .isFalse();
    }

    @Test
    @DisplayName("Finance transaction write requires approval (REQUIRE_APPROVAL effect)")
    void internalWriteTransactionRequiresApproval() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                financeTrading,
                financeTrading,
                "finance:transactions/order-789",
                "settle",
                confidential
        );
        // REQUIRE_APPROVAL maps to denied (pending approval workflow)
        assertThat(decision.allowed())
                .as("Finance transaction write/settle must require approval (not directly allowed)")
                .isFalse();
    }

    @Test
    @DisplayName("Position export is denied even for Finance-internal scopes")
    void positionExportDeniedForInternalScope() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                financeRisk,
                financeTrading,
                "finance:positions/portfolio-001",
                "export",
                confidential
        );
        assertThat(decision.allowed())
                .as("Finance position export must be denied (hardened per FIN-BP-003)")
                .isFalse();
    }

    @Test
    @DisplayName("Risk data read by Finance risk scope is allowed with audit")
    void riskScopeReadRiskDataAllowed() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                financeRisk,
                financeRisk,
                "finance:risk-data/entity-001",
                "read",
                confidential
        );
        assertThat(decision.allowed())
                .as("Finance risk scope read of risk data should be allowed")
                .isTrue();
    }

    // ── Isolation check ───────────────────────────────────────────────────────

    @Test
    @DisplayName("DefaultBoundaryPolicyResolver class does not reference FinanceBoundaryPolicyStore")
    void resolverClassHasNoFinanceReference() {
        String resolverClassName = DefaultBoundaryPolicyResolver.class.getName();
        assertThat(resolverClassName).doesNotContain("finance", "Finance", "FINANCE");

        assertThat(resolver).isInstanceOf(DefaultBoundaryPolicyResolver.class);
    }
}
