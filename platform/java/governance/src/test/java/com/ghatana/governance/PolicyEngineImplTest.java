/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP2: PolicyEngineImpl, PolicyRegistry, CompiledPolicy —
 * executable governance engine with "most restrictive wins" semantics.
 */
@DisplayName("Executable Governance (WP2)")
class PolicyEngineImplTest extends EventloopTestBase {

    private PolicyRegistry registry;
    private List<PolicyDecisionRecord> auditRecords;
    private PolicyEngineImpl engine;

    @Override
    protected Duration eventloopTimeout() { 
        return Duration.ofSeconds(15); 
    }

    @Override
    protected boolean breakOnFatalError() { 
        return false;
    }

    @BeforeEach
    void setUp() { 
        registry = new PolicyRegistry(); 
        auditRecords = new ArrayList<>(); 
        engine = new PolicyEngineImpl( 
                registry,
                Executors.newVirtualThreadPerTaskExecutor(), 
                auditRecords::add);
    }

    // =========================================================================
    // PolicyRegistry
    // =========================================================================

    @Nested
    @DisplayName("PolicyRegistry")
    class PolicyRegistryTests {

        @Test
        @DisplayName("should register and retrieve policies")
        void shouldRegisterAndRetrieve() { 
            CompiledPolicy policy = buildPolicy("p1", "Test Policy", 
                    1, "ALLOW", List.of("WRITE_REVERSIBLE"), List.of(), List.of());

            registry.register(policy); 

            assertThat(registry.size()).isEqualTo(1); 
            assertThat(registry.getAll()).hasSize(1); 
            assertThat(registry.getAll().getFirst().id()).isEqualTo("p1");
        }

        @Test
        @DisplayName("should match by action class")
        void shouldMatchByActionClass() { 
            registry.register(buildPolicy("p1", "Write Policy", 
                    1, "ALLOW", List.of("WRITE_REVERSIBLE"), List.of(), List.of()));
            registry.register(buildPolicy("p2", "Read Policy", 
                    2, "ALLOW", List.of("READ"), List.of(), List.of()));

            PolicyEvaluationContext ctx = buildContext("WRITE_REVERSIBLE", "high", "tenant-1"); 
            List<CompiledPolicy> matching = registry.findMatching(ctx); 

            assertThat(matching).hasSize(1); 
            assertThat(matching.getFirst().id()).isEqualTo("p1");
        }

        @Test
        @DisplayName("should match by criticality")
        void shouldMatchByCriticality() { 
            registry.register(buildPolicy("p1", "High Crit", 
                    1, "DENY", List.of(), List.of("critical"), List.of()));

            PolicyEvaluationContext highCtx = buildContext("WRITE_IRREVERSIBLE", "critical", "tenant-1"); 
            PolicyEvaluationContext lowCtx = buildContext("WRITE_IRREVERSIBLE", "low", "tenant-1"); 

            assertThat(registry.findMatching(highCtx)).hasSize(1); 
            assertThat(registry.findMatching(lowCtx)).isEmpty(); 
        }

        @Test
        @DisplayName("should return policies ordered by priority")
        void shouldReturnOrderedByPriority() { 
            registry.register(buildPolicy("p-low", "Low Priority", 
                    10, "ALLOW", List.of(), List.of(), List.of())); 
            registry.register(buildPolicy("p-high", "High Priority", 
                    1, "DENY", List.of(), List.of(), List.of())); 

            List<CompiledPolicy> matching = registry.findMatching( 
                    buildContext("WRITE_REVERSIBLE", "medium", "tenant-1")); 

            assertThat(matching).hasSize(2); 
            assertThat(matching.get(0).id()).isEqualTo("p-high");
            assertThat(matching.get(1).id()).isEqualTo("p-low");
        }

        @Test
        @DisplayName("replaceAll should swap all policies atomically")
        void replaceAllShouldSwapPolicies() { 
            registry.register(buildPolicy("old", "Old", 
                    1, "ALLOW", List.of(), List.of(), List.of())); 

            registry.replaceAll(List.of( 
                    buildPolicy("new1", "New1", 1, "DENY", List.of(), List.of(), List.of()), 
                    buildPolicy("new2", "New2", 2, "ALLOW", List.of(), List.of(), List.of()))); 

            assertThat(registry.size()).isEqualTo(2); 
            assertThat(registry.getAll().stream().map(CompiledPolicy::id)) 
                    .containsExactlyInAnyOrder("new1", "new2"); 
        }

        @Test
        @DisplayName("disabled policies should not match")
        void disabledPoliciesShouldNotMatch() { 
            CompiledPolicy disabled = new CompiledPolicy( 
                    "disabled", "Disabled Policy", 1,
                    List.of(), List.of(), List.of(), 
                    "DENY", List.of(), List.of(), 
                    Map.of(), false); // enabled=false 

            registry.register(disabled); 

            assertThat(registry.findMatching( 
                    buildContext("WRITE_IRREVERSIBLE", "high", "tenant-1"))).isEmpty(); 
        }
    }

