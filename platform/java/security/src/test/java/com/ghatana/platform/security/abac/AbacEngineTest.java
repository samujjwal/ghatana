package com.ghatana.platform.security.abac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for AbacEngine deny-overrides evaluation logic
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AbacEngine — attribute-based access control evaluation")
class AbacEngineTest {

    private AbacEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AbacEngine();
    }

    private AbacRequest ownerRequest(String userId, String ownerId) {
        return new AbacRequest(
                Map.of("userId", userId),
                Map.of("ownerId", ownerId, "type", "document"),
                "write",
                Map.of());
    }

    @Test
    @DisplayName("evaluate returns deny when no policies registered")
    void evaluateReturnsDenyWhenNoPolicies() {
        AbacRequest request = AbacRequest.of("user1", "document", "read");

        AbacDecision decision = engine.evaluate(request);

        assertThat(decision.permitted()).isFalse();
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    @DisplayName("policyCount is zero on fresh engine")
    void policyCountIsZeroInitially() {
        assertThat(engine.policyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("addPolicy increments policy count")
    void addPolicyIncrementsPolicyCount() {
        AbacPolicy policy = AbacPolicy.builder("test-policy")
                .condition(req -> true)
                .build();

        engine.addPolicy(policy);

        assertThat(engine.policyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("evaluate returns permit when applicable policy condition is met")
    void evaluatePermitsWhenConditionMet() {
        engine.addPolicy(AbacPolicy.builder("owner-read")
                .description("Owner can read")
                .target(req -> "read".equals(req.action()))
                .condition(req -> req.subject().get("userId")
                        .equals(req.resource().get("ownerId")))
                .build());

        AbacRequest request = new AbacRequest(
                Map.of("userId", "alice"),
                Map.of("ownerId", "alice"),
                "read",
                Map.of());

        AbacDecision decision = engine.evaluate(request);

        assertThat(decision.permitted()).isTrue();
        assertThat(decision.matchedPolicyId()).isEqualTo("owner-read");
    }

    @Test
    @DisplayName("evaluate returns deny when condition is not met")
    void evaluateDeniesWhenConditionNotMet() {
        engine.addPolicy(AbacPolicy.builder("owner-only")
                .description("Only owner can write")
                .target(req -> "write".equals(req.action()))
                .condition(req -> req.subject().get("userId")
                        .equals(req.resource().get("ownerId")))
                .build());

        AbacRequest request = ownerRequest("bob", "alice");

        AbacDecision decision = engine.evaluate(request);

        assertThat(decision.permitted()).isFalse();
    }

    @Test
    @DisplayName("deny-overrides: DENY from any policy wins over PERMIT from others")
    void denyOverridesPermit() {
        // PERMIT policy
        engine.addPolicy(AbacPolicy.builder("permit-all-reads")
                .target(req -> "read".equals(req.action()))
                .condition(req -> true)
                .effect(AbacPolicy.Effect.PERMIT)
                .build());

        // DENY policy (run first due to registration order — deny overrides)
        engine.addPolicy(AbacPolicy.builder("deny-sensitive")
                .target(req -> "read".equals(req.action()))
                .condition(req -> "sensitive".equals(req.resource().get("classification")))
                .effect(AbacPolicy.Effect.DENY)
                .build());

        AbacRequest request = new AbacRequest(
                Map.of("userId", "user1"),
                Map.of("classification", "sensitive"),
                "read",
                Map.of());

        AbacDecision decision = engine.evaluate(request);

        assertThat(decision.permitted()).isFalse();
    }

    @Test
    @DisplayName("policy not applicable (target fails) is skipped")
    void policyNotApplicableIsSkipped() {
        // Policy only applies to "write", not "read"
        engine.addPolicy(AbacPolicy.builder("write-only-policy")
                .target(req -> "write".equals(req.action()))
                .condition(req -> false) // Would deny if applicable
                .build());

        AbacRequest request = AbacRequest.of("user1", "document", "read");

        AbacDecision decision = engine.evaluate(request);

        // No applicable policy → default deny (but not from our policy)
        assertThat(decision.permitted()).isFalse();
        assertThat(decision.matchedPolicyId()).isNull(); // default deny has null policyId
    }

    @Test
    @DisplayName("removePolicy removes policy by ID and returns true")
    void removePolicyByIdReturnsTrue() {
        AbacPolicy policy = AbacPolicy.builder("temp-policy").build();
        engine.addPolicy(policy);

        boolean removed = engine.removePolicy("temp-policy");

        assertThat(removed).isTrue();
        assertThat(engine.policyCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("removePolicy returns false when ID not found")
    void removePolicyReturnsFalseWhenNotFound() {
        boolean removed = engine.removePolicy("nonexistent-id");

        assertThat(removed).isFalse();
    }

    @Test
    @DisplayName("addPolicy throws NullPointerException for null policy")
    void addNullPolicyThrows() {
        assertThatThrownBy(() -> engine.addPolicy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("evaluate throws NullPointerException for null request")
    void evaluateNullRequestThrows() {
        assertThatThrownBy(() -> engine.evaluate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
