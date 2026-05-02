/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void readAndDraftShouldNotBePrivileged() { 
            assertThat(ActionClass.READ.isPrivileged()).isFalse(); 
            assertThat(ActionClass.DRAFT.isPrivileged()).isFalse(); 
        }

        @Test
        @DisplayName("all write and external actions should be privileged")
        void writeAndExternalShouldBePrivileged() { 
            assertThat(ActionClass.WRITE_REVERSIBLE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.CALL_EXTERNAL.isPrivileged()).isTrue(); 
            assertThat(ActionClass.DELEGATE.isPrivileged()).isTrue(); 
            assertThat(ActionClass.MEMORY_MUTATION.isPrivileged()).isTrue(); 
            assertThat(ActionClass.POLICY_CHANGE.isPrivileged()).isTrue(); 
        }

        @Test
        @DisplayName("irreversible actions should be correctly classified")
        void irreversibleActions() { 
            assertThat(ActionClass.WRITE_IRREVERSIBLE.isIrreversible()).isTrue(); 
            assertThat(ActionClass.CALL_EXTERNAL.isIrreversible()).isTrue(); 
            assertThat(ActionClass.POLICY_CHANGE.isIrreversible()).isTrue(); 
            assertThat(ActionClass.READ.isIrreversible()).isFalse(); 
            assertThat(ActionClass.WRITE_REVERSIBLE.isIrreversible()).isFalse(); 
            assertThat(ActionClass.DELEGATE.isIrreversible()).isFalse(); 
        }

        @Test
        @DisplayName("should have exactly 8 values per spec")
        void shouldHaveEightValues() { 
            assertThat(ActionClass.values()).hasSize(8); 
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
        void shouldHaveThreeValues() { 
            assertThat(ReversibilityClass.values()).hasSize(3); 
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
        void denyAndEscalateShouldNotBePermitted() { 
            assertThat(PolicyDecisionType.DENY.isPermitted()).isFalse(); 
            assertThat(PolicyDecisionType.ESCALATE.isPermitted()).isFalse(); 
        }

        @Test
        @DisplayName("ALLOW variants should be permitted")
        void allowVariantsShouldBePermitted() { 
            assertThat(PolicyDecisionType.ALLOW.isPermitted()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW_WITH_APPROVAL.isPermitted()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW_WITH_COMPENSATION.isPermitted()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW_WITH_MONITORING.isPermitted()).isTrue(); 
        }

        @Test
        @DisplayName("conditional decisions should have obligations")
        void conditionalShouldHaveObligations() { 
            assertThat(PolicyDecisionType.ALLOW_WITH_APPROVAL.hasObligations()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW_WITH_COMPENSATION.hasObligations()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW_WITH_MONITORING.hasObligations()).isTrue(); 
            assertThat(PolicyDecisionType.ALLOW.hasObligations()).isFalse(); 
            assertThat(PolicyDecisionType.DENY.hasObligations()).isFalse(); 
        }

        @Test
        @DisplayName("should have exactly 6 values per spec")
        void shouldHaveSixValues() { 
            assertThat(PolicyDecisionType.values()).hasSize(6); 
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
        void shouldConstructWithRequiredFields() { 
            ActionIntent intent = new ActionIntent( 
                    "trace-1", "agent-1", "tenant-1",
                    ActionClass.WRITE_REVERSIBLE, "purchase_order", "po-123",
                    "po-service", "sha256:abc",
                    ReversibilityClass.REVERSIBLE, "high",
                    "user-1", null, "v1.0",
                    Instant.now()); 

            assertThat(intent.traceId()).isEqualTo("trace-1");
            assertThat(intent.agentId()).isEqualTo("agent-1");
            assertThat(intent.isPrivileged()).isTrue(); 
        }

        @Test
        @DisplayName("should reject null required fields")
        void shouldRejectNullRequiredFields() { 
            assertThatThrownBy(() -> new ActionIntent( 
                    null, "agent-1", "tenant-1",
                    ActionClass.READ, "target", null, null, null,
                    ReversibilityClass.REVERSIBLE, "low", "user", null, null,
                    Instant.now())) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("READ actions should not be privileged")
        void readActionsShouldNotBePrivileged() { 
            ActionIntent intent = new ActionIntent( 
                    "trace-1", "agent-1", "tenant-1",
                    ActionClass.READ, "target", null, null, null,
                    ReversibilityClass.REVERSIBLE, "low", "user", null, null,
                    Instant.now()); 

            assertThat(intent.isPrivileged()).isFalse(); 
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
        void allowShouldCreatePermittedDecision() { 
            PolicyDecision decision = PolicyDecision.allow(List.of("policy-1"), "Low risk");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.ALLOW); 
            assertThat(decision.isPermitted()).isTrue(); 
            assertThat(decision.policyRefsApplied()).containsExactly("policy-1");
            assertThat(decision.reasons()).containsExactly("Low risk");
            assertThat(decision.obligations()).isEmpty(); 
        }

        @Test
        @DisplayName("deny() should create non-permitted decision")
        void denyShouldCreateNonPermittedDecision() { 
            PolicyDecision decision = PolicyDecision.deny( 
                    List.of("policy-2"), List.of("rule-1"), "High risk action");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.DENY); 
            assertThat(decision.isPermitted()).isFalse(); 
            assertThat(decision.matchedRules()).containsExactly("rule-1");
        }

        @Test
        @DisplayName("requireApproval() should carry required roles")
        void requireApprovalShouldCarryRoles() { 
            PolicyDecision decision = PolicyDecision.requireApproval( 
                    List.of("policy-3"), List.of("manager", "compliance"), "Needs review");

            assertThat(decision.decision()).isEqualTo(PolicyDecisionType.ALLOW_WITH_APPROVAL); 
            assertThat(decision.requiredApprovals()).containsExactlyInAnyOrder("manager", "compliance"); 
            assertThat(decision.isPermitted()).isTrue(); 
        }

        @Test
        @DisplayName("lists should be immutable copies")
        void listsShouldBeImmutable() { 
            PolicyDecision decision = PolicyDecision.allow(List.of("p"), "ok");

            assertThatThrownBy(() -> decision.reasons().add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class); 
            assertThatThrownBy(() -> decision.obligations().add(null)) 
                    .isInstanceOf(UnsupportedOperationException.class); 
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
        void shouldConstructWithAllFields() { 
            AgentDatasheet ds = new AgentDatasheet( 
                    "agent.procurement-assistant",
                    "2.1.0",
                    "platform.procurement",
                    List.of(Map.of("team", "platform-agent-runtime", "role", "technical-owner")), 
                    "tier-2",
                    AutonomyLevel.SUPERVISED,
                    "high",
                    List.of(ActionClass.READ, ActionClass.DRAFT, ActionClass.WRITE_REVERSIBLE), 
                    List.of(new AgentDatasheet.ToolPermission("po-service", 
                            List.of(ActionClass.READ, ActionClass.DRAFT))), 
                    List.of(new AgentDatasheet.MemoryBindingEntry( 
                            "tenant.procurement.shared", "read-write")),
                    "internal",
                    90,
                    List.of(new AgentDatasheet.ApprovalRule( 
                            ActionClass.WRITE_IRREVERSIBLE, List.of("manager", "compliance"))), 
                    List.of("eval.procurement.regression.v1"),
                    "runbook://agent-control/procurement-assistant",
                    "rollback-via-compensation",
                    "regulated",
                    List.of("production", "staging"), 
                    Instant.parse("2026-03-22T00:00:00Z"),
                    Instant.parse("2026-06-22T00:00:00Z"));

            assertThat(ds.agentId()).isEqualTo("agent.procurement-assistant");
            assertThat(ds.autonomyTier()).isEqualTo(AutonomyLevel.SUPERVISED); 
            assertThat(ds.allowedActionClasses()).hasSize(3); 
            assertThat(ds.toolPermissions()).hasSize(1); 
            assertThat(ds.memoryBindings()).hasSize(1); 
            assertThat(ds.approvalRules()).hasSize(1); 
            assertThat(ds.evaluationPackRefs()).containsExactly("eval.procurement.regression.v1");
            assertThat(ds.auditMode()).isEqualTo("regulated");
        }

        @Test
        @DisplayName("collections should be immutable")
        void collectionsShouldBeImmutable() { 
            AgentDatasheet ds = new AgentDatasheet( 
                    "agent-1", "1.0", "ns", List.of(), "tier-1", 
                    AutonomyLevel.ADVISORY, "low", List.of(), 
                    List.of(), List.of(), "internal", 30, 
                    List.of(), List.of(), null, null, 
                    "standard", List.of(), null, null); 

            assertThatThrownBy(() -> ds.allowedActionClasses().add(ActionClass.READ)) 
                    .isInstanceOf(UnsupportedOperationException.class); 
            assertThatThrownBy(() -> ds.owners().add(Map.of())) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("should reject null required fields")
        void shouldRejectNullRequiredFields() { 
            assertThatThrownBy(() -> new AgentDatasheet( 
                    null, "1.0", "ns", List.of(), "tier-1", 
                    AutonomyLevel.ADVISORY, "low", List.of(), 
                    List.of(), List.of(), "internal", 30, 
                    List.of(), List.of(), null, null, 
                    "standard", List.of(), null, null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
