/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.approval.ApprovalProof;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AgentApprovalGate.
 */
@DisplayName("AgentApprovalGate Tests (DC-P9-001)")
class AgentApprovalGateTest {

    @Nested
    @DisplayName("Approval Required")
    class ApprovalRequiredTests {

        @Test
        @DisplayName("allows dispatch when approval is not required")
        void allowsDispatchWhenApprovalIsNotRequired() {
            AgentApprovalGate.ApprovalChecker checker = mock(AgentApprovalGate.ApprovalChecker.class);
            when(checker.isApprovalRequired("agent-1", "tenant-1")).thenReturn(false);

            AgentApprovalGate gate = new AgentApprovalGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1"));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("allows dispatch when approval is required and proof is valid")
        void allowsDispatchWhenApprovalIsRequiredAndProofIsValid() {
            AgentApprovalGate.ApprovalChecker checker = mock(AgentApprovalGate.ApprovalChecker.class);
            when(checker.isApprovalRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentApprovalGate gate = new AgentApprovalGate(checker);
            ApprovalProof proof = mock(ApprovalProof.class);
            when(proof.proofId()).thenReturn("approval-1");
            when(checker.isApprovalValid(eq("agent-1"), eq("tenant-1"), eq(proof))).thenReturn(true);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "approval-1", Map.of("tenantId", "tenant-1", "approvalProof", proof));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("rejects dispatch when approval is required but proof is absent")
        void rejectsDispatchWhenApprovalIsRequiredButProofIsAbsent() {
            AgentApprovalGate.ApprovalChecker checker = mock(AgentApprovalGate.ApprovalChecker.class);
            when(checker.isApprovalRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentApprovalGate gate = new AgentApprovalGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1"));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("Approval is required");
            assertThat(result.reason()).contains("approval proof is absent");
        }

        @Test
        @DisplayName("rejects dispatch when approval is required but proof is invalid")
        void rejectsDispatchWhenApprovalIsRequiredButProofIsInvalid() {
            AgentApprovalGate.ApprovalChecker checker = mock(AgentApprovalGate.ApprovalChecker.class);
            when(checker.isApprovalRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentApprovalGate gate = new AgentApprovalGate(checker);
            ApprovalProof proof = mock(ApprovalProof.class);
            when(proof.proofId()).thenReturn("approval-1");
            when(checker.isApprovalValid(eq("agent-1"), eq("tenant-1"), eq(proof))).thenReturn(false);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", "approval-1", Map.of("tenantId", "tenant-1", "approvalProof", proof));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("approval proof");
            assertThat(result.reason()).contains("is invalid");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("requires non-null checker")
        void requiresNonNullChecker() {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new AgentApprovalGate(null))
                .withMessageContaining("approvalChecker must not be null");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentApprovalGate.ApprovalChecker checker = mock(AgentApprovalGate.ApprovalChecker.class);
            AgentApprovalGate gate = new AgentApprovalGate(checker);

            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> gate.evaluate(null))
                .withMessageContaining("context must not be null");
        }
    }
}
