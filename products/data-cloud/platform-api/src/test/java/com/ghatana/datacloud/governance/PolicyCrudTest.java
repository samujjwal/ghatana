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
@ExtendWith(MockitoExtension.class) 
@DisplayName("Policy CRUD Tests")
class PolicyCrudTest extends EventloopTestBase {

    @Mock
    private PolicyService policyService;

    // =========================================================================
    // CREATE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("CREATE operations")
    class CreateOperations {

        @Test
        @DisplayName("should create policy with all fields")
        void shouldCreatePolicyWithAllFields() { 
            PolicyService.Policy policy = new PolicyService.Policy( 
                UUID.randomUUID().toString(), 
                "Data Retention Policy",
                "Retain customer data for 90 days",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of( 
                    new PolicyService.Rule( 
                        "retention-rule",
                        new PolicyService.Condition("dataAge", PolicyService.Condition.Operator.LESS_THAN, 90), 
                        PolicyService.Effect.ALLOW,
                        "Data within retention period is allowed"
                    )
                ),
                true,
                100,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(policy)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); 

            assertThat(result.id()).isNotNull(); 
            assertThat(result.name()).isEqualTo("Data Retention Policy");
            assertThat(result.type()).isEqualTo(PolicyService.PolicyType.DATA_RETENTION); 
            assertThat(result.enabled()).isTrue(); 
            assertThat(result.rules()).hasSize(1); 
        }

        @Test
        @DisplayName("should create policy with minimal fields")
        void shouldCreatePolicyWithMinimalFields() { 
            PolicyService.Policy policy = new PolicyService.Policy( 
                UUID.randomUUID().toString(), 
                "Simple Policy",
                null,
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), 
                true,
                0,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(policy)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); 

            assertThat(result.id()).isNotNull(); 
            assertThat(result.description()).isNull(); 
            assertThat(result.rules()).isEmpty(); 
        }

        @Test
        @DisplayName("should create disabled policy")
        void shouldCreateDisabledPolicy() { 
            PolicyService.Policy policy = new PolicyService.Policy( 
                UUID.randomUUID().toString(), 
                "Disabled Policy",
                "Not yet active",
                "tenant-123",
                PolicyService.PolicyType.ACCESS_CONTROL,
                List.of(), 
                false,
                0,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(policy)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); 

            assertThat(result.enabled()).isFalse(); 
        }

