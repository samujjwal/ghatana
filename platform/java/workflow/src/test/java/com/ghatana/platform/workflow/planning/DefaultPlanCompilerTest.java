/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DefaultPlanCompiler tests [GH-90000]")
class DefaultPlanCompilerTest extends EventloopTestBase {

    private DefaultPlanCompiler compiler;

    @BeforeEach
    void setUp() { // GH-90000
        compiler = new DefaultPlanCompiler(); // GH-90000
    }

    // ── Happy-path ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful compilation [GH-90000]")
    class SuccessfulCompilation {

        @Test
        @DisplayName("compiles objective with no context actions → stub action [GH-90000]")
        void compilesWithNoContextActions() { // GH-90000
            PlanGraph graph = runPromise(() -> compiler.compile("agent-1", "tenant-1", "Do something")); // GH-90000
            assertThat(graph.agentId()).isEqualTo("agent-1 [GH-90000]");
            assertThat(graph.objective()).isEqualTo("Do something [GH-90000]");
            assertThat(graph.actions()).hasSize(1); // GH-90000
            assertThat(graph.actions().get(0).actionClass()).isEqualTo(ActionClass.DRAFT); // GH-90000
        }

        @Test
        @DisplayName("compiles with supplied actions in context [GH-90000]")
        void compilesWithContextActions() { // GH-90000
            List<PlannedAction> actions = List.of( // GH-90000
                    PlannedAction.simple("a1", "Read data", ActionClass.READ), // GH-90000
                    new PlannedAction("a2", "Write result", null, Set.of("a1 [GH-90000]"), ActionClass.WRITE_REVERSIBLE, false));
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Multi-step", Map.of("actions", actions))); // GH-90000
            assertThat(graph.actions()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("returns unique planId on each compile [GH-90000]")
        void returnsUniquePlanId() { // GH-90000
            PlanGraph g1 = runPromise(() -> compiler.compile("a", "t", "Obj")); // GH-90000
            PlanGraph g2 = runPromise(() -> compiler.compile("a", "t", "Obj")); // GH-90000
            assertThat(g1.planId()).isNotEqualTo(g2.planId()); // GH-90000
        }

        @Test
        @DisplayName("convenience overload (no context) delegates correctly [GH-90000]")
        void convenienceOverloadDelegates() { // GH-90000
            PlanGraph graph = runPromise(() -> compiler.compile("agent-1", "tenant-1", "Obj")); // GH-90000
            assertThat(graph).isNotNull(); // GH-90000
            assertThat(graph.agentId()).isEqualTo("agent-1 [GH-90000]");
        }

        @Test
        @DisplayName("empty actions list in context produces stub action [GH-90000]")
        void emptyActionsListProducesStub() { // GH-90000
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Objective", Map.of("actions", List.of()))); // GH-90000
            assertThat(graph.actions()).hasSize(1); // GH-90000
            assertThat(graph.actions().get(0).actionClass()).isEqualTo(ActionClass.DRAFT); // GH-90000
        }
    }

    // ── Approval enrichment ────────────────────────────────────────────────

    @Nested
    @DisplayName("Approval enrichment [GH-90000]")
    class ApprovalEnrichment {

        @Test
        @DisplayName("WRITE_IRREVERSIBLE action gets requiresApproval=true [GH-90000]")
        void irreversibleActionForcesApproval() { // GH-90000
            List<PlannedAction> actions = List.of( // GH-90000
                    new PlannedAction("a1", "Send email", "email-tool", Set.of(), // GH-90000
                            ActionClass.WRITE_IRREVERSIBLE, false));
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions))); // GH-90000
            assertThat(graph.actions().get(0).requiresApproval()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("CALL_EXTERNAL action gets requiresApproval=true [GH-90000]")
        void externalCallForcesApproval() { // GH-90000
            List<PlannedAction> actions = List.of( // GH-90000
                    new PlannedAction("a1", "Call payment API", "payment-tool", Set.of(), // GH-90000
                            ActionClass.CALL_EXTERNAL, false));
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions))); // GH-90000
            assertThat(graph.actions().get(0).requiresApproval()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("READ action keeps requiresApproval=false [GH-90000]")
        void readActionKeepsFalse() { // GH-90000
            List<PlannedAction> actions = List.of( // GH-90000
                    PlannedAction.simple("a1", "Read doc", ActionClass.READ)); // GH-90000
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions))); // GH-90000
            assertThat(graph.actions().get(0).requiresApproval()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("POLICY_CHANGE action gets requiresApproval=true [GH-90000]")
        void policyChangeForcesApproval() { // GH-90000
            List<PlannedAction> actions = List.of( // GH-90000
                    new PlannedAction("a1", "Update policy", null, Set.of(), // GH-90000
                            ActionClass.POLICY_CHANGE, false));
            PlanGraph graph = runPromise(() -> compiler.compile( // GH-90000
                    "agent-1", "tenant-1", "Obj", Map.of("actions", actions))); // GH-90000
            assertThat(graph.actions().get(0).requiresApproval()).isTrue(); // GH-90000
        }
    }

    // ── Validation failures ────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation failures [GH-90000]")
    class ValidationFailures {

        @Test
        @DisplayName("rejects blank agentId [GH-90000]")
        void rejectsBlankAgentId() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> compiler.compile("  ", "tenant-1", "Obj"))) // GH-90000
                    .isInstanceOf(PlanCompilationException.class) // GH-90000
                    .hasMessageContaining("agentId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank tenantId [GH-90000]")
        void rejectsBlankTenantId() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> compiler.compile("agent-1", "", "Obj"))) // GH-90000
                    .isInstanceOf(PlanCompilationException.class) // GH-90000
                    .hasMessageContaining("tenantId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank objective [GH-90000]")
        void rejectsBlankObjective() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> compiler.compile("agent-1", "tenant-1", "   "))) // GH-90000
                    .isInstanceOf(PlanCompilationException.class) // GH-90000
                    .hasMessageContaining("objective [GH-90000]");
        }

        @Test
        @DisplayName("rejects non-PlannedAction elements in actions list [GH-90000]")
        void rejectsNonPlannedActionElement() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> compiler.compile("a", "t", "O", Map.of("actions", List.of("bad-element [GH-90000]")))))
                    .isInstanceOf(PlanCompilationException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects non-list actions context value [GH-90000]")
        void rejectsNonListContextValue() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> compiler.compile("a", "t", "O", Map.of("actions", "not-a-list")))) // GH-90000
                    .isInstanceOf(PlanCompilationException.class); // GH-90000
        }
    }

    // ── PlanCompilationException ────────────────────────────────────────────

    @Nested
    @DisplayName("PlanCompilationException [GH-90000]")
    class PlanCompilationExceptionTests {

        @Test
        @DisplayName("includes action ID in message when provided [GH-90000]")
        void includesActionIdInMessage() { // GH-90000
            PlanCompilationException ex = new PlanCompilationException("invalid class", "a1"); // GH-90000
            assertThat(ex.getMessage()).contains("a1 [GH-90000]");
            assertThat(ex.offendingActionId()).isEqualTo("a1 [GH-90000]");
        }

        @Test
        @DisplayName("message has no action ID placeholder when null [GH-90000]")
        void messageWithoutActionId() { // GH-90000
            PlanCompilationException ex = new PlanCompilationException("bad input [GH-90000]");
            assertThat(ex.getMessage()).contains("bad input [GH-90000]");
            assertThat(ex.offendingActionId()).isNull(); // GH-90000
        }
    }
}
