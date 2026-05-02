package com.ghatana.digitalmarketing.pack;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DigitalMarketingBoundaryPolicyStore}.
 *
 * <p>Verifies that all nine required DMOS boundary policy rules are present with the
 * correct resource patterns, action sets, effects, consent, and audit flags.</p>
 */
@DisplayName("DigitalMarketingBoundaryPolicyStore")
class DigitalMarketingBoundaryPolicyStoreTest {

    private DigitalMarketingBoundaryPolicyStore store;
    private List<BoundaryPolicyRule> rules;

    @BeforeEach
    void setUp() {
        store = new DigitalMarketingBoundaryPolicyStore();
        rules = store.loadRules(BoundaryPolicyLoadContext.global());
    }

    @Test
    @DisplayName("loadRules() returns exactly 9 rules")
    void shouldReturnNineRules() {
        assertThat(rules).hasSize(9);
    }

    @Test
    @DisplayName("loadRules() never returns null")
    void shouldNeverReturnNull() {
        assertThat(store.loadRules(BoundaryPolicyLoadContext.global())).isNotNull();
        assertThat(store.loadRules(BoundaryPolicyLoadContext.of("tenant-1", "EU"))).isNotNull();
    }

    @Test
    @DisplayName("DM-BP-001: workspaces/** read is ALLOW with no consent or audit requirement")
    void shouldHaveWorkspaceReadAllow() {
        BoundaryPolicyRule rule = findRule("DM-BP-001");
        assertThat(rule.getResourcePattern()).isEqualTo("workspaces/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("read");
        assertThat(rule.getEffect()).isEqualTo(Effect.ALLOW);
        assertThat(rule.isRequiresConsent()).isFalse();
        assertThat(rule.isRequiresAudit()).isFalse();
    }

    @Test
    @DisplayName("DM-BP-002: contacts/** read is ALLOW with consent and audit required")
    void shouldHaveContactReadAllow() {
        BoundaryPolicyRule rule = findRule("DM-BP-002");
        assertThat(rule.getResourcePattern()).isEqualTo("contacts/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("read");
        assertThat(rule.getEffect()).isEqualTo(Effect.ALLOW);
        assertThat(rule.isRequiresConsent()).isTrue();
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-003: contacts/** write/delete/export is REQUIRE_APPROVAL with audit required")
    void shouldHaveContactWriteRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-003");
        assertThat(rule.getResourcePattern()).isEqualTo("contacts/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("write", "delete", "export");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-004: audiences/** export/sync is REQUIRE_APPROVAL with consent and audit required")
    void shouldHaveAudienceExportRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-004");
        assertThat(rule.getResourcePattern()).isEqualTo("audiences/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("export", "sync");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresConsent()).isTrue();
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-005: campaigns/** launch/pause/resume is REQUIRE_APPROVAL with audit required")
    void shouldHaveCampaignLifecycleRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-005");
        assertThat(rule.getResourcePattern()).isEqualTo("campaigns/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("launch", "pause", "resume");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-006: budgets/** write/increase is REQUIRE_APPROVAL with audit required")
    void shouldHaveBudgetWriteRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-006");
        assertThat(rule.getResourcePattern()).isEqualTo("budgets/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("write", "increase");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-007: content/** publish is REQUIRE_APPROVAL with audit required")
    void shouldHaveContentPublishRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-007");
        assertThat(rule.getResourcePattern()).isEqualTo("content/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("publish");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-008: connectors/** write/execute is REQUIRE_APPROVAL with audit required")
    void shouldHaveConnectorWriteRequireApproval() {
        BoundaryPolicyRule rule = findRule("DM-BP-008");
        assertThat(rule.getResourcePattern()).isEqualTo("connectors/**");
        assertThat(rule.getActions()).containsExactlyInAnyOrder("write", "execute");
        assertThat(rule.getEffect()).isEqualTo(Effect.REQUIRE_APPROVAL);
        assertThat(rule.isRequiresAudit()).isTrue();
    }

    @Test
    @DisplayName("DM-BP-999: default-deny rule is last in the list and covers ** with DENY")
    void shouldHaveDefaultDenyAsLastRule() {
        List<BoundaryPolicyRule> all = rules;
        BoundaryPolicyRule last = all.get(all.size() - 1);
        assertThat(last.getRuleId()).isEqualTo("DM-BP-999");
        assertThat(last.getResourcePattern()).isEqualTo("**");
        assertThat(last.getActions()).contains("*");
        assertThat(last.getEffect()).isEqualTo(Effect.DENY);
    }

    @Test
    @DisplayName("all rules use the digital-marketing.* source and target scope pattern")
    void shouldAllUseDmScopePatterns() {
        rules.forEach(rule -> {
            assertThat(rule.getSourceScopePattern())
                .as("rule %s should use digital-marketing.* source scope", rule.getRuleId())
                .isEqualTo("digital-marketing.*");
            assertThat(rule.getTargetScopePattern())
                .as("rule %s should use digital-marketing.* target scope", rule.getRuleId())
                .isEqualTo("digital-marketing.*");
        });
    }

    @Test
    @DisplayName("all rule IDs are unique")
    void shouldHaveUniqueRuleIds() {
        long distinctCount = rules.stream().map(BoundaryPolicyRule::getRuleId).distinct().count();
        assertThat(distinctCount).isEqualTo(rules.size());
    }

    @Test
    @DisplayName("rules are stable across multiple loadRules() calls")
    void shouldReturnSameRulesOnEveryCall() {
        List<BoundaryPolicyRule> first  = store.loadRules(BoundaryPolicyLoadContext.global());
        List<BoundaryPolicyRule> second = store.loadRules(BoundaryPolicyLoadContext.global());
        assertThat(first).isEqualTo(second);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private BoundaryPolicyRule findRule(String ruleId) {
        return rules.stream()
            .filter(r -> r.getRuleId().equals(ruleId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected rule not found: " + ruleId));
    }
}
