package com.ghatana.platform.policy.evaluation;

import com.ghatana.platform.pac.InMemoryPolicyEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Policy evaluation tests — validates evaluation with different input conditions,
 * risk score ranges, tenant scoping, and reason phrase correctness.
 *
 * @doc.type class
 * @doc.purpose Tests for policy evaluation correctness across inputs and tenants
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Policy Evaluation Tests [GH-90000]")
@Tag("integration [GH-90000]")
class PolicyEvaluationTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new InMemoryPolicyEngine(); // GH-90000

        // Register test policies
        engine.register("age-gate", input -> { // GH-90000
            Object age = input.get("age [GH-90000]");
            if (age instanceof Integer a && a >= 18) { // GH-90000
                return PolicyEvalResult.allow("age-gate [GH-90000]");
            }
            return PolicyEvalResult.deny("age-gate", List.of("must be 18 or older [GH-90000]"), 70);
        });

        engine.register("ip-allowlist", input -> { // GH-90000
            Object ip = input.get("clientIp [GH-90000]");
            if ("192.168.1.1".equals(ip) || "10.0.0.1".equals(ip)) { // GH-90000
                return PolicyEvalResult.allow("ip-allowlist [GH-90000]");
            }
            return PolicyEvalResult.deny("ip-allowlist", List.of("IP not in allowlist [GH-90000]"), 90);
        });

        engine.register("low-risk-allow", input -> { // GH-90000
            return new PolicyEvalResult(true, "low-risk-allow", List.of(), 10); // GH-90000
        });
    }

    // ── Evaluation with inputs ────────────────────────────────────────────────

    @Nested
    @DisplayName("evaluation with different input conditions [GH-90000]")
    class EvaluationWithInputs {

        @Test
        @DisplayName("age-gate allows input with age >= 18 [GH-90000]")
        void ageGate_allowsInput_withAgeGreaterOrEqual18() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "age-gate", Map.of("age", 25))); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("age-gate denies input with age < 18 [GH-90000]")
        void ageGate_deniesInput_withAgeLessThan18() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "age-gate", Map.of("age", 16))); // GH-90000

            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.reasons()).containsExactly("must be 18 or older [GH-90000]");
            assertThat(result.riskScore()).isEqualTo(70); // GH-90000
        }

        @Test
        @DisplayName("age-gate denies when age input is absent [GH-90000]")
        void ageGate_denies_whenAgeInputIsAbsent() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "age-gate", Map.of())); // GH-90000

            assertThat(result.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("ip-allowlist allows whitelisted IP [GH-90000]")
        void ipAllowlist_allowsWhitelistedIp() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "ip-allowlist", // GH-90000
                            Map.of("clientIp", "192.168.1.1"))); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ip-allowlist denies IP not on whitelist [GH-90000]")
        void ipAllowlist_deniesIpNotOnWhitelist() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "ip-allowlist", // GH-90000
                            Map.of("clientIp", "1.2.3.4"))); // GH-90000

            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(90); // GH-90000
        }
    }

    // ── Risk score ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("risk score ranges [GH-90000]")
    class RiskScoreRanges {

        @Test
        @DisplayName("allow result has risk score 0 [GH-90000]")
        void allowResult_hasRiskScore0() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "age-gate", Map.of("age", 30))); // GH-90000

            assertThat(result.riskScore()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("allowed result with non-zero risk score is valid [GH-90000]")
        void allowedResultWithNonZeroRiskScore_isValid() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "low-risk-allow", Map.of())); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("deny result risk score of 90 indicates high risk [GH-90000]")
        void denyResult_riskScore90_indicatesHighRisk() { // GH-90000
            PolicyEvalResult result = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "ip-allowlist", // GH-90000
                            Map.of("clientIp", "evil.corp"))); // GH-90000

            assertThat(result.riskScore()).isEqualTo(90); // GH-90000
        }
    }

    // ── Tenant scoping ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tenant scoping [GH-90000]")
    class TenantScoping {

        @Test
        @DisplayName("same policy evaluates consistently regardless of tenant ID [GH-90000]")
        void samePolicy_evaluatesConsistently_regardlessOfTenantId() { // GH-90000
            PolicyEvalResult tenantA = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-a", "age-gate", Map.of("age", 21))); // GH-90000
            PolicyEvalResult tenantB = runPromise( // GH-90000
                    () -> engine.evaluate("tenant-b", "age-gate", Map.of("age", 21))); // GH-90000

            assertThat(tenantA.allowed()).isEqualTo(tenantB.allowed()); // GH-90000
        }
    }

    // ── Exception in rule function ────────────────────────────────────────────

    @Nested
    @DisplayName("exception in rule function [GH-90000]")
    class ExceptionInRuleFunction {

        @Test
        @DisplayName("exception thrown by rule function propagates as Promise exception [GH-90000]")
        void exceptionThrownByRuleFunction_propagatesAsPromiseException() { // GH-90000
            engine.register("buggy-policy", input -> { // GH-90000
                throw new RuntimeException("simulated rule bug [GH-90000]");
            });

            RuntimeException caught = null;
            try {
                runPromise(() -> engine.evaluate("tenant-a", "buggy-policy", Map.of())); // GH-90000
            } catch (RuntimeException e) { // GH-90000
                caught = e;
            }

            assertThat(caught).isNotNull(); // GH-90000
            assertThat(caught.getMessage()).contains("simulated rule bug [GH-90000]");
        }
    }
}
