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
    void setUp() { // GH-90000
        engine = new AbacEngine(); // GH-90000
    }

    private AbacRequest ownerRequest(String userId, String ownerId) { // GH-90000
        return new AbacRequest( // GH-90000
                Map.of("userId", userId), // GH-90000
                Map.of("ownerId", ownerId, "type", "document"), // GH-90000
                "write",
                Map.of()); // GH-90000
    }

    @Test
    @DisplayName("evaluate returns deny when no policies registered")
    void evaluateReturnsDenyWhenNoPolicies() { // GH-90000
        AbacRequest request = AbacRequest.of("user1", "document", "read"); // GH-90000

        AbacDecision decision = engine.evaluate(request); // GH-90000

        assertThat(decision.permitted()).isFalse(); // GH-90000
        assertThat(decision.reason()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("policyCount is zero on fresh engine")
    void policyCountIsZeroInitially() { // GH-90000
        assertThat(engine.policyCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("addPolicy increments policy count")
    void addPolicyIncrementsPolicyCount() { // GH-90000
        AbacPolicy policy = AbacPolicy.builder("test-policy")
                .condition(req -> true) // GH-90000
                .build(); // GH-90000

        engine.addPolicy(policy); // GH-90000

        assertThat(engine.policyCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("evaluate returns permit when applicable policy condition is met")
    void evaluatePermitsWhenConditionMet() { // GH-90000
        engine.addPolicy(AbacPolicy.builder("owner-read")
                .description("Owner can read")
                .target(req -> "read".equals(req.action())) // GH-90000
                .condition(req -> req.subject().get("userId")
                        .equals(req.resource().get("ownerId")))
                .build()); // GH-90000

        AbacRequest request = new AbacRequest( // GH-90000
                Map.of("userId", "alice"), // GH-90000
                Map.of("ownerId", "alice"), // GH-90000
                "read",
                Map.of()); // GH-90000

        AbacDecision decision = engine.evaluate(request); // GH-90000

        assertThat(decision.permitted()).isTrue(); // GH-90000
        assertThat(decision.matchedPolicyId()).isEqualTo("owner-read");
    }

    @Test
    @DisplayName("evaluate returns deny when condition is not met")
    void evaluateDeniesWhenConditionNotMet() { // GH-90000
        engine.addPolicy(AbacPolicy.builder("owner-only")
                .description("Only owner can write")
                .target(req -> "write".equals(req.action())) // GH-90000
                .condition(req -> req.subject().get("userId")
                        .equals(req.resource().get("ownerId")))
                .build()); // GH-90000

        AbacRequest request = ownerRequest("bob", "alice"); // GH-90000

        AbacDecision decision = engine.evaluate(request); // GH-90000

        assertThat(decision.permitted()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("deny-overrides: DENY from any policy wins over PERMIT from others")
    void denyOverridesPermit() { // GH-90000
        // PERMIT policy
        engine.addPolicy(AbacPolicy.builder("permit-all-reads")
                .target(req -> "read".equals(req.action())) // GH-90000
                .condition(req -> true) // GH-90000
                .effect(AbacPolicy.Effect.PERMIT) // GH-90000
                .build()); // GH-90000

        // DENY policy (run first due to registration order — deny overrides) // GH-90000
        engine.addPolicy(AbacPolicy.builder("deny-sensitive")
                .target(req -> "read".equals(req.action())) // GH-90000
                .condition(req -> "sensitive".equals(req.resource().get("classification")))
                .effect(AbacPolicy.Effect.DENY) // GH-90000
                .build()); // GH-90000

        AbacRequest request = new AbacRequest( // GH-90000
                Map.of("userId", "user1"), // GH-90000
                Map.of("classification", "sensitive"), // GH-90000
                "read",
                Map.of()); // GH-90000

        AbacDecision decision = engine.evaluate(request); // GH-90000

        assertThat(decision.permitted()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("policy not applicable (target fails) is skipped")
    void policyNotApplicableIsSkipped() { // GH-90000
        // Policy only applies to "write", not "read"
        engine.addPolicy(AbacPolicy.builder("write-only-policy")
                .target(req -> "write".equals(req.action())) // GH-90000
                .condition(req -> false) // Would deny if applicable // GH-90000
                .build()); // GH-90000

        AbacRequest request = AbacRequest.of("user1", "document", "read"); // GH-90000

        AbacDecision decision = engine.evaluate(request); // GH-90000

        // No applicable policy → default deny (but not from our policy) // GH-90000
        assertThat(decision.permitted()).isFalse(); // GH-90000
        assertThat(decision.matchedPolicyId()).isNull(); // default deny has null policyId // GH-90000
    }

    @Test
    @DisplayName("removePolicy removes policy by ID and returns true")
    void removePolicyByIdReturnsTrue() { // GH-90000
        AbacPolicy policy = AbacPolicy.builder("temp-policy").build();
        engine.addPolicy(policy); // GH-90000

        boolean removed = engine.removePolicy("temp-policy");

        assertThat(removed).isTrue(); // GH-90000
        assertThat(engine.policyCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("removePolicy returns false when ID not found")
    void removePolicyReturnsFalseWhenNotFound() { // GH-90000
        boolean removed = engine.removePolicy("nonexistent-id");

        assertThat(removed).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("addPolicy throws NullPointerException for null policy")
    void addNullPolicyThrows() { // GH-90000
        assertThatThrownBy(() -> engine.addPolicy(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("evaluate throws NullPointerException for null request")
    void evaluateNullRequestThrows() { // GH-90000
        assertThatThrownBy(() -> engine.evaluate(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
