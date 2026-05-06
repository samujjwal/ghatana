package com.ghatana.kernel.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoundaryPolicyRuleValidator")
class BoundaryPolicyRuleValidatorTest {

    @Test
    @DisplayName("accepts canonical actions without extra declaration")
    void acceptsCanonicalActions() {
        List<BoundaryPolicyRule> rules = List.of(rule("RULE-1", "read"));

        assertThatCode(() -> BoundaryPolicyRuleValidator.validate(rules))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects undeclared product-specific actions")
    void rejectsUndeclaredProductSpecificActions() {
        List<BoundaryPolicyRule> rules = List.of(rule("RULE-1", "launch"));

        assertThatThrownBy(() -> BoundaryPolicyRuleValidator.validate(rules))
            .isInstanceOf(BoundaryPolicyStore.BoundaryPolicyStoreException.class)
            .hasMessageContaining("action 'launch' is not declared");
    }

    @Test
    @DisplayName("accepts product-specific actions when declared in registry")
    void acceptsDeclaredProductSpecificActions() {
        List<BoundaryPolicyRule> rules = List.of(rule("RULE-1", "launch"));
        BoundaryPolicyActionRegistry registry =
            BoundaryPolicyActionRegistry.ofDeclaredActions(Set.of("launch", "pause"));

        assertThatCode(() -> BoundaryPolicyRuleValidator.validate(
                rules,
                registry,
                BoundaryPolicyResourceRegistry.ofDeclaredResources(Set.of("resource"))))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects undeclared product-specific resource patterns")
    void rejectsUndeclaredProductSpecificResourcePatterns() {
        List<BoundaryPolicyRule> rules = List.of(rule("RULE-1", "read"));

        assertThatThrownBy(() -> BoundaryPolicyRuleValidator.validate(
                rules,
                BoundaryPolicyActionRegistry.ofDeclaredActions(Set.of("read")),
                BoundaryPolicyResourceRegistry.ofDeclaredResources(Set.of("other-resource"))))
            .isInstanceOf(BoundaryPolicyStore.BoundaryPolicyStoreException.class)
            .hasMessageContaining("resourcePattern 'resource/**' is not declared");
    }

    @Test
    @DisplayName("accepts declared product-specific resource patterns")
    void acceptsDeclaredProductSpecificResourcePatterns() {
        List<BoundaryPolicyRule> rules = List.of(rule("RULE-1", "read"));

        assertThatCode(() -> BoundaryPolicyRuleValidator.validate(
                rules,
                BoundaryPolicyActionRegistry.ofDeclaredActions(Set.of("read")),
                BoundaryPolicyResourceRegistry.ofDeclaredResources(Set.of("resource"))))
            .doesNotThrowAnyException();
    }

    private static BoundaryPolicyRule rule(String ruleId, String action) {
        return BoundaryPolicyRule.builder()
            .ruleId(ruleId)
            .sourceScopePattern("product-a.*")
            .targetScopePattern("product-a.*")
            .resourcePattern("resource/**")
            .actions(action)
            .effect(BoundaryPolicyRule.Effect.ALLOW)
            .requiresAudit(false)
            .build();
    }
}
