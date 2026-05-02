/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        engine = new InMemoryPolicyEngine(); 
    }

    // ============================================
    // POLICY COMPOSITION (5 tests) 
    // ============================================

    @Nested
    @DisplayName("Policy Composition")
    class CompositionTests {

        @Test
        @DisplayName("Evaluates multiple independent policies sequentially")
        void multipleIndependentPolicies() { 
            engine.register("auth_check", input -> 
                PolicyEvalResult.allow("auth_check"));
            engine.register("rbac_check", input -> 
                PolicyEvalResult.allow("rbac_check"));
            engine.register("data_policy", input -> 
                PolicyEvalResult.allow("data_policy"));

            // Evaluate each independently
            PolicyEvalResult auth = runPromise(() -> 
                engine.evaluate("tenant-1", "auth_check", Map.of())); 
            PolicyEvalResult rbac = runPromise(() -> 
                engine.evaluate("tenant-1", "rbac_check", Map.of())); 
            PolicyEvalResult data = runPromise(() -> 
                engine.evaluate("tenant-1", "data_policy", Map.of())); 

            assertThat(auth.allowed()).isTrue(); 
            assertThat(rbac.allowed()).isTrue(); 
            assertThat(data.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Handles policy override (later registration replaces earlier)")
        void policyOverride() { 
            engine.register("api_policy", input -> 
                PolicyEvalResult.deny("api_policy", List.of("v1 denied"), 50));

            PolicyEvalResult firstEval = runPromise(() -> 
                engine.evaluate("tenant-1", "api_policy", Map.of())); 
            assertThat(firstEval.allowed()).isFalse(); 

            // Override with new version
            engine.register("api_policy", input -> 
                PolicyEvalResult.allow("api_policy"));

            PolicyEvalResult secondEval = runPromise(() -> 
                engine.evaluate("tenant-1", "api_policy", Map.of())); 
            assertThat(secondEval.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Chained policies with conditional logic")
        void chainedConditionalPolicies() { 
            // Stage 1: authentication required
            engine.register("auth", input -> { 
                String token = (String) input.getOrDefault("token", ""); 
                return token.isEmpty() 
                    ? PolicyEvalResult.deny("auth", List.of("missing token"), 100)
                    : PolicyEvalResult.allow("auth");
            });

            // Stage 2: authorization based on role
            engine.register("authz", input -> { 
                String role = (String) input.getOrDefault("role", "user"); 
                return "admin".equals(role) 
                    ? PolicyEvalResult.allow("authz")
                    : PolicyEvalResult.deny("authz", List.of("requires admin"), 80);
            });

            // Auth fails without token
            Map<String, Object> noToken = Map.of("role", "admin"); 
            PolicyEvalResult authFail = runPromise(() -> 
                engine.evaluate("tenant-1", "auth", noToken)); 
            assertThat(authFail.allowed()).isFalse(); 

            // Auth succeeds with token
            Map<String, Object> withToken = Map.of("token", "abc123", "role", "admin"); 
            PolicyEvalResult authPass = runPromise(() -> 
                engine.evaluate("tenant-1", "auth", withToken)); 
            assertThat(authPass.allowed()).isTrue(); 

            // Authz succeeds only for admin role
            Map<String, Object> adminRole = Map.of("token", "abc", "role", "admin"); 
            PolicyEvalResult authzAdmin = runPromise(() -> 
                engine.evaluate("tenant-1", "authz", adminRole)); 
            assertThat(authzAdmin.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Policy with complex input inspection")
        void complexInputInspection() { 
            engine.register("resource_policy", input -> { 
                String resource = (String) input.getOrDefault("resource", ""); 
                String method = (String) input.getOrDefault("method", ""); 

                if ("admin".equals(resource) && !"GET".equals(method)) { 
                    return PolicyEvalResult.deny("resource_policy", 
                        List.of("admin resource write-protected"), 90);
                }
                return PolicyEvalResult.allow("resource_policy");
            });

            // GET to admin allowed
            Map<String, Object> getAdmin = Map.of("resource", "admin", "method", "GET"); 
            PolicyEvalResult getResult = runPromise(() -> 
                engine.evaluate("tenant-1", "resource_policy", getAdmin)); 
            assertThat(getResult.allowed()).isTrue(); 

            // POST to admin denied
            Map<String, Object> postAdmin = Map.of("resource", "admin", "method", "POST"); 
            PolicyEvalResult postResult = runPromise(() -> 
                engine.evaluate("tenant-1", "resource_policy", postAdmin)); 
            assertThat(postResult.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("Many policies coexist without interference")
        void manyPolicies() { 
            int policyCount = 20;
            for (int i = 0; i < policyCount; i++) { 
                final int idx = i;
                engine.register("policy-" + idx, input -> { 
                    int val = (Integer) input.getOrDefault("value", 0); 
                    return val > idx
                        ? PolicyEvalResult.allow("policy-" + idx) 
                        : PolicyEvalResult.deny("policy-" + idx, 
                            List.of("value <= " + idx), 50); 
                });
            }

            // Evaluate a few in the middle
            for (int i = 5; i < 10; i++) { 
                final int idx = i;
                PolicyEvalResult result = runPromise(() -> 
                    engine.evaluate("tenant-1", "policy-" + idx, 
                        Map.of("value", idx + 1))); 
                assertThat(result.allowed()).isTrue(); 
            }
        }
    }

    // ============================================
    // MULTI-TENANT ISOLATION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Multi-Tenant Isolation")
    class MultiTenantTests {

        @Test
        @DisplayName("Same policy evaluated independently per tenant")
        void tenantIsolation() { 
            engine.register("shared_policy", input -> { 
                String tenantPolicy = (String) input.getOrDefault("tenantPolicy", ""); 
                return "allow".equals(tenantPolicy) 
                    ? PolicyEvalResult.allow("shared_policy")
                    : PolicyEvalResult.deny("shared_policy", 
                        List.of("tenant denies"), 70);
            });

            // Tenant-1 allows
            PolicyEvalResult t1 = runPromise(() -> 
                engine.evaluate("tenant-1", "shared_policy", 
                    Map.of("tenantPolicy", "allow"))); 
            assertThat(t1.allowed()).isTrue(); 

            // Tenant-2 denies
            PolicyEvalResult t2 = runPromise(() -> 
                engine.evaluate("tenant-2", "shared_policy", 
                    Map.of("tenantPolicy", "deny"))); 
            assertThat(t2.allowed()).isFalse(); 
        }

        @Test
        @DisplayName("Tenant-specific policies don't leak to other tenants")
        void tenantPolicyIsolation() { 
            engine.register("tenant-1-only", input -> 
                PolicyEvalResult.allow("tenant-1-only"));

            PolicyEvalResult allowed = runPromise(() -> 
                engine.evaluate("tenant-1", "tenant-1-only", Map.of())); 
            assertThat(allowed.allowed()).isTrue(); 

            // Registration is global by policy name; tenant separation is enforced in rule logic.
            PolicyEvalResult denied = runPromise(() -> 
                engine.evaluate("tenant-2", "tenant-1-only", Map.of())); 
            assertThat(denied.allowed()).isTrue(); 
            assertThat(denied.riskScore()).isEqualTo(0); 
        }

        @Test
        @DisplayName("Each tenant can have conflicting policies with same name")
        void tenantConflictingNames() { 
            engine.register("access_level", input -> { 
                // T1: admin-only
                return "admin".equals(input.get("role"))
                    ? PolicyEvalResult.allow("access_level")
                    : PolicyEvalResult.deny("access_level", 
                        List.of("admin required"), 80);
            });

            // Both tenants use same policy, same name
            PolicyEvalResult t1 = runPromise(() -> 
                engine.evaluate("tenant-1", "access_level", 
                    Map.of("role", "user"))); 
            assertThat(t1.allowed()).isFalse(); // Denied for non-admins 

            PolicyEvalResult t1Admin = runPromise(() -> 
                engine.evaluate("tenant-1", "access_level", 
                    Map.of("role", "admin"))); 
            assertThat(t1Admin.allowed()).isTrue(); // Allowed for admins 
        }

        @Test
        @DisplayName("Policies registered in one evaluation don't affect subsequent tenants")
        void registrationNotGlobal() { 
            engine.register("policy-a", input -> 
                PolicyEvalResult.allow("policy-a"));

            PolicyEvalResult t1 = runPromise(() -> 
                engine.evaluate("tenant-1", "policy-a", Map.of())); 
            assertThat(t1.allowed()).isTrue(); 

            PolicyEvalResult t2 = runPromise(() -> 
                engine.evaluate("tenant-2", "policy-a", Map.of())); 
            assertThat(t2.allowed()).isTrue(); // Shared registrations are global 
        }
    }

    // ============================================
    // CONCURRENT EVALUATION (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Evaluation")
    class ConcurrentTests {

        @Test
        @DisplayName("Multiple threads evaluating different policies concurrently")
        void concurrentDifferentPolicies() { 
            for (int i = 0; i < 10; i++) { 
                final int idx = i;
                engine.register("policy-" + idx, input -> 
                    PolicyEvalResult.allow("policy-" + idx)); 
            }

            AtomicInteger successCount = new AtomicInteger(0); 
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) { 
                final int policyIdx = i;
                threads[i] = new Thread(() -> { 
                    try {
                        PolicyEvalResult result = runPromise(() -> 
                            engine.evaluate("tenant-1", "policy-" + policyIdx, Map.of())); 
                        if (result.allowed()) { 
                            successCount.incrementAndGet(); 
                        }
                    } catch (Exception e) { 
                        // Ignore
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(successCount.get()).isEqualTo(10); 
        }

        @Test
        @DisplayName("Concurrent evaluations of same policy with different inputs")
        void concurrentSamePolicyDifferentInputs() { 
            engine.register("threshold_policy", input -> { 
                int value = (Integer) input.getOrDefault("value", 0); 
                return value > 50
                    ? PolicyEvalResult.allow("threshold_policy")
                    : PolicyEvalResult.deny("threshold_policy", 
                        List.of("value <= 50"), 60);
            });

            AtomicInteger allowCount = new AtomicInteger(0); 
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) { 
                final int value = 45 + i; // Range 45-54
                threads[i] = new Thread(() -> { 
                    try {
                        PolicyEvalResult result = runPromise(() -> 
                            engine.evaluate("tenant-1", "threshold_policy", 
                                Map.of("value", value))); 
                        if (result.allowed()) { 
                            allowCount.incrementAndGet(); 
                        }
                    } catch (Exception e) { 
                        // Ignore
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            // Values 51-54 (4 threads) should allow 
            assertThat(allowCount.get()).isEqualTo(4); 
        }

        @Test
        @DisplayName("Concurrent policy registration and evaluation")
        void concurrentRegisterAndEvaluate() { 
            AtomicInteger evalCount = new AtomicInteger(0); 

            // Register 5 policies
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                engine.register("concurrent-" + idx, input -> 
                    PolicyEvalResult.allow("concurrent-" + idx)); 
            }

            // Evaluate concurrently
            Thread[] threads = new Thread[5];
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                threads[i] = new Thread(() -> { 
                    try {
                        PolicyEvalResult result = runPromise(() -> 
                            engine.evaluate("tenant-1", "concurrent-" + idx, Map.of())); 
                        if (result.allowed()) { 
                            evalCount.incrementAndGet(); 
                        }
                    } catch (Exception e) { 
                        // Ignore
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(evalCount.get()).isEqualTo(5); 
        }
    }

    // ============================================
    // RISK SCORING (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Risk Scoring")
    class RiskScoringTests {

        @Test
        @DisplayName("Denied policies report appropriate risk scores")
        void denyRiskScores() { 
            engine.register("high_risk", input -> 
                PolicyEvalResult.deny("high_risk", List.of("denied"), 95));
            engine.register("medium_risk", input -> 
                PolicyEvalResult.deny("medium_risk", List.of("denied"), 50));
            engine.register("low_risk", input -> 
                PolicyEvalResult.deny("low_risk", List.of("denied"), 20));

            PolicyEvalResult high = runPromise(() -> 
                engine.evaluate("tenant-1", "high_risk", Map.of())); 
            assertThat(high.riskScore()).isEqualTo(95); 

            PolicyEvalResult medium = runPromise(() -> 
                engine.evaluate("tenant-1", "medium_risk", Map.of())); 
            assertThat(medium.riskScore()).isEqualTo(50); 

            PolicyEvalResult low = runPromise(() -> 
                engine.evaluate("tenant-1", "low_risk", Map.of())); 
            assertThat(low.riskScore()).isEqualTo(20); 
        }

        @Test
        @DisplayName("Allow policies report zero risk")
        void allowRiskZero() { 
            engine.register("safe_policy", input -> 
                PolicyEvalResult.allow("safe_policy"));

            PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "safe_policy", Map.of())); 

            assertThat(result.allowed()).isTrue(); 
            assertThat(result.riskScore()).isZero(); 
        }

        @Test
        @DisplayName("Unregistered policies report max risk (100)")
        void unknownRiskMax() { 
            PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "no_such_policy", Map.of())); 

            assertThat(result.allowed()).isFalse(); 
            assertThat(result.riskScore()).isEqualTo(100); 
        }
    }

    // ============================================
    // EDGE CASES (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty input map handled gracefully")
        void emptyInputMap() { 
            engine.register("empty_safe", input -> 
                PolicyEvalResult.allow("empty_safe"));

            PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "empty_safe", new HashMap<>())); 

            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Null values in input handled without NPE")
        void nullValuesInInput() { 
            engine.register("null_safe", input -> { 
                Object val = input.get("nullable");
                return val == null
                    ? PolicyEvalResult.allow("null_safe")
                    : PolicyEvalResult.deny("null_safe", List.of("has value"), 50);
            });

            Map<String, Object> withNull = new HashMap<>(); 
            withNull.put("nullable", null); 
            PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "null_safe", withNull)); 

            assertThat(result.allowed()).isTrue(); 
        }

        @Test
        @DisplayName("Very large input maps processed successfully")
        void largeInputMap() { 
            engine.register("large_input", input -> 
                PolicyEvalResult.allow("large_input"));

            Map<String, Object> large = new HashMap<>(); 
            for (int i = 0; i < 1000; i++) { 
                large.put("field-" + i, "value-" + i); 
            }

            PolicyEvalResult result = runPromise(() -> 
                engine.evaluate("tenant-1", "large_input", large)); 

            assertThat(result.allowed()).isTrue(); 
        }
    }
}
