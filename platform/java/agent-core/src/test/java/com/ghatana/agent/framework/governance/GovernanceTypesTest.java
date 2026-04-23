/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.governance;

import com.ghatana.agent.framework.runtime.AutonomyLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP1/WP2 governance value objects: ActionClass, ReversibilityClass,
 * PolicyDecisionType, ActionIntent, PolicyDecision, PolicyObligation, AgentDatasheet.
 */
@DisplayName("Governance Types")
class GovernanceTypesTest {

    // =========================================================================
    // WP1: ActionClass
    // =========================================================================

    @Nested
    @DisplayName("ActionClass")
    class ActionClassTests {

        @Test
        @DisplayName("READ and DRAFT should not be privileged")
        void readAndDraftShouldNotBePrivileged() { // GH-90000
            assertThat(ActionClass.READ.isPrivileged()).isFalse(); // GH-90000
            assertThat(ActionClass.DRAFT.isPrivileged()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("all write and external actions should be privileged")
        void writeAndExternalShouldBePrivileged() { // GH-90000
            assertThat(ActionClass.WRITE_REVERSIBLE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.CALL_EXTERNAL.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.DELEGATE.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.MEMORY_MUTATION.isPrivileged()).isTrue(); // GH-90000
            assertThat(ActionClass.POLICY_CHANGE.isPrivileged()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("irreversible actions should be correctly classified")
        void irreversibleActions() { // GH-90000
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isIrreversible()).isTrue(); // GH-90000
            assertThat(ActionClass.CALL_EXTERNAL.isIrreversible()).isTrue(); // GH-90000
            assertThat(ActionClass.POLICY_CHANGE.isIrreversible()).isTrue(); // GH-90000
            assertThat(ActionClass.READ.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.WRITE_REVERSIBLE.isIrreversible()).isFalse(); // GH-90000
            assertThat(ActionClass.DELEGATE.isIrreversible()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 8 values per spec")
        void shouldHaveEightValues() { // GH-90000
            assertThat(ActionClass.values()).hasSize(8); // GH-90000
        }
    }

    // =========================================================================
    // WP1: ReversibilityClass
    // =========================================================================

    @Nested
    @DisplayName("ReversibilityClass")
    class ReversibilityClassTests {

        @Test
        @DisplayName("should have exactly 3 values")
        void shouldHaveThreeValues() { // GH-90000
            assertThat(ReversibilityClass.values()).hasSize(3); // GH-90000
            assertThat(ReversibilityClass.valueOf("REVERSIBLE")).isNotNull();
            assertThat(ReversibilityClass.valueOf("COMPENSATABLE")).isNotNull();
            assertThat(ReversibilityClass.valueOf("IRREVERSIBLE")).isNotNull();
        }
    }

    // =========================================================================
    // WP2: PolicyDecisionType
    // =========================================================================

    @Nested
    @DisplayName("PolicyDecisionType")
    class PolicyDecisionTypeTests {

        @Test
        @DisplayName("DENY and ESCALATE should not be permitted")
        void denyAndEscalateShouldNotBePermitted() { // GH-90000
            assertThat(PolicyDecisionType.DENY.isPermitted()).isFalse(); // GH-90000
            assertThat(PolicyDecisionType.ESCALATE.isPermitted()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("ALLOW variants should be permitted")
        void allowVariantsShouldBePermitted() { // GH-90000
            assertThat(PolicyDecisionType.ALLOW.isPermitted()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_APPROVAL.isPermitted()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_COMPENSATION.isPermitted()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_MONITORING.isPermitted()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("conditional decisions should have obligations")
        void conditionalShouldHaveObligations() { // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_APPROVAL.hasObligations()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_COMPENSATION.hasObligations()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW_WITH_MONITORING.hasObligations()).isTrue(); // GH-90000
            assertThat(PolicyDecisionType.ALLOW.hasObligations()).isFalse(); // GH-90000
            assertThat(PolicyDecisionType.DENY.hasObligations()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 6 values per spec")
        void shouldHaveSixValues() { // GH-90000
            assertThat(PolicyDecisionType.values()).hasSize(6); // GH-90000
        }
    }

    // =========================================================================
    // WP2: ActionIntent
    // =========================================================================

    @Nested
    @DisplayName("ActionIntent")
    class ActionIntentTests {

        @Test
        @DisplayName("should construct with all required fields")
        void shouldConstructWithRequiredFields() { // GH-90000
            ActionIntent intent = new ActionIntent( // GH-90000
                    "trace-1", "agent-1", "tenant-1",
                    ActionClass.WRITE_REVERSIBLE, "purchase_order", "po-123",
                    "po-service", "sha256:abc",
                    ReversibilityClass.REVERSIBLE, "high",
                    "user-1", null, "v1.0",
                    Instant.now()); // GH-90000

            assertThat(intent.traceId()).isEqualTo("trace-1");
            assertThat(intent.agentId()).isEqualTo("agent-1");
            assertThat(intent.isPrivileged()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject null required fields")
        void shouldRejectNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> new ActionIntent( // GH-90000
                    null, "agent-1", "tenant-1",
                    ActionClass.READ, "target", null, null, null,
                    ReversibilityClass.REVERSIBLE, "low", "user", null, null,
                    Instant.now())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("READ actions should not be privileged")
        void readActionsShouldNotBePrivileged() { // GH-90000
            ActionIntent intent = new ActionIntent( // GH-90000
                    "trace-1", "agent-1", "tenant-1",
                    ActionClass.READ, "target", null, null, null,
                    ReversibilityClass.REVERSIBLE, "low", "user", null, null,
                    Instant.now()); // GH-90000

            assertThat(intent.isPrivileged()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // WP2: PolicyDecision
    // =========================================================================

    @Nested
    @DisplayName("PolicyDecision")
    class PolicyDecisionTests {

        @Test
        @DisplayName("allow() should create permitted decision")
        void allowShouldCreatePermittedDecision() { // GH-90000
            PolicyDecision decision = PolicyDecision.allow(List.of("policy-1"), "Low risk");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.ALLOW); // GH-90000
            assertThat(decision.isPermitted()).isTrue(); // GH-90000
            assertThat(decision.policyRefsApplied()).containsExactly("policy-1");
            assertThat(decision.reasons()).containsExactly("Low risk");
            assertThat(decision.obligations()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("deny() should create non-permitted decision")
        void denyShouldCreateNonPermittedDecision() { // GH-90000
            PolicyDecision decision = PolicyDecision.deny( // GH-90000
                    List.of("policy-2"), List.of("rule-1"), "High risk action");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.DENY); // GH-90000
            assertThat(decision.isPermitted()).isFalse(); // GH-90000
            assertThat(decision.matchedRules()).containsExactly("rule-1");
        }

        @Test
        @DisplayName("requireApproval() should carry required roles")
        void requireApprovalShouldCarryRoles() { // GH-90000
            PolicyDecision decision = PolicyDecision.requireApproval( // GH-90000
                    List.of("policy-3"), List.of("manager", "compliance"), "Needs review");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.ALLOW_WITH_APPROVAL); // GH-90000
            assertThat(decision.requiredApprovals()).containsExactlyInAnyOrder("manager", "compliance"); // GH-90000
            assertThat(decision.isPermitted()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("lists should be immutable copies")
        void listsShouldBeImmutable() { // GH-90000
            PolicyDecision decision = PolicyDecision.allow(List.of("p"), "ok");

            assertThatThrownBy(() -> decision.reasons().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> decision.obligations().add(null)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // =========================================================================
    // WP2: AgentDatasheet
    // =========================================================================

    @Nested
    @DisplayName("AgentDatasheet")
    class AgentDatasheetTests {

        @Test
        @DisplayName("should construct with all fields from spec section 7.1")
        void shouldConstructWithAllFields() { // GH-90000
            AgentDatasheet ds = new AgentDatasheet( // GH-90000
                    "agent.procurement-assistant",
                    "2.1.0",
                    "platform.procurement",
                    List.of(Map.of("team", "platform-agent-runtime", "role", "technical-owner")), // GH-90000
                    "tier-2",
                    AutonomyLevel.SUPERVISED,
                    "high",
                    List.of(ActionClass.READ, ActionClass.DRAFT, ActionClass.WRITE_REVERSIBLE), // GH-90000
                    List.of(new AgentDatasheet.ToolPermission("po-service", // GH-90000
                            List.of(ActionClass.READ, ActionClass.DRAFT))), // GH-90000
                    List.of(new AgentDatasheet.MemoryBindingEntry( // GH-90000
                            "tenant.procurement.shared", "read-write")),
                    "internal",
                    90,
                    List.of(new AgentDatasheet.ApprovalRule( // GH-90000
                            ActionClass.WRITE_IRREVERSIBLE, List.of("manager", "compliance"))), // GH-90000
                    List.of("eval.procurement.regression.v1"),
                    "runbook://agent-control/procurement-assistant",
                    "rollback-via-compensation",
                    "regulated",
                    List.of("production", "staging"), // GH-90000
                    Instant.parse("2026-03-22T00:00:00Z"),
                    Instant.parse("2026-06-22T00:00:00Z"));

            assertThat(ds.agentId()).isEqualTo("agent.procurement-assistant");
            assertThat(ds.autonomyTier()).isEqualTo(AutonomyLevel.SUPERVISED); // GH-90000
            assertThat(ds.allowedActionClasses()).hasSize(3); // GH-90000
            assertThat(ds.toolPermissions()).hasSize(1); // GH-90000
            assertThat(ds.memoryBindings()).hasSize(1); // GH-90000
            assertThat(ds.approvalRules()).hasSize(1); // GH-90000
            assertThat(ds.evaluationPackRefs()).containsExactly("eval.procurement.regression.v1");
            assertThat(ds.auditMode()).isEqualTo("regulated");
        }

        @Test
        @DisplayName("collections should be immutable")
        void collectionsShouldBeImmutable() { // GH-90000
            AgentDatasheet ds = new AgentDatasheet( // GH-90000
                    "agent-1", "1.0", "ns", List.of(), "tier-1", // GH-90000
                    AutonomyLevel.ADVISORY, "low", List.of(), // GH-90000
                    List.of(), List.of(), "internal", 30, // GH-90000
                    List.of(), List.of(), null, null, // GH-90000
                    "standard", List.of(), null, null); // GH-90000

            assertThatThrownBy(() -> ds.allowedActionClasses().add(ActionClass.READ)) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
            assertThatThrownBy(() -> ds.owners().add(Map.of())) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject null required fields")
        void shouldRejectNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> new AgentDatasheet( // GH-90000
                    null, "1.0", "ns", List.of(), "tier-1", // GH-90000
                    AutonomyLevel.ADVISORY, "low", List.of(), // GH-90000
                    List.of(), List.of(), "internal", 30, // GH-90000
                    List.of(), List.of(), null, null, // GH-90000
                    "standard", List.of(), null, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
