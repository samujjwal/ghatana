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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryPolicyEngine} and {@link PolicyEvalResult}.
 */
@DisplayName("Policy-As-Code")
class PolicyAsCodeTest extends EventloopTestBase {

    private InMemoryPolicyEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new InMemoryPolicyEngine(); // GH-90000
    }

    @Nested
    @DisplayName("InMemoryPolicyEngine")
    class EngineTests {

        @Test
        @DisplayName("unregistered policy returns deny with riskScore=100")
        void unregisteredPolicyDenies() { // GH-90000
            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("t1", "no_such_policy", Map.of())); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(100); // GH-90000
            assertThat(result.reasons()).anyMatch(r -> r.contains("no_such_policy"));
        }

        @Test
        @DisplayName("registered allow policy returns allowed=true")
        void registeredAllowPolicyAllows() { // GH-90000
            engine.register("data_access", input -> PolicyEvalResult.allow("data_access"));
            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("t1", "data_access", Map.of("userId", "u1"))); // GH-90000
            assertThat(result.allowed()).isTrue(); // GH-90000
            assertThat(result.riskScore()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("registered deny policy returns allowed=false with reasons")
        void registeredDenyPolicyDenies() { // GH-90000
            engine.register("strict_policy", input -> // GH-90000
                PolicyEvalResult.deny("strict_policy", // GH-90000
                    List.of("user is not in allowlist"), 80));

            PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("t1", "strict_policy", Map.of())); // GH-90000
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(80); // GH-90000
            assertThat(result.reasons()).containsExactly("user is not in allowlist");
        }

        @Test
        @DisplayName("policy can inspect input map")
        void policyInspectsInput() { // GH-90000
            engine.register("role_check", input -> { // GH-90000
                String role = (String) input.getOrDefault("role", ""); // GH-90000
                if ("admin".equals(role)) return PolicyEvalResult.allow("role_check");
                return PolicyEvalResult.deny("role_check", List.of("requires admin role"), 60);
            });

            PolicyEvalResult admin = runPromise(() -> // GH-90000
                engine.evaluate("t1", "role_check", Map.of("role", "admin"))); // GH-90000
            PolicyEvalResult user = runPromise(() -> // GH-90000
                engine.evaluate("t1", "role_check", Map.of("role", "viewer"))); // GH-90000

            assertThat(admin.allowed()).isTrue(); // GH-90000
            assertThat(user.allowed()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("PolicyEvalResult")
    class ResultTests {

        @Test
        @DisplayName("allow() factory creates an allowed result with riskScore=0")
        void allowFactory() { // GH-90000
            PolicyEvalResult result = PolicyEvalResult.allow("my_policy");
            assertThat(result.allowed()).isTrue(); // GH-90000
            assertThat(result.riskScore()).isZero(); // GH-90000
            assertThat(result.reasons()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("deny() factory creates a not-allowed result")
        void denyFactory() { // GH-90000
            PolicyEvalResult result = PolicyEvalResult.deny("my_policy", List.of("reason"), 50);
            assertThat(result.allowed()).isFalse(); // GH-90000
            assertThat(result.riskScore()).isEqualTo(50); // GH-90000
        }

        @Test
        @DisplayName("riskScore out of [0,100] throws")
        void invalidRiskScoreThrows() { // GH-90000
            org.junit.jupiter.api.Assertions.assertThrows( // GH-90000
                IllegalArgumentException.class,
                () -> new PolicyEvalResult(true, "p", List.of(), 101)); // GH-90000
        }

        @Test
        @DisplayName("reasons list is immutable")
        void reasonsListIsImmutable() { // GH-90000
            PolicyEvalResult result = PolicyEvalResult.deny("p", List.of("r1"), 5);
            org.junit.jupiter.api.Assertions.assertThrows( // GH-90000
                UnsupportedOperationException.class,
                () -> ((List<String>) result.reasons()).add("r2"));
        }
    }
}
