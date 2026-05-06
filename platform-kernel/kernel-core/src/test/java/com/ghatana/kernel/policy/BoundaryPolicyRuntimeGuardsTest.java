package com.ghatana.kernel.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BoundaryPolicyRuntimeGuards")
class BoundaryPolicyRuntimeGuardsTest {

    @Test
    void shouldAllowInMemoryStoreInLocalEnvironment() {
        assertThatCode(() -> BoundaryPolicyRuntimeGuards.assertStoreAllowed(
                "local",
                testInMemoryStore(),
                "test-composition-root"
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInMemoryStoreInProductionEnvironment() {
        assertThatThrownBy(() -> BoundaryPolicyRuntimeGuards.assertStoreAllowed(
                "production",
                testInMemoryStore(),
                "test-composition-root"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("InMemoryBoundaryPolicyStore")
                .hasMessageContaining("test-composition-root");
    }

    @Test
    void shouldAllowProductOwnedStoreInProductionEnvironment() {
        assertThatCode(() -> BoundaryPolicyRuntimeGuards.assertStoreAllowed(
                "production",
                new TestBoundaryPolicyStore(),
                "test-composition-root"
        )).doesNotThrowAnyException();
    }

    private static final class TestBoundaryPolicyStore implements BoundaryPolicyStore {

        @Override
        public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
            return List.of();
        }
    }

    private static InMemoryBoundaryPolicyStore testInMemoryStore() {
        return new InMemoryBoundaryPolicyStore(List.of(
                BoundaryPolicyRule.builder()
                        .ruleId("TEST-001")
                        .sourceScopePattern("test.*")
                        .targetScopePattern("test.*")
                        .resourcePattern("resource/**")
                        .actions("read")
                        .effect(BoundaryPolicyRule.Effect.ALLOW)
                        .requiresAudit(false)
                        .build()
        ));
    }
}
