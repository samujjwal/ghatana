package com.ghatana.kernel.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductBoundaryPolicyPackValidator")
class ProductBoundaryPolicyPackValidatorTest {

    private static final ProductBoundaryPolicyValidationProfile PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("Test Product")
                    .rulePrefix("TP-")
                    .defaultDenyRuleId("TP-BP-999")
                    .targetScopePrefix("test-product.")
                    .build();

    @Test
    void shouldAcceptWellFormedRules() {
        assertThatCode(() -> ProductBoundaryPolicyPackValidator.validate(validRules(), PROFILE))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateRuleIds() {
        List<BoundaryPolicyRule> rules = List.of(
                allowRule("TP-BP-001"),
                allowRule("TP-BP-001"),
                defaultDeny()
        );

        assertThatThrownBy(() -> ProductBoundaryPolicyPackValidator.validate(rules, PROFILE))
                .hasMessageContaining("Duplicate ruleId");
    }

    @Test
    void shouldRejectMissingDefaultDeny() {
        assertThatThrownBy(() -> ProductBoundaryPolicyPackValidator.validate(List.of(allowRule("TP-BP-001")), PROFILE))
                .hasMessageContaining("Missing default-deny rule");
    }

    @Test
    void shouldRejectUnsafeTargetWildcard() {
        List<BoundaryPolicyRule> rules = List.of(
                BoundaryPolicyRule.builder()
                        .ruleId("TP-BP-001")
                        .sourceScopePattern("test-product.*")
                        .targetScopePattern("**")
                        .resourcePattern("workspace/**")
                        .actions("read")
                        .requiresAudit(true)
                        .effect(BoundaryPolicyRule.Effect.ALLOW)
                        .metadata(Map.of("packVersion", "1.0.0", "ruleCategory", "workspace"))
                        .build(),
                defaultDeny()
        );

        assertThatThrownBy(() -> ProductBoundaryPolicyPackValidator.validate(rules, PROFILE))
                .hasMessageContaining("unsafe targetScopePattern '**'");
    }

    @Test
    void shouldRejectDefaultDenyWithoutAudit() {
        List<BoundaryPolicyRule> rules = List.of(
                allowRule("TP-BP-001"),
                BoundaryPolicyRule.builder()
                        .ruleId("TP-BP-999")
                        .sourceScopePattern("**")
                        .targetScopePattern("test-product.*")
                        .resourcePattern("**")
                        .actions("*")
                        .effect(BoundaryPolicyRule.Effect.DENY)
                        .metadata(Map.of("packVersion", "1.0.0", "ruleCategory", "default-deny"))
                        .build()
        );

        assertThatThrownBy(() -> ProductBoundaryPolicyPackValidator.validate(rules, PROFILE))
                .hasMessageContaining("Default-deny rule must require audit");
    }

    private static List<BoundaryPolicyRule> validRules() {
        return List.of(allowRule("TP-BP-001"), defaultDeny());
    }

    private static BoundaryPolicyRule allowRule(String ruleId) {
        return BoundaryPolicyRule.builder()
                .ruleId(ruleId)
                .sourceScopePattern("test-product.*")
                .targetScopePattern("test-product.*")
                .resourcePattern("workspace/**")
                .actions("read")
                .requiresAudit(true)
                .effect(BoundaryPolicyRule.Effect.ALLOW)
                .metadata(Map.of("packVersion", "1.0.0", "ruleCategory", "workspace"))
                .build();
    }

    private static BoundaryPolicyRule defaultDeny() {
        return BoundaryPolicyRule.builder()
                .ruleId("TP-BP-999")
                .sourceScopePattern("**")
                .targetScopePattern("test-product.*")
                .resourcePattern("**")
                .actions("*")
                .requiresAudit(true)
                .effect(BoundaryPolicyRule.Effect.DENY)
                .metadata(Map.of("packVersion", "1.0.0", "ruleCategory", "default-deny"))
                .build();
    }
}
