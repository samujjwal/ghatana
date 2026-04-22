/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Executable Governance (WP2) [GH-90000]")
class PolicyEngineImplTest extends EventloopTestBase {

    private PolicyRegistry registry;
    private List<PolicyDecisionRecord> auditRecords;
    private PolicyEngineImpl engine;

    @Override
    protected Duration eventloopTimeout() { // GH-90000
        return Duration.ofSeconds(15); // GH-90000
    }

    @Override
    protected boolean breakOnFatalError() { // GH-90000
        return false;
    }

    @BeforeEach
    void setUp() { // GH-90000
        registry = new PolicyRegistry(); // GH-90000
        auditRecords = new ArrayList<>(); // GH-90000
        engine = new PolicyEngineImpl( // GH-90000
                registry,
                Executors.newVirtualThreadPerTaskExecutor(), // GH-90000
                auditRecords::add);
    }

    // =========================================================================
    // PolicyRegistry
    // =========================================================================

    @Nested
    @DisplayName("PolicyRegistry [GH-90000]")
    class PolicyRegistryTests {

        @Test
        @DisplayName("should register and retrieve policies [GH-90000]")
        void shouldRegisterAndRetrieve() { // GH-90000
            CompiledPolicy policy = buildPolicy("p1", "Test Policy", // GH-90000
                    1, "ALLOW", List.of("WRITE_REVERSIBLE [GH-90000]"), List.of(), List.of());

            registry.register(policy); // GH-90000

            assertThat(registry.size()).isEqualTo(1); // GH-90000
            assertThat(registry.getAll()).hasSize(1); // GH-90000
            assertThat(registry.getAll().getFirst().id()).isEqualTo("p1 [GH-90000]");
        }

        @Test
        @DisplayName("should match by action class [GH-90000]")
        void shouldMatchByActionClass() { // GH-90000
            registry.register(buildPolicy("p1", "Write Policy", // GH-90000
                    1, "ALLOW", List.of("WRITE_REVERSIBLE [GH-90000]"), List.of(), List.of()));
            registry.register(buildPolicy("p2", "Read Policy", // GH-90000
                    2, "ALLOW", List.of("READ [GH-90000]"), List.of(), List.of()));

            PolicyEvaluationContext ctx = buildContext("WRITE_REVERSIBLE", "high", "tenant-1"); // GH-90000
            List<CompiledPolicy> matching = registry.findMatching(ctx); // GH-90000

            assertThat(matching).hasSize(1); // GH-90000
            assertThat(matching.getFirst().id()).isEqualTo("p1 [GH-90000]");
        }