    // =========================================================================
    // PolicyEngineImpl — evaluate
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.evaluateContext")
    class EvaluateContextTests {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with no policies should DENY (hard default)")
        void writeIrreversibleWithNoPoliciesShouldDeny() { 
            PolicyEvaluationContext ctx = buildContext("WRITE_IRREVERSIBLE", "high", "tenant-1"); 

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); 

            assertThat(record.decision()).isEqualTo("DENY");
            assertThat(record.reasons()).anyMatch(r -> r.toLowerCase().contains("irreversible"));
        }

        @Test
        @DisplayName("POLICY_CHANGE with no policies should require approval (hard default)")
        void policyChangeWithNoPoliciesShouldRequireApproval() { 
            PolicyEvaluationContext ctx = buildContext("POLICY_CHANGE", "high", "tenant-1"); 

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); 

            assertThat(record.decision()).isEqualTo("ALLOW_WITH_APPROVAL");
        }

        @Test
        @DisplayName("READ with no policies should ALLOW (safe default)")
        void readWithNoPoliciesShouldAllow() { 
            PolicyEvaluationContext ctx = buildContext("READ", "low", "tenant-1"); 

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); 

            assertThat(record.decision()).isEqualTo("ALLOW");
        }

        @Test
        @DisplayName("most restrictive policy wins when multiple match")
        void mostRestrictiveWins() { 
            registry.register(buildPolicy("p-allow", "Allow Policy", 
                    1, "ALLOW", List.of("WRITE_REVERSIBLE"), List.of(), List.of()));
            registry.register(buildPolicy("p-deny", "Deny Policy", 
                    2, "DENY", List.of("WRITE_REVERSIBLE"), List.of(), List.of()));

            PolicyEvaluationContext ctx = buildContext("WRITE_REVERSIBLE", "high", "tenant-1"); 
            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); 

            assertThat(record.decision()).isEqualTo("DENY");
        }

        @Test
        @DisplayName("should record audit decision for every evaluation")
        void shouldRecordAuditDecision() { 
            PolicyEvaluationContext ctx = buildContext("READ", "low", "tenant-1"); 

            runPromise(() -> engine.evaluateContext(ctx)); 

            assertThat(auditRecords).hasSize(1); 
            assertThat(auditRecords.getFirst().context().tenantId()).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("tenant-scoped policy should only match its tenant")
        void tenantScopedPolicyShouldOnlyMatchItsTenant() { 
            registry.register(buildPolicy("p-acme", "ACME Only", 
                    1, "DENY", List.of(), List.of(), List.of("acme-tenant")));

            PolicyEvaluationContext acmeCtx = buildContext("WRITE_REVERSIBLE", "high", "acme-tenant"); 
            PolicyEvaluationContext otherCtx = buildContext("WRITE_REVERSIBLE", "low", "other-tenant"); 

            PolicyDecisionRecord acmeRecord = runPromise(() -> engine.evaluateContext(acmeCtx)); 
            PolicyDecisionRecord otherRecord = runPromise(() -> engine.evaluateContext(otherCtx)); 

            assertThat(acmeRecord.decision()).isEqualTo("DENY");
            assertThat(otherRecord.decision()).isEqualTo("ALLOW"); // no policies match, low criticality
        }
    }

    // =========================================================================
    // PolicyEngineImpl — legacy evaluate interface
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.evaluate (legacy)")
    class LegacyEvaluateTests {

        @Test
        @DisplayName("should return true for allowed actions")
        void shouldReturnTrueForAllowed() { 
            Boolean result = runPromise(() -> engine.evaluate("any-policy", 
                    Map.of("actionClass", "READ", "criticality", "low"))); 

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("should return false for denied actions (hard default)")
        void shouldReturnFalseForDenied() { 
            Boolean result = runPromise(() -> engine.evaluate("any-policy", 
                    Map.of("actionClass", "WRITE_IRREVERSIBLE", "criticality", "critical"))); 

            assertThat(result).isFalse(); 
        }
    }

    // =========================================================================
    // PolicyEngineImpl — policyExists
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.policyExists")
    class PolicyExistsTests {

        @Test
        @DisplayName("should return true for registered policy")
        void shouldReturnTrueForRegistered() { 
            registry.register(buildPolicy("my-policy", "My Policy", 
                    1, "ALLOW", List.of(), List.of(), List.of())); 

            Boolean exists = runPromise(() -> engine.policyExists("my-policy"));
            assertThat(exists).isTrue(); 
        }

        @Test
        @DisplayName("should return false for unregistered policy")
        void shouldReturnFalseForUnregistered() { 
            Boolean exists = runPromise(() -> engine.policyExists("nonexistent"));
            assertThat(exists).isFalse(); 
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CompiledPolicy buildPolicy( 
            String id, String name, int priority, String decision,
            List<String> actionClasses, List<String> criticalities,
            List<String> tenantScopes) {
        return new CompiledPolicy( 
                id, name, priority,
                actionClasses, criticalities, tenantScopes,
                decision, List.of(), List.of(), 
                Map.of(), true); 
    }

    private PolicyEvaluationContext buildContext( 
            String actionClass, String criticality, String tenantId) {
        return new PolicyEvaluationContext( 
                "agent-1", tenantId, actionClass,
                "target-type", "target-1", "tool-1",
                criticality, "REVERSIBLE",
                Map.of(), Instant.now()); 
    }
}
