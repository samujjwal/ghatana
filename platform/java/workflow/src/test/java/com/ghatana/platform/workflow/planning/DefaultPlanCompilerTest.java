/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.planning;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.planning.PlanGraph;
import com.ghatana.agent.planning.PlannedAction;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DefaultPlanCompiler} and {@link PlanCompilationException}.
 */
@DisplayName("DefaultPlanCompiler tests")
class DefaultPlanCompilerTest extends EventloopTestBase {

    private DefaultPlanCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new DefaultPlanCompiler();
    }

    // ── Happy-path ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful compilation")
    class SuccessfulCompilation {

        @Test
        @DisplayName("compiles objective with no context actions → stub action")
        void compilesWithNoContextActions() {
            PlanGraph graph = runPromise(() -> compiler.compile("agent-1", "tenant-1", "Do something"));
            assertThat(graph.agentId()).isEqualTo("agent-1");
            assertThat(graph.objective()).isEqualTo("Do something");
            assertThat(graph.actions()).hasSize(1);
            assertThat(graph.actions().get(0).actionClass()).isEqualTo(ActionClass.DRAFT);
        }

        @Test
        @DisplayName("compiles with supplied actions in context")
        void compilesWithContextActions() {
            List<PlannedAction> actions = List.of(
                    PlannedAction.simple("a1", "Read data", ActionClass.READ),
                    new PlannedAction("a2", "Write result", null, Set.of("a1"), ActionClass.WRITE_REVERSIBLE, false));
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Multi-step", Map.of("actions", actions)));
            assertThat(graph.actions()).hasSize(2);
        }

        @Test
        @DisplayName("returns unique planId on each compile")
        void returnsUniquePlanId() {
            PlanGraph g1 = runPromise(() -> compiler.compile("a", "t", "Obj"));
            PlanGraph g2 = runPromise(() -> compiler.compile("a", "t", "Obj"));
            assertThat(g1.planId()).isNotEqualTo(g2.planId());
        }

        @Test
        @DisplayName("convenience overload (no context) delegates correctly")
        void convenienceOverloadDelegates() {
            PlanGraph graph = runPromise(() -> compiler.compile("agent-1", "tenant-1", "Obj"));
            assertThat(graph).isNotNull();
            assertThat(graph.agentId()).isEqualTo("agent-1");
        }

        @Test
        @DisplayName("empty actions list in context produces stub action")
        void emptyActionsListProducesStub() {
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Objective", Map.of("actions", List.of())));
            assertThat(graph.actions()).hasSize(1);
            assertThat(graph.actions().get(0).actionClass()).isEqualTo(ActionClass.DRAFT);
        }
    }

    // ── Approval enrichment ────────────────────────────────────────────────

    @Nested
    @DisplayName("Approval enrichment")
    class ApprovalEnrichment {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE action gets requiresApproval=true")
        void irreversibleActionForcesApproval() {
            List<PlannedAction> actions = List.of(
                    new PlannedAction("a1", "Send email", "email-tool", Set.of(),
                            ActionClass.WRITE_IRREVERSIBLE, false));
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions)));
            assertThat(graph.actions().get(0).requiresApproval()).isTrue();
        }

        @Test
        @DisplayName("CALL_EXTERNAL action gets requiresApproval=true")
        void externalCallForcesApproval() {
            List<PlannedAction> actions = List.of(
                    new PlannedAction("a1", "Call payment API", "payment-tool", Set.of(),
                            ActionClass.CALL_EXTERNAL, false));
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions)));
            assertThat(graph.actions().get(0).requiresApproval()).isTrue();
        }

        @Test
        @DisplayName("READ action keeps requiresApproval=false")
        void readActionKeepsFalse() {
            List<PlannedAction> actions = List.of(
                    PlannedAction.simple("a1", "Read doc", ActionClass.READ));
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions)));
            assertThat(graph.actions().get(0).requiresApproval()).isFalse();
        }

        @Test
        @DisplayName("POLICY_CHANGE action gets requiresApproval=true")
        void policyChangeForcesApproval() {
            List<PlannedAction> actions = List.of(
                    new PlannedAction("a1", "Update policy", null, Set.of(),
                            ActionClass.POLICY_CHANGE, false));
            PlanGraph graph = runPromise(() -> compiler.compile(
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions)));
            assertThat(graph.actions().get(0).requiresApproval()).isTrue();
        }
    }

    // ── Validation failures ────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("rejects blank agentId")
        void rejectsBlankAgentId() {
            assertThatThrownBy(() ->
                    runPromise(() -> compiler.compile("  ", "tenant-1", "Obj")))
                    .isInstanceOf(PlanCompilationException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("rejects blank tenantId")
        void rejectsBlankTenantId() {
            assertThatThrownBy(() ->
                    runPromise(() -> compiler.compile("agent-1", "", "Obj")))
                    .isInstanceOf(PlanCompilationException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects blank objective")
        void rejectsBlankObjective() {
            assertThatThrownBy(() ->
                    runPromise(() -> compiler.compile("agent-1", "tenant-1", "   ")))
                    .isInstanceOf(PlanCompilationException.class)
                    .hasMessageContaining("objective");
        }

        @Test
        @DisplayName("rejects non-PlannedAction elements in actions list")
        void rejectsNonPlannedActionElement() {
            assertThatThrownBy(() ->
                    runPromise(() -> compiler.compile("a", "t", "O", Map.of("actions", List.of("bad-element")))))
                    .isInstanceOf(PlanCompilationException.class);
        }

        @Test
        @DisplayName("rejects non-list actions context value")
        void rejectsNonListContextValue() {
            assertThatThrownBy(() ->
                    runPromise(() -> compiler.compile("a", "t", "O", Map.of("actions", "not-a-list"))))
                    .isInstanceOf(PlanCompilationException.class);
        }
    }

    // ── PlanCompilationException ────────────────────────────────────────────

    @Nested
    @DisplayName("PlanCompilationException")
    class PlanCompilationExceptionTests {

        @Test
        @DisplayName("includes action ID in message when provided")
        void includesActionIdInMessage() {
            PlanCompilationException ex = new PlanCompilationException("invalid class", "a1");
            assertThat(ex.getMessage()).contains("a1");
            assertThat(ex.offendingActionId()).isEqualTo("a1");
        }

        @Test
        @DisplayName("message has no action ID placeholder when null")
        void messageWithoutActionId() {
            PlanCompilationException ex = new PlanCompilationException("bad input");
            assertThat(ex.getMessage()).contains("bad input");
            assertThat(ex.offendingActionId()).isNull();
        }
    }
}
