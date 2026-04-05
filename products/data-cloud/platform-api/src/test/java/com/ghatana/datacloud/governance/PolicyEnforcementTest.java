/*
 * Copyright (c) 2026 Ghatana Inc.
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for policy enforcement validation (S001).
 *
 * @doc.type class
 * @doc.purpose Policy enforcement validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyEnforcement – Policy Validation (S001)")
class PolicyEnforcementTest extends EventloopTestBase {

    @Mock
    private PolicyService policyService;

    @Nested
    @DisplayName("Policy Evaluation")
    class PolicyEvaluationTests {

        @Test
        @DisplayName("[S001]: evaluate_returns_allowed_for_compliant_action")
        void evaluateReturnsAllowedForCompliantAction() {
            String policyId = "policy-001";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext(
                "user-001", "tenant-alpha", "read", "entity-123",
                Map.of(), Instant.now()
            );

            PolicyService.PolicyResult result = new PolicyService.PolicyResult(
                policyId, true, List.of("rule-1"), List.of(), Map.of()
            );

            when(policyService.evaluate(policyId, context))
                .thenReturn(Promise.of(result));

            PolicyService.PolicyResult eval = runPromise(() -> policyService.evaluate(policyId, context));

            assertThat(eval.isAllowed()).isTrue();
            assertThat(eval.violatedRules()).isEmpty();
        }

        @Test
        @DisplayName("[S001]: evaluate_returns_denied_for_violation")
        void evaluateReturnsDeniedForViolation() {
            String policyId = "data-retention-policy";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext(
                "user-001", "tenant-alpha", "delete", "protected-entity",
                Map.of(), Instant.now()
            );

            PolicyService.PolicyResult result = new PolicyService.PolicyResult(
                policyId, false, List.of(), List.of("retention-rule"),
                Map.of("reason", "Cannot delete within retention period")
            );

            when(policyService.evaluate(policyId, context))
                .thenReturn(Promise.of(result));

            PolicyService.PolicyResult eval = runPromise(() -> policyService.evaluate(policyId, context));

            assertThat(eval.isAllowed()).isFalse();
            assertThat(eval.violatedRules()).contains("retention-rule");
        }
    }

    @Nested
    @DisplayName("Action Validation")
    class ActionValidationTests {

        @Test
        @DisplayName("[S001]: validate_action_checks_all_policies")
        void validateActionChecksAllPolicies() {
            String action = "export-data";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext(
                "user-001", "tenant-alpha", action, "dataset-123",
                Map.of("sensitive", true), Instant.now()
            );

            PolicyService.ValidationResult result = new PolicyService.ValidationResult(
                false,
                List.of(
                    new PolicyService.PolicyResult("p1", false, List.of(), List.of("export-limit"), Map.of())
                ),
                List.of("Export quota exceeded"),
                List.of()
            );

            when(policyService.validateAction(action, context))
                .thenReturn(Promise.of(result));

            PolicyService.ValidationResult validation = runPromise(() ->
                policyService.validateAction(action, context)
            );

            assertThat(validation.valid()).isFalse();
            assertThat(validation.errors()).contains("Export quota exceeded");
        }

        @Test
        @DisplayName("[S001]: validate_action_returns_valid_when_all_policies_pass")
        void validateActionReturnsValidWhenAllPoliciesPass() {
            String action = "read-entity";
            PolicyService.PolicyContext context = new PolicyService.PolicyContext(
                "user-001", "tenant-alpha", action, "entity-123",
                Map.of(), Instant.now()
            );

            PolicyService.ValidationResult result = new PolicyService.ValidationResult(
                true,
                List.of(
                    new PolicyService.PolicyResult("p1", true, List.of("rule-1"), List.of(), Map.of()),
                    new PolicyService.PolicyResult("p2", true, List.of("rule-2"), List.of(), Map.of())
                ),
                List.of(),
                List.of()
            );

            when(policyService.validateAction(action, context))
                .thenReturn(Promise.of(result));

            PolicyService.ValidationResult validation = runPromise(() ->
                policyService.validateAction(action, context)
            );

            assertThat(validation.valid()).isTrue();
            assertThat(validation.errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Policy Management")
    class PolicyManagementTests {

        @Test
        @DisplayName("[S001]: save_policy_creates_policy")
        void savePolicyCreatesPolicy() {
            PolicyService.Policy policy = new PolicyService.Policy(
                "new-policy", "Data Retention", "Retain data for 90 days",
                "tenant-alpha", PolicyService.PolicyType.DATA_RETENTION,
                List.of(), true, 1, Instant.now(), Instant.now()
            );

            when(policyService.savePolicy(any()))
                .thenReturn(Promise.of(policy));

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy));

            assertThat(result.id()).isEqualTo("new-policy");
            assertThat(result.enabled()).isTrue();
        }

        @Test
        @DisplayName("[S001]: get_policy_returns_existing")
        void getPolicyReturnsExisting() {
            String policyId = "existing-policy";
            PolicyService.Policy policy = new PolicyService.Policy(
                policyId, "Access Control", "Control access",
                "tenant-alpha", PolicyService.PolicyType.ACCESS_CONTROL,
                List.of(), true, 1, Instant.now(), Instant.now()
            );

            when(policyService.getPolicy(policyId))
                .thenReturn(Promise.of(Optional.of(policy)));

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId));

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(policyId);
        }

        @Test
        @DisplayName("[S001]: list_policies_filters_by_type")
        void listPoliciesFiltersByType() {
            String tenantId = "tenant-alpha";
            PolicyService.PolicyType type = PolicyService.PolicyType.DATA_RETENTION;

            List<PolicyService.Policy> policies = List.of(
                new PolicyService.Policy("p1", "Retention 1", "", tenantId, type, List.of(), true, 1, Instant.now(), Instant.now()),
                new PolicyService.Policy("p2", "Retention 2", "", tenantId, type, List.of(), true, 2, Instant.now(), Instant.now())
            );

            when(policyService.listPolicies(tenantId, type))
                .thenReturn(Promise.of(policies));

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, type));

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.type() == type);
        }
    }

    @Nested
    @DisplayName("Policy Rules")
    class PolicyRulesTests {

        @Test
        @DisplayName("[S001]: rule_with_allow_effect_permits_action")
        void ruleWithAllowEffectPermitsAction() {
            PolicyService.Rule rule = new PolicyService.Rule(
                "allow-read",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"),
                PolicyService.Effect.ALLOW,
                "Read operations are allowed"
            );

            assertThat(rule.effect()).isEqualTo(PolicyService.Effect.ALLOW);
            assertThat(rule.condition().operator()).isEqualTo(PolicyService.Condition.Operator.EQUALS);
        }

        @Test
        @DisplayName("[S001]: rule_with_deny_effect_blocks_action")
        void ruleWithDenyEffectBlocksAction() {
            PolicyService.Rule rule = new PolicyService.Rule(
                "deny-delete",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "delete"),
                PolicyService.Effect.DENY,
                "Delete operations are denied"
            );

            assertThat(rule.effect()).isEqualTo(PolicyService.Effect.DENY);
        }
    }

    @Nested
    @DisplayName("Violations")
    class ViolationsTests {

        @Test
        @DisplayName("[S001]: get_violations_returns_policy_breaches")
        void getViolationsReturnsPolicyBreaches() {
            String tenantId = "tenant-alpha";
            Instant since = Instant.now().minus(Duration.ofDays(7));

            List<PolicyService.PolicyViolation> violations = List.of(
                new PolicyService.PolicyViolation(
                    "v1", "policy-1", tenantId, "user-1", "export", "data-1",
                    "Export without approval", Instant.now(), Map.of()
                ),
                new PolicyService.PolicyViolation(
                    "v2", "policy-2", tenantId, "user-2", "access", "data-2",
                    "Unauthorized access", Instant.now(), Map.of()
                )
            );

            when(policyService.getViolations(tenantId, since))
                .thenReturn(Promise.of(violations));

            List<PolicyService.PolicyViolation> result = runPromise(() ->
                policyService.getViolations(tenantId, since)
            );

            assertThat(result).hasSize(2);
        }
    }
}
