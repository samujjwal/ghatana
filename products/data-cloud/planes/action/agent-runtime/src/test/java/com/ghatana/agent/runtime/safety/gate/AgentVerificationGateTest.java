/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.approval.VerificationProof;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AgentVerificationGate.
 */
@DisplayName("AgentVerificationGate Tests (DC-P9-001)")
class AgentVerificationGateTest {

    @Nested
    @DisplayName("Verification Required")
    class VerificationRequiredTests {

        @Test
        @DisplayName("allows dispatch when verification is not required")
        void allowsDispatchWhenVerificationIsNotRequired() {
            AgentVerificationGate.VerificationChecker checker = mock(AgentVerificationGate.VerificationChecker.class);
            when(checker.isVerificationRequired("agent-1", "tenant-1")).thenReturn(false);

            AgentVerificationGate gate = new AgentVerificationGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1"));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("allows dispatch when verification is required and proof is valid")
        void allowsDispatchWhenVerificationIsRequiredAndProofIsValid() {
            AgentVerificationGate.VerificationChecker checker = mock(AgentVerificationGate.VerificationChecker.class);
            when(checker.isVerificationRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentVerificationGate gate = new AgentVerificationGate(checker);
            VerificationProof proof = mock(VerificationProof.class);
            when(proof.proofId()).thenReturn("verification-1");
            when(checker.isVerificationValid(eq("agent-1"), eq("tenant-1"), eq(proof))).thenReturn(true);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1", "verificationProof", proof));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("rejects dispatch when verification is required but proof is absent")
        void rejectsDispatchWhenVerificationIsRequiredButProofIsAbsent() {
            AgentVerificationGate.VerificationChecker checker = mock(AgentVerificationGate.VerificationChecker.class);
            when(checker.isVerificationRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentVerificationGate gate = new AgentVerificationGate(checker);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1"));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("Verification is required");
            assertThat(result.reason()).contains("verification proof is absent");
        }

        @Test
        @DisplayName("rejects dispatch when verification is required but proof is invalid")
        void rejectsDispatchWhenVerificationIsRequiredButProofIsInvalid() {
            AgentVerificationGate.VerificationChecker checker = mock(AgentVerificationGate.VerificationChecker.class);
            when(checker.isVerificationRequired("agent-1", "tenant-1")).thenReturn(true);

            AgentVerificationGate gate = new AgentVerificationGate(checker);
            VerificationProof proof = mock(VerificationProof.class);
            when(proof.proofId()).thenReturn("verification-1");
            when(checker.isVerificationValid(eq("agent-1"), eq("tenant-1"), eq(proof))).thenReturn(false);
            AgentDispatchGate.DispatchContext context = new AgentDispatchGate.DispatchContext(
                "agent-1", "1.0.0", null, Map.of("tenantId", "tenant-1", "verificationProof", proof));

            AgentDispatchGate.GateResult result = gate.evaluate(context);

            assertThat(result.passed()).isFalse();
            assertThat(result.reason()).contains("verification proof");
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
                .isThrownBy(() -> new AgentVerificationGate(null))
                .withMessageContaining("verificationChecker must not be null");
        }

        @Test
        @DisplayName("requires non-null context")
        void requiresNonNullContext() {
            AgentVerificationGate.VerificationChecker checker = mock(AgentVerificationGate.VerificationChecker.class);
            AgentVerificationGate gate = new AgentVerificationGate(checker);

            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> gate.evaluate(null))
                .withMessageContaining("context must not be null");
        }
    }
}
