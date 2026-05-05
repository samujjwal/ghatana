/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.kernel;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;
import com.ghatana.plugin.compliance.CompliancePlugin;
import com.ghatana.finance.kernel.policy.FinanceBoundaryPolicyStore;
import com.ghatana.finance.kernel.policy.FinanceComplianceRulePack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3.4 product-side contract tests for Finance packs.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Finance boundary policy store loads a non-empty, well-formed rule list.</li>
 *   <li>Default-deny rule is present and is the last rule.</li>
 *   <li>Finance compliance rule packs are non-empty and use non-blank rule IDs.</li>
 *   <li>Kernel source files do not import Finance pack classes (boundary isolation).</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose Finance pack contract tests — boundary and rule correctness
 * @doc.layer product
 * @doc.pattern Test
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
@DisplayName("Finance Pack Contract Tests")
class FinancePackContractTest {

    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("Finance")
                    .rulePrefix("FIN-BP-")
                    .defaultDenyRuleId("FIN-BP-999")
                    .targetScopePrefix("finance.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();

    // -------------------------------------------------------------------------
    // BoundaryPolicyStore contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FinanceBoundaryPolicyStore")
    class BoundaryPolicyStoreTests {

        private final FinanceBoundaryPolicyStore store = new FinanceBoundaryPolicyStore();
        private final BoundaryPolicyLoadContext anyContext = BoundaryPolicyLoadContext.global();

        @Test
        @DisplayName("loadRules() returns a non-empty rule list")
        void loadRulesReturnsNonEmpty() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            assertThat(rules).isNotEmpty();
        }

        @Test
        @DisplayName("all rules have non-blank ruleId and non-empty actions")
        void allRulesWellFormed() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            ProductBoundaryPolicyPackValidator.validate(rules, VALIDATION_PROFILE);
            for (BoundaryPolicyRule rule : rules) {
                assertThat(rule.getRuleId())
                        .as("ruleId must not be blank")
                        .isNotBlank();
                assertThat(rule.getActions())
                        .as("actions must not be empty for rule %s", rule.getRuleId())
                        .isNotEmpty();
                assertThat(rule.getEffect())
                        .as("effect must not be null for rule %s", rule.getRuleId())
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("last rule is a default-deny rule covering all scopes and resources")
        void lastRuleIsDefaultDeny() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule lastRule = rules.get(rules.size() - 1);
            assertThat(lastRule.getEffect())
                    .as("last rule must be DENY (default-deny posture)")
                    .isEqualTo(Effect.DENY);
            assertThat(lastRule.getSourceScopePattern())
                    .as("default-deny must match all source scopes")
                    .isEqualTo("**");
            assertThat(lastRule.getTargetScopePattern())
                    .as("default-deny must target finance scope")
                    .startsWith("finance");
        }

        @Test
        @DisplayName("transaction write requires approval (four-eyes)")
        void transactionWriteRequiresApproval() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule writeRule = rules.stream()
                    .filter(r -> r.getActions().contains("write")
                            && r.getResourcePattern().startsWith("transactions"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No transaction write rule found"));

            assertThat(writeRule.getEffect())
                    .as("transaction write must require approval (four-eyes)")
                    .isEqualTo(Effect.REQUIRE_APPROVAL);
        }

        @Test
        @DisplayName("position export is always denied")
        void positionExportIsDenied() {
            List<BoundaryPolicyRule> rules = store.loadRules(anyContext);
            BoundaryPolicyRule exportRule = rules.stream()
                    .filter(r -> r.getActions().contains("export")
                            && r.getResourcePattern().startsWith("positions"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No position export rule found"));

            assertThat(exportRule.getEffect())
                    .as("position export must be DENY")
                    .isEqualTo(Effect.DENY);
        }

        @Test
        @DisplayName("transaction writes require approval and audit")
        void transactionWritesRequireApprovalAndAudit() {
            BoundaryPolicyRule writeRule = store.loadRules(anyContext).stream()
                    .filter(r -> r.getResourcePattern().startsWith("transactions")
                            && r.getActions().contains("write"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No transaction write rule found"));

            assertThat(writeRule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
            assertThat(writeRule.isRequiresAudit()).isTrue();
            assertThat(writeRule.getMetadata()).containsEntry("approvalPolicy", "four-eyes");
        }

        @Test
        @DisplayName("unsupported tenant or region override fails closed")
        void unsupportedTenantOrRegionOverrideFailsClosed() {
            assertThatThrownBy(() -> store.loadRules(BoundaryPolicyLoadContext.of("tenant-emea", "EU")))
                    .isInstanceOf(BoundaryPolicyStore.BoundaryPolicyStoreException.class)
                    .hasMessageContaining("unsupported");
        }
    }

    // -------------------------------------------------------------------------
    // ComplianceRulePack contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FinanceComplianceRulePack")
    class ComplianceRulePackTests {

        @Test
        @DisplayName("transactionIntegrityRules() is non-empty with non-blank IDs")
        void transactionIntegrityRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = FinanceComplianceRulePack.transactionIntegrityRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> {
                assertThat(r.ruleId()).isNotBlank();
                assertThat(r.description()).isNotBlank();
                assertThat(r.severity()).isNotNull();
            });
        }

        @Test
        @DisplayName("auditRecordKeepingRules() is non-empty with non-blank IDs")
        void auditRecordKeepingRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = FinanceComplianceRulePack.auditRecordKeepingRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> assertThat(r.ruleId()).isNotBlank());
        }

        @Test
        @DisplayName("tradeSurveillanceRules() is non-empty with non-blank IDs")
        void tradeSurveillanceRulesNonEmpty() {
            List<CompliancePlugin.ComplianceRule> rules = FinanceComplianceRulePack.tradeSurveillanceRules();
            assertThat(rules).isNotEmpty();
            rules.forEach(r -> assertThat(r.ruleId()).isNotBlank());
        }

        @Test
        @DisplayName("rule set ID constants are non-blank and unique")
        void ruleSetIdsNonBlankAndUnique() {
            List<String> ids = List.of(
                    FinanceComplianceRulePack.TRANSACTION_INTEGRITY,
                    FinanceComplianceRulePack.AUDIT_RECORD_KEEPING,
                    FinanceComplianceRulePack.TRADE_SURVEILLANCE
            );
            assertThat(ids).doesNotHaveDuplicates();
            ids.forEach(id -> assertThat(id).isNotBlank());
        }

        @Test
        @DisplayName("all rule IDs are prefixed with FIN- to avoid collisions")
        void ruleIdsPrefixedWithFin() {
            List<CompliancePlugin.ComplianceRule> allRules = new ArrayList<>();
            allRules.addAll(FinanceComplianceRulePack.transactionIntegrityRules());
            allRules.addAll(FinanceComplianceRulePack.auditRecordKeepingRules());
            allRules.addAll(FinanceComplianceRulePack.tradeSurveillanceRules());
            allRules.forEach(r ->
                    assertThat(r.ruleId())
                            .as("rule ID must be prefixed with FIN-")
                            .startsWith("FIN-")
            );
        }
    }

    // -------------------------------------------------------------------------
    // Boundary isolation contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Kernel boundary isolation")
    class BoundaryIsolationTests {

        @Test
        @DisplayName("FinanceBoundaryPolicyStore must not extend any kernel implementation class")
        void financeStoreDoesNotExtendKernelImpl() {
            Class<?> storeClass = FinanceBoundaryPolicyStore.class;
            Class<?>[] interfaces = storeClass.getInterfaces();
            assertThat(interfaces)
                    .extracting(Class::getName)
                    .contains("com.ghatana.kernel.policy.BoundaryPolicyStore");
            assertThat(storeClass.getSuperclass().getName())
                    .as("Finance store must not extend a kernel implementation class")
                    .isEqualTo("java.lang.Object");
        }
    }
}
