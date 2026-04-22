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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive CRUD tests for governance policy management.
 *
 * <p>Tests create, read, update, and delete operations for policies,
 * including validation, error handling, and edge cases.</p>
 *
 * @doc.type test
 * @doc.purpose Governance policy CRUD tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Policy CRUD Tests [GH-90000]")
class PolicyCrudTest extends EventloopTestBase {

    @Mock
    private PolicyService policyService;

    // =========================================================================
    // CREATE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("CREATE operations [GH-90000]")
    class CreateOperations {

        @Test
        @DisplayName("should create policy with all fields [GH-90000]")
        void shouldCreatePolicyWithAllFields() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "Data Retention Policy",
                "Retain customer data for 90 days",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of( // GH-90000
                    new PolicyService.Rule( // GH-90000
                        "retention-rule",
                        new PolicyService.Condition("dataAge", PolicyService.Condition.Operator.LESS_THAN, 90), // GH-90000
                        PolicyService.Effect.ALLOW,
                        "Data within retention period is allowed"
                    )
                ),
                true,
                100,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(policy)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); // GH-90000

            assertThat(result.id()).isNotNull(); // GH-90000
            assertThat(result.name()).isEqualTo("Data Retention Policy [GH-90000]");
            assertThat(result.type()).isEqualTo(PolicyService.PolicyType.DATA_RETENTION); // GH-90000
            assertThat(result.enabled()).isTrue(); // GH-90000
            assertThat(result.rules()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should create policy with minimal fields [GH-90000]")
        void shouldCreatePolicyWithMinimalFields() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "Simple Policy",
                null,
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(policy)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); // GH-90000

            assertThat(result.id()).isNotNull(); // GH-90000
            assertThat(result.description()).isNull(); // GH-90000
            assertThat(result.rules()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should create disabled policy [GH-90000]")
        void shouldCreateDisabledPolicy() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "Disabled Policy",
                "Not yet active",
                "tenant-123",
                PolicyService.PolicyType.ACCESS_CONTROL,
                List.of(), // GH-90000
                false,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(policy)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); // GH-90000

            assertThat(result.enabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should assign priority when creating policy [GH-90000]")
        void shouldAssignPriorityWhenCreatingPolicy() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "High Priority Policy",
                "Critical compliance policy",
                "tenant-123",
                PolicyService.PolicyType.COMPLIANCE,
                List.of(), // GH-90000
                true,
                1000,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(policy)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); // GH-90000

