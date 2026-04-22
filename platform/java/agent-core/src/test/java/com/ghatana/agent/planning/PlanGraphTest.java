/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.planning;

import com.ghatana.agent.framework.governance.ActionClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PlanGraph} and {@link PlannedAction} records.
 */
@DisplayName("PlanGraph tests [GH-90000]")
class PlanGraphTest {

    // ── PlannedAction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("PlannedAction validation [GH-90000]")
    class PlannedActionValidation {

        @Test
        @DisplayName("constructs successfully with all fields [GH-90000]")
        void constructsWithAllFields() { // GH-90000
            PlannedAction action = new PlannedAction( // GH-90000
                    "a1", "Retrieve context", "context-tool",
                    Set.of(), ActionClass.READ, false); // GH-90000
            assertThat(action.actionId()).isEqualTo("a1 [GH-90000]");
            assertThat(action.specification()).isEqualTo("Retrieve context [GH-90000]");
            assertThat(action.toolId()).isEqualTo("context-tool [GH-90000]");
            assertThat(action.dependencies()).isEmpty(); // GH-90000
            assertThat(action.actionClass()).isEqualTo(ActionClass.READ); // GH-90000
            assertThat(action.requiresApproval()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("simple factory produces no-tool, no-dependency action [GH-90000]")
        void simpleFactory() { // GH-90000
            PlannedAction action = PlannedAction.simple("a1", "Spec", ActionClass.DRAFT); // GH-90000
            assertThat(action.toolId()).isNull(); // GH-90000
            assertThat(action.dependencies()).isEmpty(); // GH-90000
            assertThat(action.requiresApproval()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("dependencies are immutable copy [GH-90000]")
        void dependenciesAreImmutable() { // GH-90000
            PlannedAction action = new PlannedAction( // GH-90000
                    "a2", "Write result", null, Set.of("a1 [GH-90000]"), ActionClass.WRITE_REVERSIBLE, false);
            assertThat(action.dependencies()).containsExactly("a1 [GH-90000]");
            assertThatThrownBy(() -> action.dependencies().add("a3 [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects blank actionId [GH-90000]")
        void rejectsBlankActionId() { // GH-90000
            assertThatThrownBy(() -> new PlannedAction( // GH-90000
                    "  ", "Spec", null, Set.of(), ActionClass.READ, false)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("actionId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank specification [GH-90000]")
        void rejectsBlankSpecification() { // GH-90000
            assertThatThrownBy(() -> new PlannedAction( // GH-90000
                    "a1", "  ", null, Set.of(), ActionClass.READ, false)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("specification [GH-90000]");
        }

        @Test
        @DisplayName("rejects null actionClass [GH-90000]")
        void rejectsNullActionClass() { // GH-90000
            assertThatThrownBy(() -> new PlannedAction( // GH-90000
                    "a1", "Spec", null, Set.of(), null, false)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ── PlanGraph ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PlanGraph construction [GH-90000]")
    class PlanGraphConstruction {

        @Test
        @DisplayName("builds empty plan [GH-90000]")
        void buildsEmptyPlan() { // GH-90000
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Do nothing", List.of()); // GH-90000
            assertThat(plan.planId()).isEqualTo("p1 [GH-90000]");
            assertThat(plan.agentId()).isEqualTo("agent-1 [GH-90000]");
            assertThat(plan.actions()).isEmpty(); // GH-90000
            assertThat(plan.createdAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("builds single-action plan [GH-90000]")
        void buildsSingleAction() { // GH-90000
            PlannedAction action = PlannedAction.simple("a1", "Read data", ActionClass.READ); // GH-90000
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Objective", List.of(action)); // GH-90000
            assertThat(plan.actions()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("builds linear chain of actions [GH-90000]")
        void buildsLinearChain() { // GH-90000
            PlannedAction a1 = PlannedAction.simple("a1", "Step 1", ActionClass.READ); // GH-90000
            PlannedAction a2 = new PlannedAction("a2", "Step 2", null, Set.of("a1 [GH-90000]"), ActionClass.DRAFT, false);
            PlannedAction a3 = new PlannedAction("a3", "Step 3", null, Set.of("a2 [GH-90000]"), ActionClass.WRITE_REVERSIBLE, false);
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Multi-step", List.of(a1, a2, a3)); // GH-90000
            assertThat(plan.actions()).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("actions list is immutable [GH-90000]")
        void actionsAreImmutable() { // GH-90000
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Obj", List.of()); // GH-90000
            assertThatThrownBy(() -> plan.actions().add( // GH-90000
                    PlannedAction.simple("a1", "Spec", ActionClass.READ))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects blank planId [GH-90000]")
        void rejectsBlankPlanId() { // GH-90000
            assertThatThrownBy(() -> PlanGraph.of("", "agent-1", "Obj", List.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("planId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank agentId [GH-90000]")
        void rejectsBlankAgentId() { // GH-90000
            assertThatThrownBy(() -> PlanGraph.of("p1", "  ", "Obj", List.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("agentId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank objective [GH-90000]")
        void rejectsBlankObjective() { // GH-90000
            assertThatThrownBy(() -> PlanGraph.of("p1", "agent-1", "", List.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("objective [GH-90000]");
        }
    }

    // ── Cycle detection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cycle detection [GH-90000]")
    class CycleDetection {

        @Test
        @DisplayName("detects simple 2-node cycle (a1 → a2 → a1) [GH-90000]")
        void detectsTwoNodeCycle() { // GH-90000
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("a2 [GH-90000]"), ActionClass.READ, false);
            PlannedAction a2 = new PlannedAction("a2", "S2", null, Set.of("a1 [GH-90000]"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2))) // GH-90000
                    .isInstanceOf(PlanCycleException.class) // GH-90000
                    .hasMessageContaining("cycle [GH-90000]");
        }

        @Test
        @DisplayName("detects three-node cycle [GH-90000]")
        void detectsThreeNodeCycle() { // GH-90000
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("a3 [GH-90000]"), ActionClass.READ, false);
            PlannedAction a2 = new PlannedAction("a2", "S2", null, Set.of("a1 [GH-90000]"), ActionClass.READ, false);
            PlannedAction a3 = new PlannedAction("a3", "S3", null, Set.of("a2 [GH-90000]"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2, a3))) // GH-90000
                    .isInstanceOf(PlanCycleException.class); // GH-90000
        }

        @Test
        @DisplayName("allows diamond DAG (no cycle) [GH-90000]")
        void allowsDiamondDag() { // GH-90000
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ); // GH-90000
            PlannedAction a2 = new PlannedAction("a2", "Left", null, Set.of("a1 [GH-90000]"), ActionClass.READ, false);
            PlannedAction a3 = new PlannedAction("a3", "Right", null, Set.of("a1 [GH-90000]"), ActionClass.READ, false);
            PlannedAction a4 = new PlannedAction("a4", "Join", null, Set.of("a2", "a3"), ActionClass.DRAFT, false); // GH-90000
            PlanGraph plan = PlanGraph.of("p1", "ag", "Diamond", List.of(a1, a2, a3, a4)); // GH-90000
            assertThat(plan.actions()).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("throws on unknown dependency reference [GH-90000]")
        void throwsOnUnknownDependency() { // GH-90000
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("nonexistent [GH-90000]"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("nonexistent [GH-90000]");
        }
    }

    // ── Traversal helpers ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Traversal helpers [GH-90000]")
    class TraversalHelpers {

        @Test
        @DisplayName("roots() returns actions with no dependencies [GH-90000]")
        void rootsReturnsNoDependencyActions() { // GH-90000
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ); // GH-90000
            PlannedAction a2 = new PlannedAction("a2", "Child", null, Set.of("a1 [GH-90000]"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2)); // GH-90000
            assertThat(plan.roots()).containsExactly(a1); // GH-90000
        }

        @Test
        @DisplayName("dependents() returns actions that depend on the given ID [GH-90000]")
        void dependentsReturnsChildren() { // GH-90000
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ); // GH-90000
            PlannedAction a2 = new PlannedAction("a2", "Child1", null, Set.of("a1 [GH-90000]"), ActionClass.DRAFT, false);
            PlannedAction a3 = new PlannedAction("a3", "Child2", null, Set.of("a1 [GH-90000]"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2, a3)); // GH-90000
            assertThat(plan.dependents("a1 [GH-90000]")).containsExactlyInAnyOrder(a2, a3);
        }

        @Test
        @DisplayName("dependents() returns empty list for leaf nodes [GH-90000]")
        void dependentsEmptyForLeaf() { // GH-90000
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ); // GH-90000
            PlannedAction a2 = new PlannedAction("a2", "Leaf", null, Set.of("a1 [GH-90000]"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2)); // GH-90000
            assertThat(plan.dependents("a2 [GH-90000]")).isEmpty();
        }
    }
}
