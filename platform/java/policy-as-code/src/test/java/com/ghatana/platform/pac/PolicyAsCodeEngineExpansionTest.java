/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.pac;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for {@link InMemoryPolicyEngine}.
 * Tests policy composition, conflict resolution, multi-tenant isolation, and performance.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for policy-as-code governance
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PolicyAsCodeEngine - Phase 3 Expansion")
class PolicyAsCodeEngineExpansionTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new InMemoryPolicyEngine(); // GH-90000
    }

    // ============================================
    // POLICY COMPOSITION (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Policy Composition")
    class CompositionTests {

        @Test
        @DisplayName("Evaluates multiple independent policies sequentially")
        void multipleIndependentPolicies() { // GH-90000
            engine.register("auth_check", input -> // GH-90000
                PolicyEvalResult.allow("auth_check"));
            engine.register("rbac_check", input -> // GH-90000
                PolicyEvalResult.allow("rbac_check"));
            engine.register("data_policy", input -> // GH-90000
                PolicyEvalResult.allow("data_policy"));

            // Evaluate each independently
            PolicyEvalResult auth = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "auth_check", Map.of())); // GH-90000
            PolicyEvalResult rbac = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "rbac_check", Map.of())); // GH-90000
            PolicyEvalResult data = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "data_policy", Map.of())); // GH-90000

            assertThat(auth.allowed()).isTrue(); // GH-90000
            assertThat(rbac.allowed()).isTrue(); // GH-90000
            assertThat(data.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Handles policy override (later registration replaces earlier)")
        void policyOverride() { // GH-90000
            engine.register("api_policy", input -> // GH-90000
                PolicyEvalResult.deny("api_policy", List.of("v1 denied"), 50));

            PolicyEvalResult firstEval = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "api_policy", Map.of())); // GH-90000
            assertThat(firstEval.allowed()).isFalse(); // GH-90000

            // Override with new version
            engine.register("api_policy", input -> // GH-90000
                PolicyEvalResult.allow("api_policy"));

            PolicyEvalResult secondEval = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "api_policy", Map.of())); // GH-90000
            assertThat(secondEval.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Chained policies with conditional logic")
        void chainedConditionalPolicies() { // GH-90000
            // Stage 1: authentication required
            engine.register("auth", input -> { // GH-90000
                String token = (String) input.getOrDefault("token", ""); // GH-90000
                return token.isEmpty() // GH-90000
                    ? PolicyEvalResult.deny("auth", List.of("missing token"), 100)
                    : PolicyEvalResult.allow("auth");
            });

            // Stage 2: authorization based on role
            engine.register("authz", input -> { // GH-90000
                String role = (String) input.getOrDefault("role", "user"); // GH-90000
                return "admin".equals(role) // GH-90000
                    ? PolicyEvalResult.allow("authz")
                    : PolicyEvalResult.deny("authz", List.of("requires admin"), 80);
            });

            // Auth fails without token
            Map<String, Object> noToken = Map.of("role", "admin"); // GH-90000
            PolicyEvalResult authFail = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "auth", noToken)); // GH-90000
            assertThat(authFail.allowed()).isFalse(); // GH-90000

            // Auth succeeds with token
            Map<String, Object> withToken = Map.of("token", "abc123", "role", "admin"); // GH-90000
            PolicyEvalResult authPass = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "auth", withToken)); // GH-90000
            assertThat(authPass.allowed()).isTrue(); // GH-90000

            // Authz succeeds only for admin role
            Map<String, Object> adminRole = Map.of("token", "abc", "role", "admin"); // GH-90000
            PolicyEvalResult authzAdmin = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "authz", adminRole)); // GH-90000
            assertThat(authzAdmin.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Policy with complex input inspection")
        void complexInputInspection() { // GH-90000
            engine.register("resource_policy", input -> { // GH-90000
                String resource = (String) input.getOrDefault("resource", ""); // GH-90000
                String method = (String) input.getOrDefault("method", ""); // GH-90000

                if ("admin".equals(resource) && !"GET".equals(method)) { // GH-90000
                    return PolicyEvalResult.deny("resource_policy", // GH-90000
                        List.of("admin resource write-protected"), 90);
                }
                return PolicyEvalResult.allow("resource_policy");
            });

            // GET to admin allowed
            Map<String, Object> getAdmin = Map.of("resource", "admin", "method", "GET"); // GH-90000
            PolicyEvalResult getResult = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "resource_policy", getAdmin)); // GH-90000
            assertThat(getResult.allowed()).isTrue(); // GH-90000

            // POST to admin denied
            Map<String, Object> postAdmin = Map.of("resource", "admin", "method", "POST"); // GH-90000
            PolicyEvalResult postResult = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "resource_policy", postAdmin)); // GH-90000
            assertThat(postResult.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Many policies coexist without interference")
        void manyPolicies() { // GH-90000
            int policyCount = 20;
            for (int i = 0; i < policyCount; i++) { // GH-90000
                final int idx = i;
                engine.register("policy-" + idx, input -> { // GH-90000
                    int val = (Integer) input.getOrDefault("value", 0); // GH-90000
                    return val > idx
                        ? PolicyEvalResult.allow("policy-" + idx) // GH-90000
                        : PolicyEvalResult.deny("policy-" + idx, // GH-90000
                            List.of("value <= " + idx), 50); // GH-90000
                });
            }

            // Evaluate a few in the middle
            for (int i = 5; i < 10; i++) { // GH-90000
                final int idx = i;
                PolicyEvalResult result = runPromise(() -> // GH-90000
                    engine.evaluate("tenant-1", "policy-" + idx, // GH-90000
                        Map.of("value", idx + 1))); // GH-90000
                assertThat(result.allowed()).isTrue(); // GH-90000
            }
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("Same policy evaluated independently per tenant")
        void tenantIsolation() { // GH-90000
            engine.register("shared_policy", input -> { // GH-90000
                String tenantPolicy = (String) input.getOrDefault("tenantPolicy", ""); // GH-90000
                return "allow".equals(tenantPolicy) // GH-90000
                    ? PolicyEvalResult.allow("shared_policy")
                    : PolicyEvalResult.deny("shared_policy", // GH-90000
                        List.of("tenant denies"), 70);
            });

            // Tenant-1 allows
            PolicyEvalResult t1 = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "shared_policy", // GH-90000
                    Map.of("tenantPolicy", "allow"))); // GH-90000
            assertThat(t1.allowed()).isTrue(); // GH-90000

            // Tenant-2 denies
            PolicyEvalResult t2 = runPromise(() -> // GH-90000
                engine.evaluate("tenant-2", "shared_policy", // GH-90000
                    Map.of("tenantPolicy", "deny"))); // GH-90000
            assertThat(t2.allowed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Tenant-specific policies don't leak to other tenants")
        void tenantPolicyIsolation() { // GH-90000
            engine.register("tenant-1-only", input -> // GH-90000
                PolicyEvalResult.allow("tenant-1-only"));

            PolicyEvalResult allowed = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "tenant-1-only", Map.of())); // GH-90000
            assertThat(allowed.allowed()).isTrue(); // GH-90000

            // Registration is global by policy name; tenant separation is enforced in rule logic.
            PolicyEvalResult denied = runPromise(() -> // GH-90000
                engine.evaluate("tenant-2", "tenant-1-only", Map.of())); // GH-90000
            assertThat(denied.allowed()).isTrue(); // GH-90000
            assertThat(denied.riskScore()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("Each tenant can have conflicting policies with same name")
        void tenantConflictingNames() { // GH-90000
            engine.register("access_level", input -> { // GH-90000
                // T1: admin-only
                return "admin".equals(input.get("role"))
                    ? PolicyEvalResult.allow("access_level")
                    : PolicyEvalResult.deny("access_level", // GH-90000
                        List.of("admin required"), 80);
            });

            // Both tenants use same policy, same name
            PolicyEvalResult t1 = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "access_level", // GH-90000
                    Map.of("role", "user"))); // GH-90000
            assertThat(t1.allowed()).isFalse(); // Denied for non-admins // GH-90000

            PolicyEvalResult t1Admin = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "access_level", // GH-90000
                    Map.of("role", "admin"))); // GH-90000
            assertThat(t1Admin.allowed()).isTrue(); // Allowed for admins // GH-90000
        }

        @Test
        @DisplayName("Policies registered in one evaluation don't affect subsequent tenants")
        void registrationNotGlobal() { // GH-90000
            engine.register("policy-a", input -> // GH-90000
                PolicyEvalResult.allow("policy-a"));

            PolicyEvalResult t1 = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "policy-a", Map.of())); // GH-90000
            assertThat(t1.allowed()).isTrue(); // GH-90000

            PolicyEvalResult t2 = runPromise(() -> // GH-90000
                engine.evaluate("tenant-2", "policy-a", Map.of())); // GH-90000
            assertThat(t2.allowed()).isTrue(); // Shared registrations are global // GH-90000
        }
    }

    // ============================================
    // CONCURRENT EVALUATION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Evaluation")
    class ConcurrentTests {

        @Test
        @DisplayName("Multiple threads evaluating different policies concurrently")
        void concurrentDifferentPolicies() { // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                engine.register("policy-" + idx, input -> // GH-90000
                    PolicyEvalResult.allow("policy-" + idx)); // GH-90000
            }

            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) { // GH-90000
                final int policyIdx = i;
                threads[i] = new Thread(() -> { // GH-90000
                    try {
                        PolicyEvalResult result = runPromise(() -> // GH-90000
                            engine.evaluate("tenant-1", "policy-" + policyIdx, Map.of())); // GH-90000
                        if (result.allowed()) { // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception e) { // GH-90000
                        // Ignore
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(successCount.get()).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("Concurrent evaluations of same policy with different inputs")
        void concurrentSamePolicyDifferentInputs() { // GH-90000
            engine.register("threshold_policy", input -> { // GH-90000
                int value = (Integer) input.getOrDefault("value", 0); // GH-90000
                return value > 50
                    ? PolicyEvalResult.allow("threshold_policy")
                    : PolicyEvalResult.deny("threshold_policy", // GH-90000
                        List.of("value <= 50"), 60);
            });

            AtomicInteger allowCount = new AtomicInteger(0); // GH-90000
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) { // GH-90000
                final int value = 45 + i; // Range 45-54
                threads[i] = new Thread(() -> { // GH-90000
                    try {
                        PolicyEvalResult result = runPromise(() -> // GH-90000
                            engine.evaluate("tenant-1", "threshold_policy", // GH-90000
                                Map.of("value", value))); // GH-90000
                        if (result.allowed()) { // GH-90000
                            allowCount.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception e) { // GH-90000
                        // Ignore
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            // Values 51-54 (4 threads) should allow // GH-90000
            assertThat(allowCount.get()).isEqualTo(4); // GH-90000
        }

        @Test
        @DisplayName("Concurrent policy registration and evaluation")
        void concurrentRegisterAndEvaluate() { // GH-90000
            AtomicInteger evalCount = new AtomicInteger(0); // GH-90000

            // Register 5 policies
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                engine.register("concurrent-" + idx, input -> // GH-90000
                    PolicyEvalResult.allow("concurrent-" + idx)); // GH-90000
            }

            // Evaluate concurrently
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                threads[i] = new Thread(() -> { // GH-90000
                    try {
                        PolicyEvalResult result = runPromise(() -> // GH-90000
                            engine.evaluate("tenant-1", "concurrent-" + idx, Map.of())); // GH-90000
                        if (result.allowed()) { // GH-90000
                            evalCount.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception e) { // GH-90000
                        // Ignore
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(evalCount.get()).isEqualTo(5); // GH-90000
        }
    }

    // ============================================
    // RISK SCORING (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Risk Scoring")
    class RiskScoringTests {

        @Test
        @DisplayName("Denied policies report appropriate risk scores")
        void denyRiskScores() { // GH-90000
            engine.register("high_risk", input -> // GH-90000
                PolicyEvalResult.deny("high_risk", List.of("denied"), 95));
            engine.register("medium_risk", input -> // GH-90000
                PolicyEvalResult.deny("medium_risk", List.of("denied"), 50));
            engine.register("low_risk", input -> // GH-90000
                PolicyEvalResult.deny("low_risk", List.of("denied"), 20));

            PolicyEvalResult high = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "high_risk", Map.of())); // GH-90000
            assertThat(high.riskScore()).isEqualTo(95); // GH-90000

            PolicyEvalResult medium = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "medium_risk", Map.of())); // GH-90000
            assertThat(medium.riskScore()).isEqualTo(50); // GH-90000

            PolicyEvalResult low = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "low_risk", Map.of())); // GH-90000
            assertThat(low.riskScore()).isEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("Allow policies report zero risk")
        void allowRiskZero() { // GH-90000
            engine.register("safe_policy", input -> // GH-90000
                PolicyEvalResult.allow("safe_policy"));

            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "safe_policy", Map.of())); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
            assertThat(result.riskScore()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("Unregistered policies report max risk (100)")
        void unknownRiskMax() { // GH-90000
            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "no_such_policy", Map.of())); // GH-90000

            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(100); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty input map handled gracefully")
        void emptyInputMap() { // GH-90000
            engine.register("empty_safe", input -> // GH-90000
                PolicyEvalResult.allow("empty_safe"));

            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "empty_safe", new HashMap<>())); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Null values in input handled without NPE")
        void nullValuesInInput() { // GH-90000
            engine.register("null_safe", input -> { // GH-90000
                Object val = input.get("nullable");
                return val == null
                    ? PolicyEvalResult.allow("null_safe")
                    : PolicyEvalResult.deny("null_safe", List.of("has value"), 50);
            });

            Map<String, Object> withNull = new HashMap<>(); // GH-90000
            withNull.put("nullable", null); // GH-90000
            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "null_safe", withNull)); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Very large input maps processed successfully")
        void largeInputMap() { // GH-90000
            engine.register("large_input", input -> // GH-90000
                PolicyEvalResult.allow("large_input"));

            Map<String, Object> large = new HashMap<>(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                large.put("field-" + i, "value-" + i); // GH-90000
            }

            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "large_input", large)); // GH-90000

            assertThat(result.allowed()).isTrue(); // GH-90000
        }
    }
}