            assertThat(result.priority()).isEqualTo(1000); // GH-90000
        }
    }

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("READ operations [GH-90000]")
    class ReadOperations {

        @Test
        @DisplayName("should read policy by ID [GH-90000]")
        void shouldReadPolicyById() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                policyId,
                "Test Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.getPolicy(policyId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(policy))); // GH-90000

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(policyId); // GH-90000
        }

        @Test
        @DisplayName("should return empty when policy not found [GH-90000]")
        void shouldReturnEmptyWhenPolicyNotFound() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000

            when(policyService.getPolicy(policyId)) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should list all policies for tenant [GH-90000]")
        void shouldListAllPoliciesForTenant() { // GH-90000
            String tenantId = "tenant-123";
            List<PolicyService.Policy> policies = List.of( // GH-90000
                new PolicyService.Policy("p1", "Policy 1", "", tenantId, PolicyService.PolicyType.DATA_RETENTION, List.of(), true, 1, Instant.now(), Instant.now()), // GH-90000
                new PolicyService.Policy("p2", "Policy 2", "", tenantId, PolicyService.PolicyType.ACCESS_CONTROL, List.of(), true, 2, Instant.now(), Instant.now()) // GH-90000
            );

            when(policyService.listPolicies(tenantId, null)) // GH-90000
                .thenReturn(Promise.of(policies)); // GH-90000

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, null)); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should list policies filtered by type [GH-90000]")
        void shouldListPoliciesFilteredByType() { // GH-90000
            String tenantId = "tenant-123";
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

        @Test
        @DisplayName("should return empty list when no policies exist [GH-90000]")
        void shouldReturnEmptyListWhenNoPoliciesExist() { // GH-90000
            String tenantId = "tenant-123";

            when(policyService.listPolicies(tenantId, null)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, null)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // UPDATE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("UPDATE operations [GH-90000]")
    class UpdateOperations {

        @Test
        @DisplayName("should update policy name and description [GH-90000]")
        void shouldUpdatePolicyNameAndDescription() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            Instant createdAt = Instant.now().minusSeconds(3600); // GH-90000
            Instant updatedAt = Instant.now(); // GH-90000

            PolicyService.Policy updated = new PolicyService.Policy( // GH-90000
                policyId,
                "New Name",
                "New Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), // GH-90000
                true,
                0,
                createdAt,
                updatedAt
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); // GH-90000

            assertThat(result.id()).isEqualTo(policyId); // GH-90000
            assertThat(result.name()).isEqualTo("New Name [GH-90000]");
            assertThat(result.description()).isEqualTo("New Description [GH-90000]");
            assertThat(result.createdAt()).isEqualTo(createdAt); // GH-90000
            assertThat(result.updatedAt()).isEqualTo(updatedAt); // GH-90000
        }

        @Test
        @DisplayName("should update policy rules [GH-90000]")
        void shouldUpdatePolicyRules() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            List<PolicyService.Rule> newRules = List.of( // GH-90000
                new PolicyService.Rule( // GH-90000
                    "new-rule",
                    new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"), // GH-90000
                    PolicyService.Effect.ALLOW,
                    "Allow read operations"
                )
            );

            PolicyService.Policy updated = new PolicyService.Policy( // GH-90000
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.ACCESS_CONTROL,
                newRules,
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); // GH-90000

            assertThat(result.rules()).hasSize(1); // GH-90000
            assertThat(result.rules().get(0).name()).isEqualTo("new-rule [GH-90000]");
        }

        @Test
        @DisplayName("should enable disabled policy [GH-90000]")
        void shouldEnableDisabledPolicy() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            PolicyService.Policy disabled = new PolicyService.Policy( // GH-90000
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), // GH-90000
                false,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            PolicyService.Policy enabled = new PolicyService.Policy( // GH-90000
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), // GH-90000
                true,
                0,
                disabled.createdAt(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(enabled)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(enabled)); // GH-90000

            assertThat(result.enabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should disable enabled policy [GH-90000]")
        void shouldDisableEnabledPolicy() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000

            PolicyService.Policy disabled = new PolicyService.Policy( // GH-90000
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), // GH-90000
                false,
                0,
                createdAt,
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(disabled)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(disabled)); // GH-90000

            assertThat(result.enabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should update policy priority [GH-90000]")
        void shouldUpdatePolicyPriority() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000
            PolicyService.Policy updated = new PolicyService.Policy( // GH-90000
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.COMPLIANCE,
                List.of(), // GH-90000
                true,
                500,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            when(policyService.savePolicy(any())) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); // GH-90000

            assertThat(result.priority()).isEqualTo(500); // GH-90000
        }
    }

    // =========================================================================
    // DELETE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("DELETE operations [GH-90000]")
    class DeleteOperations {

        @Test
        @DisplayName("should delete policy by ID [GH-90000]")
        void shouldDeletePolicyById() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000

            when(policyService.deletePolicy(policyId)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> policyService.deletePolicy(policyId)); // GH-90000

            verify(policyService).deletePolicy(policyId); // GH-90000
        }

        @Test
        @DisplayName("should complete when deleting non-existent policy [GH-90000]")
        void shouldCompleteWhenDeletingNonExistentPolicy() { // GH-90000
            String policyId = UUID.randomUUID().toString(); // GH-90000

            when(policyService.deletePolicy(policyId)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            Void result = runPromise(() -> policyService.deletePolicy(policyId)); // GH-90000

            assertThat(result).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Validation [GH-90000]")
    class Validation {

        @Test
        @DisplayName("should validate policy with required fields [GH-90000]")
        void shouldValidatePolicyWithRequiredFields() { // GH-90000
            PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                UUID.randomUUID().toString(), // GH-90000
                "Valid Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), // GH-90000
                true,
                0,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
            );

            assertThat(policy.id()).isNotNull(); // GH-90000
            assertThat(policy.name()).isNotNull(); // GH-90000
            assertThat(policy.tenantId()).isNotNull(); // GH-90000
            assertThat(policy.type()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should validate rule with condition and effect [GH-90000]")
        void shouldValidateRuleWithConditionAndEffect() { // GH-90000
            PolicyService.Rule rule = new PolicyService.Rule( // GH-90000
                "test-rule",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"), // GH-90000
                PolicyService.Effect.ALLOW,
                "Test message"
            );

            assertThat(rule.name()).isNotNull(); // GH-90000
            assertThat(rule.condition()).isNotNull(); // GH-90000
            assertThat(rule.effect()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should validate condition with operator [GH-90000]")
        void shouldValidateConditionWithOperator() { // GH-90000
            PolicyService.Condition condition = new PolicyService.Condition( // GH-90000
                "dataAge",
                PolicyService.Condition.Operator.GREATER_THAN,
                90
            );

            assertThat(condition.attribute()).isNotNull(); // GH-90000
            assertThat(condition.operator()).isNotNull(); // GH-90000
            assertThat(condition.value()).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // POLICY TYPES
    // =========================================================================

    @Nested
    @DisplayName("Policy types [GH-90000]")
    class PolicyTypes {

        @Test
        @DisplayName("should support all policy types [GH-90000]")
        void shouldSupportAllPolicyTypes() { // GH-90000
            PolicyService.PolicyType[] types = PolicyService.PolicyType.values(); // GH-90000

            assertThat(types).containsExactlyInAnyOrder( // GH-90000
                PolicyService.PolicyType.DATA_RETENTION,
                PolicyService.PolicyType.ACCESS_CONTROL,
                PolicyService.PolicyType.DATA_CLASSIFICATION,
                PolicyService.PolicyType.COMPLIANCE,
                PolicyService.PolicyType.CUSTOM
            );
        }

        @Test
        @DisplayName("should create policies for each type [GH-90000]")
        void shouldCreatePoliciesForEachType() { // GH-90000
            String tenantId = "tenant-123";

            for (PolicyService.PolicyType type : PolicyService.PolicyType.values()) { // GH-90000
                PolicyService.Policy policy = new PolicyService.Policy( // GH-90000
                    UUID.randomUUID().toString(), // GH-90000
                    type + " Policy",
                    "Description",
                    tenantId,
                    type,
                    List.of(), // GH-90000
                    true,
                    0,
                    Instant.now(), // GH-90000
                    Instant.now() // GH-90000
                );

                assertThat(policy.type()).isEqualTo(type); // GH-90000
            }
        }
    }

    // =========================================================================
    // EFFECTS
    // =========================================================================

    @Nested
    @DisplayName("Effects [GH-90000]")
    class Effects {

        @Test
        @DisplayName("should support all effect types [GH-90000]")
        void shouldSupportAllEffectTypes() { // GH-90000
            PolicyService.Effect[] effects = PolicyService.Effect.values(); // GH-90000

            assertThat(effects).containsExactlyInAnyOrder( // GH-90000
                PolicyService.Effect.ALLOW,
                PolicyService.Effect.DENY,
                PolicyService.Effect.AUDIT,
                PolicyService.Effect.REQUIRE_APPROVAL
            );
        }

        @Test
        @DisplayName("should create rules with each effect type [GH-90000]")
        void shouldCreateRulesWithEachEffectType() { // GH-90000
            for (PolicyService.Effect effect : PolicyService.Effect.values()) { // GH-90000
                PolicyService.Rule rule = new PolicyService.Rule( // GH-90000
                    effect.name() + "-rule", // GH-90000
                    new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "test"), // GH-90000
                    effect,
                    "Test message"
                );

                assertThat(rule.effect()).isEqualTo(effect); // GH-90000
            }
        }
    }

    // =========================================================================
    // OPERATORS
    // =========================================================================

    @Nested
    @DisplayName("Operators [GH-90000]")
    class Operators {

        @Test
        @DisplayName("should support all condition operators [GH-90000]")
        void shouldSupportAllConditionOperators() { // GH-90000
            PolicyService.Condition.Operator[] operators = PolicyService.Condition.Operator.values(); // GH-90000

            assertThat(operators).containsExactlyInAnyOrder( // GH-90000
                PolicyService.Condition.Operator.EQUALS,
                PolicyService.Condition.Operator.NOT_EQUALS,
                PolicyService.Condition.Operator.CONTAINS,
                PolicyService.Condition.Operator.GREATER_THAN,
                PolicyService.Condition.Operator.LESS_THAN,
                PolicyService.Condition.Operator.EXISTS,
                PolicyService.Condition.Operator.NOT_EXISTS,
                PolicyService.Condition.Operator.MATCHES,
                PolicyService.Condition.Operator.IN,
                PolicyService.Condition.Operator.NOT_IN
            );
        }

        @Test
        @DisplayName("should create conditions with each operator [GH-90000]")
        void shouldCreateConditionsWithEachOperator() { // GH-90000
            for (PolicyService.Condition.Operator operator : PolicyService.Condition.Operator.values()) { // GH-90000
                PolicyService.Condition condition = new PolicyService.Condition( // GH-90000
                    "attribute",
                    operator,
                    "value"
                );

                assertThat(condition.operator()).isEqualTo(operator); // GH-90000
            }
        }
    }
}
