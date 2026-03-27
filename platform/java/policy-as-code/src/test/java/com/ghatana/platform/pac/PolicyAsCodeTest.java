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
    void setUp() {
        engine = new InMemoryPolicyEngine();
    }

    @Nested
    @DisplayName("InMemoryPolicyEngine")
    class EngineTests {

        @Test
        @DisplayName("unregistered policy returns deny with riskScore=100")
        void unregisteredPolicyDenies() {
            PolicyEvalResult result = runPromise(() ->
                engine.evaluate("t1", "no_such_policy", Map.of()));
            assertThat(result.allowed()).isFalse();
            assertThat(result.riskScore()).isEqualTo(100);
            assertThat(result.reasons()).anyMatch(r -> r.contains("no_such_policy"));
        }

        @Test
        @DisplayName("registered allow policy returns allowed=true")
        void registeredAllowPolicyAllows() {
            engine.register("data_access", input -> PolicyEvalResult.allow("data_access"));
            PolicyEvalResult result = runPromise(() ->
                engine.evaluate("t1", "data_access", Map.of("userId", "u1")));
            assertThat(result.allowed()).isTrue();
            assertThat(result.riskScore()).isZero();
        }

        @Test
        @DisplayName("registered deny policy returns allowed=false with reasons")
        void registeredDenyPolicyDenies() {
            engine.register("strict_policy", input ->
                PolicyEvalResult.deny("strict_policy",
                    List.of("user is not in allowlist"), 80));

            PolicyEvalResult result = runPromise(() ->
                engine.evaluate("t1", "strict_policy", Map.of()));
            assertThat(result.allowed()).isFalse();
            assertThat(result.riskScore()).isEqualTo(80);
            assertThat(result.reasons()).containsExactly("user is not in allowlist");
        }

        @Test
        @DisplayName("policy can inspect input map")
        void policyInspectsInput() {
            engine.register("role_check", input -> {
                String role = (String) input.getOrDefault("role", "");
                if ("admin".equals(role)) return PolicyEvalResult.allow("role_check");
                return PolicyEvalResult.deny("role_check", List.of("requires admin role"), 60);
            });

            PolicyEvalResult admin = runPromise(() ->
                engine.evaluate("t1", "role_check", Map.of("role", "admin")));
            PolicyEvalResult user = runPromise(() ->
                engine.evaluate("t1", "role_check", Map.of("role", "viewer")));

            assertThat(admin.allowed()).isTrue();
            assertThat(user.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("PolicyEvalResult")
    class ResultTests {

        @Test
        @DisplayName("allow() factory creates an allowed result with riskScore=0")
        void allowFactory() {
            PolicyEvalResult result = PolicyEvalResult.allow("my_policy");
            assertThat(result.allowed()).isTrue();
            assertThat(result.riskScore()).isZero();
            assertThat(result.reasons()).isEmpty();
        }

        @Test
        @DisplayName("deny() factory creates a not-allowed result")
        void denyFactory() {
            PolicyEvalResult result = PolicyEvalResult.deny("my_policy", List.of("reason"), 50);
            assertThat(result.allowed()).isFalse();
            assertThat(result.riskScore()).isEqualTo(50);
        }

        @Test
        @DisplayName("riskScore out of [0,100] throws")
        void invalidRiskScoreThrows() {
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PolicyEvalResult(true, "p", List.of(), 101));
        }

        @Test
        @DisplayName("reasons list is immutable")
        void reasonsListIsImmutable() {
            PolicyEvalResult result = PolicyEvalResult.deny("p", List.of("r1"), 5);
            org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<String>) result.reasons()).add("r2"));
        }
    }
}