        @Test
        @DisplayName("should match by criticality [GH-90000]")
        void shouldMatchByCriticality() { // GH-90000
            registry.register(buildPolicy("p1", "High Crit", // GH-90000
                    1, "DENY", List.of(), List.of("critical [GH-90000]"), List.of()));

            PolicyEvaluationContext highCtx = buildContext("WRITE_IRREVERSIBLE", "critical", "tenant-1"); // GH-90000
            PolicyEvaluationContext lowCtx = buildContext("WRITE_IRREVERSIBLE", "low", "tenant-1"); // GH-90000

            assertThat(registry.findMatching(highCtx)).hasSize(1); // GH-90000
            assertThat(registry.findMatching(lowCtx)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should return policies ordered by priority [GH-90000]")
        void shouldReturnOrderedByPriority() { // GH-90000
            registry.register(buildPolicy("p-low", "Low Priority", // GH-90000
                    10, "ALLOW", List.of(), List.of(), List.of())); // GH-90000
            registry.register(buildPolicy("p-high", "High Priority", // GH-90000
                    1, "DENY", List.of(), List.of(), List.of())); // GH-90000

            List<CompiledPolicy> matching = registry.findMatching( // GH-90000
                    buildContext("WRITE_REVERSIBLE", "medium", "tenant-1")); // GH-90000

            assertThat(matching).hasSize(2); // GH-90000
            assertThat(matching.get(0).id()).isEqualTo("p-high [GH-90000]");
            assertThat(matching.get(1).id()).isEqualTo("p-low [GH-90000]");
        }

        @Test
        @DisplayName("replaceAll should swap all policies atomically [GH-90000]")
        void replaceAllShouldSwapPolicies() { // GH-90000
            registry.register(buildPolicy("old", "Old", // GH-90000
                    1, "ALLOW", List.of(), List.of(), List.of())); // GH-90000

            registry.replaceAll(List.of( // GH-90000
                    buildPolicy("new1", "New1", 1, "DENY", List.of(), List.of(), List.of()), // GH-90000
                    buildPolicy("new2", "New2", 2, "ALLOW", List.of(), List.of(), List.of()))); // GH-90000

            assertThat(registry.size()).isEqualTo(2); // GH-90000
            assertThat(registry.getAll().stream().map(CompiledPolicy::id)) // GH-90000
                    .containsExactlyInAnyOrder("new1", "new2"); // GH-90000
        }

        @Test
        @DisplayName("disabled policies should not match [GH-90000]")
        void disabledPoliciesShouldNotMatch() { // GH-90000
            CompiledPolicy disabled = new CompiledPolicy( // GH-90000
                    "disabled", "Disabled Policy", 1,
                    List.of(), List.of(), List.of(), // GH-90000
                    "DENY", List.of(), List.of(), // GH-90000
                    Map.of(), false); // enabled=false // GH-90000

            registry.register(disabled); // GH-90000

            assertThat(registry.findMatching( // GH-90000
                    buildContext("WRITE_IRREVERSIBLE", "high", "tenant-1"))).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // PolicyEngineImpl — evaluate
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.evaluateContext [GH-90000]")
    class EvaluateContextTests {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE with no policies should DENY (hard default) [GH-90000]")
        void writeIrreversibleWithNoPoliciesShouldDeny() { // GH-90000
            PolicyEvaluationContext ctx = buildContext("WRITE_IRREVERSIBLE", "high", "tenant-1"); // GH-90000

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); // GH-90000

            assertThat(record.decision()).isEqualTo("DENY [GH-90000]");
            assertThat(record.reasons()).anyMatch(r -> r.toLowerCase().contains("irreversible [GH-90000]"));
        }

        @Test
        @DisplayName("POLICY_CHANGE with no policies should require approval (hard default) [GH-90000]")
        void policyChangeWithNoPoliciesShouldRequireApproval() { // GH-90000
            PolicyEvaluationContext ctx = buildContext("POLICY_CHANGE", "high", "tenant-1"); // GH-90000

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); // GH-90000

            assertThat(record.decision()).isEqualTo("ALLOW_WITH_APPROVAL [GH-90000]");
        }

        @Test
        @DisplayName("READ with no policies should ALLOW (safe default) [GH-90000]")
        void readWithNoPoliciesShouldAllow() { // GH-90000
            PolicyEvaluationContext ctx = buildContext("READ", "low", "tenant-1"); // GH-90000

            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); // GH-90000

            assertThat(record.decision()).isEqualTo("ALLOW [GH-90000]");
        }

        @Test
        @DisplayName("most restrictive policy wins when multiple match [GH-90000]")
        void mostRestrictiveWins() { // GH-90000
            registry.register(buildPolicy("p-allow", "Allow Policy", // GH-90000
                    1, "ALLOW", List.of("WRITE_REVERSIBLE [GH-90000]"), List.of(), List.of()));
            registry.register(buildPolicy("p-deny", "Deny Policy", // GH-90000
                    2, "DENY", List.of("WRITE_REVERSIBLE [GH-90000]"), List.of(), List.of()));

            PolicyEvaluationContext ctx = buildContext("WRITE_REVERSIBLE", "high", "tenant-1"); // GH-90000
            PolicyDecisionRecord record = runPromise(() -> engine.evaluateContext(ctx)); // GH-90000

            assertThat(record.decision()).isEqualTo("DENY [GH-90000]");
        }

        @Test
        @DisplayName("should record audit decision for every evaluation [GH-90000]")
        void shouldRecordAuditDecision() { // GH-90000
            PolicyEvaluationContext ctx = buildContext("READ", "low", "tenant-1"); // GH-90000

            runPromise(() -> engine.evaluateContext(ctx)); // GH-90000

            assertThat(auditRecords).hasSize(1); // GH-90000
            assertThat(auditRecords.getFirst().context().tenantId()).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("tenant-scoped policy should only match its tenant [GH-90000]")
        void tenantScopedPolicyShouldOnlyMatchItsTenant() { // GH-90000
            registry.register(buildPolicy("p-acme", "ACME Only", // GH-90000
                    1, "DENY", List.of(), List.of(), List.of("acme-tenant [GH-90000]")));

            PolicyEvaluationContext acmeCtx = buildContext("WRITE_REVERSIBLE", "high", "acme-tenant"); // GH-90000
            PolicyEvaluationContext otherCtx = buildContext("WRITE_REVERSIBLE", "low", "other-tenant"); // GH-90000

            PolicyDecisionRecord acmeRecord = runPromise(() -> engine.evaluateContext(acmeCtx)); // GH-90000
            PolicyDecisionRecord otherRecord = runPromise(() -> engine.evaluateContext(otherCtx)); // GH-90000

            assertThat(acmeRecord.decision()).isEqualTo("DENY [GH-90000]");
            assertThat(otherRecord.decision()).isEqualTo("ALLOW [GH-90000]"); // no policies match, low criticality
        }
    }

    // =========================================================================
    // PolicyEngineImpl — legacy evaluate interface
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.evaluate (legacy) [GH-90000]")
    class LegacyEvaluateTests {

        @Test
        @DisplayName("should return true for allowed actions [GH-90000]")
        void shouldReturnTrueForAllowed() { // GH-90000
            Boolean result = runPromise(() -> engine.evaluate("any-policy", // GH-90000
                    Map.of("actionClass", "READ", "criticality", "low"))); // GH-90000

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false for denied actions (hard default) [GH-90000]")
        void shouldReturnFalseForDenied() { // GH-90000
            Boolean result = runPromise(() -> engine.evaluate("any-policy", // GH-90000
                    Map.of("actionClass", "WRITE_IRREVERSIBLE", "criticality", "critical"))); // GH-90000

            assertThat(result).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // PolicyEngineImpl — policyExists
    // =========================================================================

    @Nested
    @DisplayName("PolicyEngineImpl.policyExists [GH-90000]")
    class PolicyExistsTests {

        @Test
        @DisplayName("should return true for registered policy [GH-90000]")
        void shouldReturnTrueForRegistered() { // GH-90000
            registry.register(buildPolicy("my-policy", "My Policy", // GH-90000
                    1, "ALLOW", List.of(), List.of(), List.of())); // GH-90000

            Boolean exists = runPromise(() -> engine.policyExists("my-policy [GH-90000]"));
            assertThat(exists).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false for unregistered policy [GH-90000]")
        void shouldReturnFalseForUnregistered() { // GH-90000
            Boolean exists = runPromise(() -> engine.policyExists("nonexistent [GH-90000]"));
            assertThat(exists).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CompiledPolicy buildPolicy( // GH-90000
            String id, String name, int priority, String decision,
            List<String> actionClasses, List<String> criticalities,
            List<String> tenantScopes) {
        return new CompiledPolicy( // GH-90000
                id, name, priority,
                actionClasses, criticalities, tenantScopes,
                decision, List.of(), List.of(), // GH-90000
                Map.of(), true); // GH-90000
    }

    private PolicyEvaluationContext buildContext( // GH-90000
            String actionClass, String criticality, String tenantId) {
        return new PolicyEvaluationContext( // GH-90000
                "agent-1", tenantId, actionClass,
                "target-type", "target-1", "tool-1",
                criticality, "REVERSIBLE",
                Map.of(), Instant.now()); // GH-90000
    }
}
