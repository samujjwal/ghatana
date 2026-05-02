package com.ghatana.platform.policy.compilation;

import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Policy compilation tests — validates policy registration, overwrite behavior,
 * invalid risk score rejection, and rule function registration.
 *
 * @doc.type class
 * @doc.purpose Tests for policy-as-code compilation (registration and rule function lifecycle) 
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Policy Compilation Tests")
@Tag("integration")
class PolicyCompilationTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;

    @BeforeEach
    void setUp() { 
        engine = new InMemoryPolicyEngine(); 
    }

    // ── Policy registration ───────────────────────────────────────────────────

    @Nested
    @DisplayName("policy registration")
    class PolicyRegistration {

        @Test
        @DisplayName("registered policy is evaluatable by name")
        void registeredPolicy_isEvaluatableByName() { 
            engine.register("allow-all", input -> PolicyEvalResult.allow("allow-all"));

            PolicyEvalResult result = runPromise( 
                    () -> engine.evaluate("tenant-a", "allow-all", Map.of())); 

            assertThat(result.allowed()).isTrue(); 
            assertThat(result.policyName()).isEqualTo("allow-all");
        }

        @Test
        @DisplayName("unregistered policy defaults to DENY with clear reason")
        void unregisteredPolicy_defaultsToDenyWithClearReason() { 
            PolicyEvalResult result = runPromise( 
                    () -> engine.evaluate("tenant-a", "unknown-policy", Map.of())); 

            assertThat(result.allowed()).isFalse(); 
            assertThat(result.reasons()).isNotEmpty(); 
            assertThat(result.reasons().getFirst()).contains("unknown-policy");
        }

        @Test
        @DisplayName("overwriting a policy replaces the old rule")
        void overwritingPolicy_replacesOldRule() { 
            engine.register("my-policy", input -> PolicyEvalResult.allow("my-policy"));
            engine.register("my-policy", input -> PolicyEvalResult.deny("my-policy", 
                    java.util.List.of("explicit deny"), 80));

            PolicyEvalResult result = runPromise( 
                    () -> engine.evaluate("tenant-a", "my-policy", Map.of())); 

            assertThat(result.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("multiple policies are registered independently")
        void multiplePolicies_registeredIndependently() { 
            engine.register("policy-a", input -> PolicyEvalResult.allow("policy-a"));
            engine.register("policy-b", input -> PolicyEvalResult.deny("policy-b", 
                    java.util.List.of("denied"), 50));

            PolicyEvalResult a = runPromise(() -> engine.evaluate("tenant-a", "policy-a", Map.of())); 
            PolicyEvalResult b = runPromise(() -> engine.evaluate("tenant-a", "policy-b", Map.of())); 

            assertThat(a.allowed()).isTrue(); 
            assertThat(b.allowed()).isFalse(); 
        }
    }

    // ── PolicyEvalResult construction ─────────────────────────────────────────

    @Nested
    @DisplayName("PolicyEvalResult construction validation")
    class PolicyEvalResultConstruction {

        @Test
        @DisplayName("risk score below 0 throws IllegalArgumentException")
        void riskScoreBelow0_throwsIllegalArgumentException() { 
            assertThatThrownBy(() -> PolicyEvalResult.deny("policy", java.util.List.of("reason"), -1))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("riskScore");
        }

        @Test
        @DisplayName("risk score above 100 throws IllegalArgumentException")
        void riskScoreAbove100_throwsIllegalArgumentException() { 
            assertThatThrownBy(() -> PolicyEvalResult.deny("policy", java.util.List.of("reason"), 101))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("riskScore");
        }

        @Test
        @DisplayName("allow factory produces result with riskScore 0 and empty reasons")
        void allowFactory_producesResultWithRiskScore0AndEmptyReasons() { 
            PolicyEvalResult result = PolicyEvalResult.allow("test-policy");

            assertThat(result.allowed()).isTrue(); 
            assertThat(result.riskScore()).isEqualTo(0); 
            assertThat(result.reasons()).isEmpty(); 
        }

        @Test
        @DisplayName("deny factory produces immutable reasons list")
        void denyFactory_producesImmutableReasonsList() { 
            PolicyEvalResult result = PolicyEvalResult.deny("p", java.util.List.of("r1"), 50);

            assertThatThrownBy(() -> result.reasons().add("r2"))
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }
}
