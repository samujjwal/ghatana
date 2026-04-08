/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("PlanGraph tests")
class PlanGraphTest {

    // ── PlannedAction ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("PlannedAction validation")
    class PlannedActionValidation {

        @Test
        @DisplayName("constructs successfully with all fields")
        void constructsWithAllFields() {
            PlannedAction action = new PlannedAction(
                    "a1", "Retrieve context", "context-tool",
                    Set.of(), ActionClass.READ, false);
            assertThat(action.actionId()).isEqualTo("a1");
            assertThat(action.specification()).isEqualTo("Retrieve context");
            assertThat(action.toolId()).isEqualTo("context-tool");
            assertThat(action.dependencies()).isEmpty();
            assertThat(action.actionClass()).isEqualTo(ActionClass.READ);
            assertThat(action.requiresApproval()).isFalse();
        }

        @Test
        @DisplayName("simple factory produces no-tool, no-dependency action")
        void simpleFactory() {
            PlannedAction action = PlannedAction.simple("a1", "Spec", ActionClass.DRAFT);
            assertThat(action.toolId()).isNull();
            assertThat(action.dependencies()).isEmpty();
            assertThat(action.requiresApproval()).isFalse();
        }

        @Test
        @DisplayName("dependencies are immutable copy")
        void dependenciesAreImmutable() {
            PlannedAction action = new PlannedAction(
                    "a2", "Write result", null, Set.of("a1"), ActionClass.WRITE_REVERSIBLE, false);
            assertThat(action.dependencies()).containsExactly("a1");
            assertThatThrownBy(() -> action.dependencies().add("a3"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("rejects blank actionId")
        void rejectsBlankActionId() {
            assertThatThrownBy(() -> new PlannedAction(
                    "  ", "Spec", null, Set.of(), ActionClass.READ, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("actionId");
        }

        @Test
        @DisplayName("rejects blank specification")
        void rejectsBlankSpecification() {
            assertThatThrownBy(() -> new PlannedAction(
                    "a1", "  ", null, Set.of(), ActionClass.READ, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("specification");
        }

        @Test
        @DisplayName("rejects null actionClass")
        void rejectsNullActionClass() {
            assertThatThrownBy(() -> new PlannedAction(
                    "a1", "Spec", null, Set.of(), null, false))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ── PlanGraph ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PlanGraph construction")
    class PlanGraphConstruction {

        @Test
        @DisplayName("builds empty plan")
        void buildsEmptyPlan() {
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Do nothing", List.of());
            assertThat(plan.planId()).isEqualTo("p1");
            assertThat(plan.agentId()).isEqualTo("agent-1");
            assertThat(plan.actions()).isEmpty();
            assertThat(plan.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("builds single-action plan")
        void buildsSingleAction() {
            PlannedAction action = PlannedAction.simple("a1", "Read data", ActionClass.READ);
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Objective", List.of(action));
            assertThat(plan.actions()).hasSize(1);
        }

        @Test
        @DisplayName("builds linear chain of actions")
        void buildsLinearChain() {
            PlannedAction a1 = PlannedAction.simple("a1", "Step 1", ActionClass.READ);
            PlannedAction a2 = new PlannedAction("a2", "Step 2", null, Set.of("a1"), ActionClass.DRAFT, false);
            PlannedAction a3 = new PlannedAction("a3", "Step 3", null, Set.of("a2"), ActionClass.WRITE_REVERSIBLE, false);
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Multi-step", List.of(a1, a2, a3));
            assertThat(plan.actions()).hasSize(3);
        }

        @Test
        @DisplayName("actions list is immutable")
        void actionsAreImmutable() {
            PlanGraph plan = PlanGraph.of("p1", "agent-1", "Obj", List.of());
            assertThatThrownBy(() -> plan.actions().add(
                    PlannedAction.simple("a1", "Spec", ActionClass.READ)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("rejects blank planId")
        void rejectsBlankPlanId() {
            assertThatThrownBy(() -> PlanGraph.of("", "agent-1", "Obj", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("planId");
        }

        @Test
        @DisplayName("rejects blank agentId")
        void rejectsBlankAgentId() {
            assertThatThrownBy(() -> PlanGraph.of("p1", "  ", "Obj", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("rejects blank objective")
        void rejectsBlankObjective() {
            assertThatThrownBy(() -> PlanGraph.of("p1", "agent-1", "", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("objective");
        }
    }

    // ── Cycle detection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cycle detection")
    class CycleDetection {

        @Test
        @DisplayName("detects simple 2-node cycle (a1 → a2 → a1)")
        void detectsTwoNodeCycle() {
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("a2"), ActionClass.READ, false);
            PlannedAction a2 = new PlannedAction("a2", "S2", null, Set.of("a1"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2)))
                    .isInstanceOf(PlanCycleException.class)
                    .hasMessageContaining("cycle");
        }

        @Test
        @DisplayName("detects three-node cycle")
        void detectsThreeNodeCycle() {
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("a3"), ActionClass.READ, false);
            PlannedAction a2 = new PlannedAction("a2", "S2", null, Set.of("a1"), ActionClass.READ, false);
            PlannedAction a3 = new PlannedAction("a3", "S3", null, Set.of("a2"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2, a3)))
                    .isInstanceOf(PlanCycleException.class);
        }

        @Test
        @DisplayName("allows diamond DAG (no cycle)")
        void allowsDiamondDag() {
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ);
            PlannedAction a2 = new PlannedAction("a2", "Left", null, Set.of("a1"), ActionClass.READ, false);
            PlannedAction a3 = new PlannedAction("a3", "Right", null, Set.of("a1"), ActionClass.READ, false);
            PlannedAction a4 = new PlannedAction("a4", "Join", null, Set.of("a2", "a3"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Diamond", List.of(a1, a2, a3, a4));
            assertThat(plan.actions()).hasSize(4);
        }

        @Test
        @DisplayName("throws on unknown dependency reference")
        void throwsOnUnknownDependency() {
            PlannedAction a1 = new PlannedAction("a1", "S1", null, Set.of("nonexistent"), ActionClass.READ, false);
            assertThatThrownBy(() -> PlanGraph.of("p1", "ag", "Obj", List.of(a1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nonexistent");
        }
    }

    // ── Traversal helpers ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Traversal helpers")
    class TraversalHelpers {

        @Test
        @DisplayName("roots() returns actions with no dependencies")
        void rootsReturnsNoDependencyActions() {
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ);
            PlannedAction a2 = new PlannedAction("a2", "Child", null, Set.of("a1"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2));
            assertThat(plan.roots()).containsExactly(a1);
        }

        @Test
        @DisplayName("dependents() returns actions that depend on the given ID")
        void dependentsReturnsChildren() {
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ);
            PlannedAction a2 = new PlannedAction("a2", "Child1", null, Set.of("a1"), ActionClass.DRAFT, false);
            PlannedAction a3 = new PlannedAction("a3", "Child2", null, Set.of("a1"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2, a3));
            assertThat(plan.dependents("a1")).containsExactlyInAnyOrder(a2, a3);
        }

        @Test
        @DisplayName("dependents() returns empty list for leaf nodes")
        void dependentsEmptyForLeaf() {
            PlannedAction a1 = PlannedAction.simple("a1", "Root", ActionClass.READ);
            PlannedAction a2 = new PlannedAction("a2", "Leaf", null, Set.of("a1"), ActionClass.DRAFT, false);
            PlanGraph plan = PlanGraph.of("p1", "ag", "Obj", List.of(a1, a2));
            assertThat(plan.dependents("a2")).isEmpty();
        }
    }
}