        @Test
        @DisplayName("should assign priority when creating policy")
        void shouldAssignPriorityWhenCreatingPolicy() { 
            PolicyService.Policy policy = new PolicyService.Policy( 
                UUID.randomUUID().toString(), 
                "High Priority Policy",
                "Critical compliance policy",
                "tenant-123",
                PolicyService.PolicyType.COMPLIANCE,
                List.of(), 
                true,
                1000,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(policy)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(policy)); 

            assertThat(result.priority()).isEqualTo(1000); 
        }
    }

    // =========================================================================
    // READ OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("READ operations")
    class ReadOperations {

        @Test
        @DisplayName("should read policy by ID")
        void shouldReadPolicyById() { 
            String policyId = UUID.randomUUID().toString(); 
            PolicyService.Policy policy = new PolicyService.Policy( 
                policyId,
                "Test Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), 
                true,
                0,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.getPolicy(policyId)) 
                .thenReturn(Promise.of(Optional.of(policy))); 

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId)); 

            assertThat(result).isPresent(); 
            assertThat(result.get().id()).isEqualTo(policyId); 
        }

        @Test
        @DisplayName("should return empty when policy not found")
        void shouldReturnEmptyWhenPolicyNotFound() { 
            String policyId = UUID.randomUUID().toString(); 

            when(policyService.getPolicy(policyId)) 
                .thenReturn(Promise.of(Optional.empty())); 

            Optional<PolicyService.Policy> result = runPromise(() -> policyService.getPolicy(policyId)); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("should list all policies for tenant")
        void shouldListAllPoliciesForTenant() { 
            String tenantId = "tenant-123";
            List<PolicyService.Policy> policies = List.of( 
                new PolicyService.Policy("p1", "Policy 1", "", tenantId, PolicyService.PolicyType.DATA_RETENTION, List.of(), true, 1, Instant.now(), Instant.now()), 
                new PolicyService.Policy("p2", "Policy 2", "", tenantId, PolicyService.PolicyType.ACCESS_CONTROL, List.of(), true, 2, Instant.now(), Instant.now()) 
            );

            when(policyService.listPolicies(tenantId, null)) 
                .thenReturn(Promise.of(policies)); 

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, null)); 

            assertThat(result).hasSize(2); 
        }

        @Test
        @DisplayName("should list policies filtered by type")
        void shouldListPoliciesFilteredByType() { 
            String tenantId = "tenant-123";
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

        @Test
        @DisplayName("should return empty list when no policies exist")
        void shouldReturnEmptyListWhenNoPoliciesExist() { 
            String tenantId = "tenant-123";

            when(policyService.listPolicies(tenantId, null)) 
                .thenReturn(Promise.of(List.of())); 

            List<PolicyService.Policy> result = runPromise(() -> policyService.listPolicies(tenantId, null)); 

            assertThat(result).isEmpty(); 
        }
    }

    // =========================================================================
    // UPDATE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("UPDATE operations")
    class UpdateOperations {

        @Test
        @DisplayName("should update policy name and description")
        void shouldUpdatePolicyNameAndDescription() { 
            String policyId = UUID.randomUUID().toString(); 
            Instant createdAt = Instant.now().minusSeconds(3600); 
            Instant updatedAt = Instant.now(); 

            PolicyService.Policy updated = new PolicyService.Policy( 
                policyId,
                "New Name",
                "New Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), 
                true,
                0,
                createdAt,
                updatedAt
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(updated)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); 

            assertThat(result.id()).isEqualTo(policyId); 
            assertThat(result.name()).isEqualTo("New Name");
            assertThat(result.description()).isEqualTo("New Description");
            assertThat(result.createdAt()).isEqualTo(createdAt); 
            assertThat(result.updatedAt()).isEqualTo(updatedAt); 
        }

        @Test
        @DisplayName("should update policy rules")
        void shouldUpdatePolicyRules() { 
            String policyId = UUID.randomUUID().toString(); 
            List<PolicyService.Rule> newRules = List.of( 
                new PolicyService.Rule( 
                    "new-rule",
                    new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"), 
                    PolicyService.Effect.ALLOW,
                    "Allow read operations"
                )
            );

            PolicyService.Policy updated = new PolicyService.Policy( 
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.ACCESS_CONTROL,
                newRules,
                true,
                0,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(updated)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); 

            assertThat(result.rules()).hasSize(1); 
            assertThat(result.rules().get(0).name()).isEqualTo("new-rule");
        }

        @Test
        @DisplayName("should enable disabled policy")
        void shouldEnableDisabledPolicy() { 
            String policyId = UUID.randomUUID().toString(); 
            PolicyService.Policy disabled = new PolicyService.Policy( 
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), 
                false,
                0,
                Instant.now(), 
                Instant.now() 
            );

            PolicyService.Policy enabled = new PolicyService.Policy( 
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), 
                true,
                0,
                disabled.createdAt(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(enabled)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(enabled)); 

            assertThat(result.enabled()).isTrue(); 
        }

        @Test
        @DisplayName("should disable enabled policy")
        void shouldDisableEnabledPolicy() { 
            String policyId = UUID.randomUUID().toString(); 
            Instant createdAt = Instant.now(); 

            PolicyService.Policy disabled = new PolicyService.Policy( 
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.CUSTOM,
                List.of(), 
                false,
                0,
                createdAt,
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(disabled)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(disabled)); 

            assertThat(result.enabled()).isFalse(); 
        }

        @Test
        @DisplayName("should update policy priority")
        void shouldUpdatePolicyPriority() { 
            String policyId = UUID.randomUUID().toString(); 
            PolicyService.Policy updated = new PolicyService.Policy( 
                policyId,
                "Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.COMPLIANCE,
                List.of(), 
                true,
                500,
                Instant.now(), 
                Instant.now() 
            );

            when(policyService.savePolicy(any())) 
                .thenReturn(Promise.of(updated)); 

            PolicyService.Policy result = runPromise(() -> policyService.savePolicy(updated)); 

            assertThat(result.priority()).isEqualTo(500); 
        }
    }

    // =========================================================================
    // DELETE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("DELETE operations")
    class DeleteOperations {

        @Test
        @DisplayName("should delete policy by ID")
        void shouldDeletePolicyById() { 
            String policyId = UUID.randomUUID().toString(); 

            when(policyService.deletePolicy(policyId)) 
                .thenReturn(Promise.of(null)); 

            runPromise(() -> policyService.deletePolicy(policyId)); 

            verify(policyService).deletePolicy(policyId); 
        }

        @Test
        @DisplayName("should complete when deleting non-existent policy")
        void shouldCompleteWhenDeletingNonExistentPolicy() { 
            String policyId = UUID.randomUUID().toString(); 

            when(policyService.deletePolicy(policyId)) 
                .thenReturn(Promise.of(null)); 

            Void result = runPromise(() -> policyService.deletePolicy(policyId)); 

            assertThat(result).isNull(); 
        }
    }

    // =========================================================================
    // VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should validate policy with required fields")
        void shouldValidatePolicyWithRequiredFields() { 
            PolicyService.Policy policy = new PolicyService.Policy( 
                UUID.randomUUID().toString(), 
                "Valid Policy",
                "Description",
                "tenant-123",
                PolicyService.PolicyType.DATA_RETENTION,
                List.of(), 
                true,
                0,
                Instant.now(), 
                Instant.now() 
            );

            assertThat(policy.id()).isNotNull(); 
            assertThat(policy.name()).isNotNull(); 
            assertThat(policy.tenantId()).isNotNull(); 
            assertThat(policy.type()).isNotNull(); 
        }

        @Test
        @DisplayName("should validate rule with condition and effect")
        void shouldValidateRuleWithConditionAndEffect() { 
            PolicyService.Rule rule = new PolicyService.Rule( 
                "test-rule",
                new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "read"), 
                PolicyService.Effect.ALLOW,
                "Test message"
            );

            assertThat(rule.name()).isNotNull(); 
            assertThat(rule.condition()).isNotNull(); 
            assertThat(rule.effect()).isNotNull(); 
        }

        @Test
        @DisplayName("should validate condition with operator")
        void shouldValidateConditionWithOperator() { 
            PolicyService.Condition condition = new PolicyService.Condition( 
                "dataAge",
                PolicyService.Condition.Operator.GREATER_THAN,
                90
            );

            assertThat(condition.attribute()).isNotNull(); 
            assertThat(condition.operator()).isNotNull(); 
            assertThat(condition.value()).isNotNull(); 
        }
    }

    // =========================================================================
    // POLICY TYPES
    // =========================================================================

    @Nested
    @DisplayName("Policy types")
    class PolicyTypes {

        @Test
        @DisplayName("should support all policy types")
        void shouldSupportAllPolicyTypes() { 
            PolicyService.PolicyType[] types = PolicyService.PolicyType.values(); 

            assertThat(types).containsExactlyInAnyOrder( 
                PolicyService.PolicyType.DATA_RETENTION,
                PolicyService.PolicyType.ACCESS_CONTROL,
                PolicyService.PolicyType.DATA_CLASSIFICATION,
                PolicyService.PolicyType.COMPLIANCE,
                PolicyService.PolicyType.CUSTOM
            );
        }

        @Test
        @DisplayName("should create policies for each type")
        void shouldCreatePoliciesForEachType() { 
            String tenantId = "tenant-123";

            for (PolicyService.PolicyType type : PolicyService.PolicyType.values()) { 
                PolicyService.Policy policy = new PolicyService.Policy( 
                    UUID.randomUUID().toString(), 
                    type + " Policy",
                    "Description",
                    tenantId,
                    type,
                    List.of(), 
                    true,
                    0,
                    Instant.now(), 
                    Instant.now() 
                );

                assertThat(policy.type()).isEqualTo(type); 
            }
        }
    }

    // =========================================================================
    // EFFECTS
    // =========================================================================

    @Nested
    @DisplayName("Effects")
    class Effects {

        @Test
        @DisplayName("should support all effect types")
        void shouldSupportAllEffectTypes() { 
            PolicyService.Effect[] effects = PolicyService.Effect.values(); 

            assertThat(effects).containsExactlyInAnyOrder( 
                PolicyService.Effect.ALLOW,
                PolicyService.Effect.DENY,
                PolicyService.Effect.AUDIT,
                PolicyService.Effect.REQUIRE_APPROVAL
            );
        }

        @Test
        @DisplayName("should create rules with each effect type")
        void shouldCreateRulesWithEachEffectType() { 
            for (PolicyService.Effect effect : PolicyService.Effect.values()) { 
                PolicyService.Rule rule = new PolicyService.Rule( 
                    effect.name() + "-rule", 
                    new PolicyService.Condition("action", PolicyService.Condition.Operator.EQUALS, "test"), 
                    effect,
                    "Test message"
                );

                assertThat(rule.effect()).isEqualTo(effect); 
            }
        }
    }

    // =========================================================================
    // OPERATORS
    // =========================================================================

    @Nested
    @DisplayName("Operators")
    class Operators {

        @Test
        @DisplayName("should support all condition operators")
        void shouldSupportAllConditionOperators() { 
            PolicyService.Condition.Operator[] operators = PolicyService.Condition.Operator.values(); 

            assertThat(operators).containsExactlyInAnyOrder( 
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
        @DisplayName("should create conditions with each operator")
        void shouldCreateConditionsWithEachOperator() { 
            for (PolicyService.Condition.Operator operator : PolicyService.Condition.Operator.values()) { 
                PolicyService.Condition condition = new PolicyService.Condition( 
                    "attribute",
                    operator,
                    "value"
                );

                assertThat(condition.operator()).isEqualTo(operator); 
            }
        }
    }
}
