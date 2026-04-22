/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import com.ghatana.agent.framework.governance.ActionClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolExecutionEnvelope}, {@link ToolExecutionResult}, and {@link ToolExecutionStatus}.
 */
@DisplayName("ToolExecutionResult [GH-90000]")
class ToolExecutionResultTest {

    // ─── ToolExecutionStatus ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionStatus [GH-90000]")
    class StatusTests {

        @Test
        @DisplayName("only SUCCESS is successful [GH-90000]")
        void onlySuccessIsSuccessful() { // GH-90000
            assertThat(ToolExecutionStatus.SUCCESS.isSuccessful()).isTrue(); // GH-90000
            assertThat(ToolExecutionStatus.FAILED.isSuccessful()).isFalse(); // GH-90000
            assertThat(ToolExecutionStatus.DENIED.isSuccessful()).isFalse(); // GH-90000
            assertThat(ToolExecutionStatus.APPROVAL_PENDING.isSuccessful()).isFalse(); // GH-90000
            assertThat(ToolExecutionStatus.TIMEOUT.isSuccessful()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DENIED and APPROVAL_PENDING are blocked statuses [GH-90000]")
        void blockedStatuses() { // GH-90000
            assertThat(ToolExecutionStatus.DENIED.isBlocked()).isTrue(); // GH-90000
            assertThat(ToolExecutionStatus.APPROVAL_PENDING.isBlocked()).isTrue(); // GH-90000
            assertThat(ToolExecutionStatus.SUCCESS.isBlocked()).isFalse(); // GH-90000
            assertThat(ToolExecutionStatus.FAILED.isBlocked()).isFalse(); // GH-90000
            assertThat(ToolExecutionStatus.TIMEOUT.isBlocked()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("5 canonical status values defined [GH-90000]")
        void fiveValues() { // GH-90000
            assertThat(ToolExecutionStatus.values()).hasSize(5); // GH-90000
        }
    }

    // ─── ToolExecutionEnvelope ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionEnvelope [GH-90000]")
    class EnvelopeTests {

        @Test
        @DisplayName("envelope created with all required fields [GH-90000]")
        void envelopeCreation() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( // GH-90000
                    "inv-001", "tool-a", "1.0.0",
                    "agent-x", "release-1", "tenant-1",
                    ActionClass.READ, "1.0",
                    Map.of("query", "foo"), now); // GH-90000

            assertThat(envelope.invocationId()).isEqualTo("inv-001 [GH-90000]");
            assertThat(envelope.toolId()).isEqualTo("tool-a [GH-90000]");
            assertThat(envelope.callerAgentId()).isEqualTo("agent-x [GH-90000]");
            assertThat(envelope.tenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(envelope.actionClass()).isEqualTo(ActionClass.READ); // GH-90000
            assertThat(envelope.input()).containsEntry("query", "foo"); // GH-90000
        }

        @Test
        @DisplayName("of() factory creates envelope with generated invocationId [GH-90000]")
        void factoryGeneratesId() { // GH-90000
            ToolExecutionEnvelope e1 = ToolExecutionEnvelope.of( // GH-90000
                    "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of()); // GH-90000
            ToolExecutionEnvelope e2 = ToolExecutionEnvelope.of( // GH-90000
                    "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of()); // GH-90000

            assertThat(e1.invocationId()).isNotBlank(); // GH-90000
            assertThat(e2.invocationId()).isNotBlank(); // GH-90000
            assertThat(e1.invocationId()).isNotEqualTo(e2.invocationId()); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void nullTenantIdThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolExecutionEnvelope( // GH-90000
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, null,
                    ActionClass.READ, "1.0", Map.of(), Instant.now())) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("blank callerAgentId throws IllegalArgumentException [GH-90000]")
        void blankAgentIdThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolExecutionEnvelope( // GH-90000
                    "inv-001", "tool-a", "1.0.0", "  ", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of(), Instant.now())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("callerAgentId [GH-90000]");
        }

        @Test
        @DisplayName("input map is unmodifiable after construction [GH-90000]")
        void inputMapImmutable() { // GH-90000
            Map<String, Object> mutable = new java.util.HashMap<>(); // GH-90000
            mutable.put("key", "value"); // GH-90000
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( // GH-90000
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", mutable, Instant.now()); // GH-90000

            assertThatThrownBy(() -> envelope.input().put("extra", "x")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null input defaults to empty map [GH-90000]")
        void nullInputDefaultsToEmpty() { // GH-90000
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( // GH-90000
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", null, Instant.now()); // GH-90000
            assertThat(envelope.input()).isEmpty(); // GH-90000
        }
    }

    // ─── ToolExecutionResult ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionResult [GH-90000]")
    class ResultTests {

        private static final Instant NOW = Instant.parse("2026-04-10T12:00:00Z [GH-90000]");

        @Test
        @DisplayName("succeeded factory produces SUCCESS with ALLOW policy [GH-90000]")
        void succeededFactory() { // GH-90000
            ToolExecutionResult result = ToolExecutionResult.succeeded( // GH-90000
                    "inv-001", "output-value", Map.of("records", 3), // GH-90000
                    "corr-abc", NOW, Duration.ofMillis(150)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); // GH-90000
            assertThat(result.output()).isEqualTo("output-value [GH-90000]");
            assertThat(result.policyDecision()).isEqualTo("ALLOW [GH-90000]");
            assertThat(result.approvalDecision()).isEqualTo("N/A [GH-90000]");
            assertThat(result.errorMessage()).isNull(); // GH-90000
            assertThat(result.sideEffectSummary()).containsEntry("records", 3); // GH-90000
            assertThat(result.executionDuration()).isEqualTo(Duration.ofMillis(150)); // GH-90000
        }

        @Test
        @DisplayName("denied factory produces DENIED with DENY policy [GH-90000]")
        void deniedFactory() { // GH-90000
            ToolExecutionResult result = ToolExecutionResult.denied( // GH-90000
                    "inv-002", "policy rule X violated", NOW);

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); // GH-90000
            assertThat(result.output()).isNull(); // GH-90000
            assertThat(result.policyDecision()).isEqualTo("DENY [GH-90000]");
            assertThat(result.errorMessage()).isEqualTo("policy rule X violated [GH-90000]");
            assertThat(result.executionDuration()).isEqualTo(Duration.ZERO); // GH-90000
        }

        @Test
        @DisplayName("failed factory produces FAILED with error message [GH-90000]")
        void failedFactory() { // GH-90000
            ToolExecutionResult result = ToolExecutionResult.failed( // GH-90000
                    "inv-003", "connection timeout", "corr-xyz", NOW, Duration.ofMillis(500)); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); // GH-90000
            assertThat(result.errorMessage()).isEqualTo("connection timeout [GH-90000]");
            assertThat(result.correlationId()).isEqualTo("corr-xyz [GH-90000]");
        }

        @Test
        @DisplayName("pendingApproval factory produces APPROVAL_PENDING with CONDITIONAL policy [GH-90000]")
        void pendingApprovalFactory() { // GH-90000
            ToolExecutionResult result = ToolExecutionResult.pendingApproval("inv-004", NOW); // GH-90000

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.APPROVAL_PENDING); // GH-90000
            assertThat(result.policyDecision()).isEqualTo("CONDITIONAL [GH-90000]");
            assertThat(result.approvalDecision()).isEqualTo("PENDING [GH-90000]");
            assertThat(result.output()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("null invocationId throws NullPointerException [GH-90000]")
        void nullInvocationIdThrows() { // GH-90000
            assertThatThrownBy(() -> new ToolExecutionResult( // GH-90000
                    null, ToolExecutionStatus.SUCCESS, null, "ALLOW", "N/A",
                    Map.of(), null, null, NOW, Duration.ZERO)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("sideEffectSummary is unmodifiable after construction [GH-90000]")
        void sideEffectSummaryImmutable() { // GH-90000
            Map<String, Object> mutableEffects = new java.util.HashMap<>(); // GH-90000
            mutableEffects.put("records", 5); // GH-90000
            ToolExecutionResult result = new ToolExecutionResult( // GH-90000
                    "inv-001", ToolExecutionStatus.SUCCESS, null,
                    "ALLOW", "N/A", mutableEffects, null, null, NOW, Duration.ZERO);

            assertThatThrownBy(() -> result.sideEffectSummary().put("extra", "x")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null sideEffectSummary defaults to empty map [GH-90000]")
        void nullSideEffectsDefaultsToEmpty() { // GH-90000
            ToolExecutionResult result = new ToolExecutionResult( // GH-90000
                    "inv-001", ToolExecutionStatus.SUCCESS, null,
                    "ALLOW", "N/A", null, null, null, NOW, Duration.ZERO);
            assertThat(result.sideEffectSummary()).isEmpty(); // GH-90000
        }
    }
}
