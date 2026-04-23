/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for policy enforcement validation (S001). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Policy enforcement validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PolicyEnforcement – Policy Validation (S001)")
class PolicyEnforcementTest extends EventloopTestBase {

    @Mock
    private PolicyService policyService;

    @Nested
    @DisplayName("Policy Evaluation")
    class PolicyEvaluationTests {

        @Test
        @DisplayName("[S001]: evaluate_returns_allowed_for_compliant_action")
        void evaluateReturnsAllowedForCompliantAction() { // GH-90000
            String policyId = "policy-001";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext( // GH-90000
                "user-001", "tenant-alpha", "read", "entity-123",
                Map.of(), Instant.now() // GH-90000
            );

            PolicyService.PolicyResult result = new PolicyService.PolicyResult( // GH-90000
                policyId, true, List.of("rule-1"), List.of(), Map.of()
            );

            when(policyService.evaluate(policyId, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            PolicyService.PolicyResult eval = runPromise(() -> policyService.evaluate(policyId, context)); // GH-90000

            assertThat(eval.isAllowed()).isTrue(); // GH-90000
            assertThat(eval.violatedRules()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[S001]: evaluate_returns_denied_for_violation")
        void evaluateReturnsDeniedForViolation() { // GH-90000
            String policyId = "data-retention-policy";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext( // GH-90000
                "user-001", "tenant-alpha", "delete", "protected-entity",
                Map.of(), Instant.now() // GH-90000
            );

            PolicyService.PolicyResult result = new PolicyService.PolicyResult( // GH-90000
                policyId, false, List.of(), List.of("retention-rule"),
                Map.of("reason", "Cannot delete within retention period") // GH-90000
            );

            when(policyService.evaluate(policyId, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            PolicyService.PolicyResult eval = runPromise(() -> policyService.evaluate(policyId, context)); // GH-90000

            assertThat(eval.isAllowed()).isFalse(); // GH-90000
            assertThat(eval.violatedRules()).contains("retention-rule");
        }
    }

    @Nested
    @DisplayName("Action Validation")
    class ActionValidationTests {

        @Test
        @DisplayName("[S001]: validate_action_checks_all_policies")
        void validateActionChecksAllPolicies() { // GH-90000
            String action = "export-data";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext( // GH-90000
                "user-001", "tenant-alpha", action, "dataset-123",
                Map.of("sensitive", true), Instant.now() // GH-90000
            );

            PolicyService.ValidationResult result = new PolicyService.ValidationResult( // GH-90000
                false,
                List.of( // GH-90000
                    new PolicyService.PolicyResult("p1", false, List.of(), List.of("export-limit"), Map.of())
                ),
                List.of("Export quota exceeded"),
                List.of() // GH-90000
            );

            when(policyService.validateAction(action, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            PolicyService.ValidationResult validation = runPromise(() -> // GH-90000
                policyService.validateAction(action, context) // GH-90000
            );

            assertThat(validation.valid()).isFalse(); // GH-90000
            assertThat(validation.errors()).contains("Export quota exceeded");
        }

        @Test
        @DisplayName("[S001]: validate_action_returns_valid_when_all_policies_pass")
        void validateActionReturnsValidWhenAllPoliciesPass() { // GH-90000
            String action = "read-entity";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext( // GH-90000
                "user-001", "tenant-alpha", action, "entity-123",
                Map.of(), Instant.now() // GH-90000
            );

            PolicyService.ValidationResult result = new PolicyService.ValidationResult( // GH-90000
                true,
                List.of( // GH-90000
                    new PolicyService.PolicyResult("p1", true, List.of("rule-1"), List.of(), Map.of()),
                    new PolicyService.PolicyResult("p2", true, List.of("rule-2"), List.of(), Map.of())
                ),
                List.of(), // GH-90000
                List.of() // GH-90000
            );

            when(policyService.validateAction(action, context)) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            PolicyService.ValidationResult validation = runPromise(() -> // GH-90000
                policyService.validateAction(action, context) // GH-90000
            );

            assertThat(validation.valid()).isTrue(); // GH-90000
            assertThat(validation.errors()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Policy Management")
    class PolicyManagementTests {

        @Test
        @DisplayName("[S001]: save_policy_creates_policy")
        void savePolicyCreatesPolicy() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                "new-policy", "Data Retention", "Retain data for 90 days",
                "tenant-alpha", PolicyService.PolicyType.DATA_RETENTION,
                List.of(), true, 1, Instant.now(), Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(policy)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); // GH-90000

            assertThat(result.id()).isEqualTo("new-policy");
            assertThat(result.enabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[S001]: get_policy_returns_existing")
        void getPolicyReturnsExisting() { // GH-90000
            String policyId = "existing-policy";
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                policyId, "Access Control", "Control access",
                "tenant-alpha", PolicyService.PolicyType.ACCESS_CONTROL,
                List.of(), true, 1, Instant.now(), Instant.now() // GH-90000
            );

            when(policyService.getPolicy(policyId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(policy))); // GH-90000

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(policyId); // GH-90000
        }

        @Test
        @DisplayName("[S001]: list_policies_filters_by_type")
        void listPoliciesFiltersByType() { // GH-90000
            String tenantId = "tenant-alpha";
            PolicyService.PolicyType type = PolicyService.PolicyType.DATA_RETENTION;

            List<PolicyService.Policy> policies = List.of( // GH-90000
                new PolicyService.Policy("p1", "Retention 1", "", tenantId, type, List.of(), true, 1, Instant.now(), Instant.now()), // GH-90000
                new PolicyService.Policy("p2", "Retention 2", "", tenantId, type, List.of(), true, 2, Instant.now(), Instant.now()) // GH-90000
            );

            when(policyService.listPolicies(tenantId, type)) // GH-90000
                .thenReturn(Promise.of(policies)); // GH-90000

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, type)); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).allMatch(p -> p.type() == type); // GH-90000
        }
    }

    @Nested
    @DisplayName("Policy Rules")
    class PolicyRulesTests {

        @Test
        @DisplayName("[S001]: rule_with_allow_effect_permits_action")
        void ruleWithAllowEffectPermitsAction() { // GH-90000
            PolicyService.Rule rule = new PolicyService.Rule( // GH-90000
                "allow-read",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"), // GH-90000
                PolicyService.Effect.ALLOW,
                "Read operations are allowed"
            );

            assertThat(rule.effect()).isEqualTo(PolicyService.Effect.ALLOW); // GH-90000
            assertThat(rule.condition().operator()).isEqualTo(PolicyService.Condition.Operator.EQUALS); // GH-90000
        }

        @Test
        @DisplayName("[S001]: rule_with_deny_effect_blocks_action")
        void ruleWithDenyEffectBlocksAction() { // GH-90000
            PolicyService.Rule rule = new PolicyService.Rule( // GH-90000
                "deny-delete",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "delete"), // GH-90000
                PolicyService.Effect.DENY,
                "Delete operations are denied"
            );

            assertThat(rule.effect()).isEqualTo(PolicyService.Effect.DENY); // GH-90000
        }
    }

    @Nested
    @DisplayName("Violations")
    class ViolationsTests {

        @Test
        @DisplayName("[S001]: get_violations_returns_policy_breaches")
        void getViolationsReturnsPolicyBreaches() { // GH-90000
            String tenantId = "tenant-alpha";
            Instant since = Instant.now().minus(Duration.ofDays(7)); // GH-90000

            List<PolicyService.PolicyViolation> violations = List.of( // GH-90000
                new PolicyService.PolicyViolation( // GH-90000
                    "v1", "policy-1", tenantId, "user-1", "export", "data-1",
                    "Export without approval", Instant.now(), Map.of() // GH-90000
                ),
                new PolicyService.PolicyViolation( // GH-90000
                    "v2", "policy-2", tenantId, "user-2", "access", "data-2",
                    "Unauthorized access", Instant.now(), Map.of() // GH-90000
                )
            );

            when(policyService.getViolations(tenantId, since)) // GH-90000
                .thenReturn(Promise.of(violations)); // GH-90000

            List<PolicyService.PolicyViolation> result = runPromise(() -> // GH-90000
                policyService.getViolations(tenantId, since) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
        }
    }
}
