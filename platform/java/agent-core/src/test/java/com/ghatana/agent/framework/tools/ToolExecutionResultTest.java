/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("ToolExecutionResult")
class ToolExecutionResultTest {

    // ─── ToolExecutionStatus ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionStatus")
    class StatusTests {

        @Test
        @DisplayName("only SUCCESS is successful")
        void onlySuccessIsSuccessful() { 
            assertThat(ToolExecutionStatus.SUCCESS.isSuccessful()).isTrue(); 
            assertThat(ToolExecutionStatus.FAILED.isSuccessful()).isFalse(); 
            assertThat(ToolExecutionStatus.DENIED.isSuccessful()).isFalse(); 
            assertThat(ToolExecutionStatus.APPROVAL_PENDING.isSuccessful()).isFalse(); 
            assertThat(ToolExecutionStatus.TIMEOUT.isSuccessful()).isFalse(); 
        }

        @Test
        @DisplayName("DENIED and APPROVAL_PENDING are blocked statuses")
        void blockedStatuses() { 
            assertThat(ToolExecutionStatus.DENIED.isBlocked()).isTrue(); 
            assertThat(ToolExecutionStatus.APPROVAL_PENDING.isBlocked()).isTrue(); 
            assertThat(ToolExecutionStatus.SUCCESS.isBlocked()).isFalse(); 
            assertThat(ToolExecutionStatus.FAILED.isBlocked()).isFalse(); 
            assertThat(ToolExecutionStatus.TIMEOUT.isBlocked()).isFalse(); 
        }

        @Test
        @DisplayName("5 canonical status values defined")
        void fiveValues() { 
            assertThat(ToolExecutionStatus.values()).hasSize(5); 
        }
    }

    // ─── ToolExecutionEnvelope ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionEnvelope")
    class EnvelopeTests {

        @Test
        @DisplayName("envelope created with all required fields")
        void envelopeCreation() { 
            Instant now = Instant.now(); 
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( 
                    "inv-001", "tool-a", "1.0.0",
                    "agent-x", "release-1", "tenant-1",
                    ActionClass.READ, "1.0",
                    Map.of("query", "foo"), now); 

            assertThat(envelope.invocationId()).isEqualTo("inv-001");
            assertThat(envelope.toolId()).isEqualTo("tool-a");
            assertThat(envelope.callerAgentId()).isEqualTo("agent-x");
            assertThat(envelope.tenantId()).isEqualTo("tenant-1");
            assertThat(envelope.actionClass()).isEqualTo(ActionClass.READ); 
            assertThat(envelope.input()).containsEntry("query", "foo"); 
        }

        @Test
        @DisplayName("of() factory creates envelope with generated invocationId")
        void factoryGeneratesId() { 
            ToolExecutionEnvelope e1 = ToolExecutionEnvelope.of( 
                    "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of()); 
            ToolExecutionEnvelope e2 = ToolExecutionEnvelope.of( 
                    "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of()); 

            assertThat(e1.invocationId()).isNotBlank(); 
            assertThat(e2.invocationId()).isNotBlank(); 
            assertThat(e1.invocationId()).isNotEqualTo(e2.invocationId()); 
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenantIdThrows() { 
            assertThatThrownBy(() -> new ToolExecutionEnvelope( 
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, null,
                    ActionClass.READ, "1.0", Map.of(), Instant.now())) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("blank callerAgentId throws IllegalArgumentException")
        void blankAgentIdThrows() { 
            assertThatThrownBy(() -> new ToolExecutionEnvelope( 
                    "inv-001", "tool-a", "1.0.0", "  ", null, "tenant-1",
                    ActionClass.READ, "1.0", Map.of(), Instant.now())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("callerAgentId");
        }

        @Test
        @DisplayName("input map is unmodifiable after construction")
        void inputMapImmutable() { 
            Map<String, Object> mutable = new java.util.HashMap<>(); 
            mutable.put("key", "value"); 
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( 
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", mutable, Instant.now()); 

            assertThatThrownBy(() -> envelope.input().put("extra", "x")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("null input defaults to empty map")
        void nullInputDefaultsToEmpty() { 
            ToolExecutionEnvelope envelope = new ToolExecutionEnvelope( 
                    "inv-001", "tool-a", "1.0.0", "agent-x", null, "tenant-1",
                    ActionClass.READ, "1.0", null, Instant.now()); 
            assertThat(envelope.input()).isEmpty(); 
        }
    }

    // ─── ToolExecutionResult ──────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolExecutionResult")
    class ResultTests {

        private static final Instant NOW = Instant.parse("2026-04-10T12:00:00Z");

        @Test
        @DisplayName("succeeded factory produces SUCCESS with ALLOW policy")
        void succeededFactory() { 
            ToolExecutionResult result = ToolExecutionResult.succeeded( 
                    "inv-001", "output-value", Map.of("records", 3), 
                    "corr-abc", NOW, Duration.ofMillis(150)); 

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCESS); 
            assertThat(result.output()).isEqualTo("output-value");
            assertThat(result.policyDecision()).isEqualTo("ALLOW");
            assertThat(result.approvalDecision()).isEqualTo("N/A");
            assertThat(result.errorMessage()).isNull(); 
            assertThat(result.sideEffectSummary()).containsEntry("records", 3); 
            assertThat(result.executionDuration()).isEqualTo(Duration.ofMillis(150)); 
        }

        @Test
        @DisplayName("denied factory produces DENIED with DENY policy")
        void deniedFactory() { 
            ToolExecutionResult result = ToolExecutionResult.denied( 
                    "inv-002", "policy rule X violated", NOW);

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED); 
            assertThat(result.output()).isNull(); 
            assertThat(result.policyDecision()).isEqualTo("DENY");
            assertThat(result.errorMessage()).isEqualTo("policy rule X violated");
            assertThat(result.executionDuration()).isEqualTo(Duration.ZERO); 
        }

        @Test
        @DisplayName("failed factory produces FAILED with error message")
        void failedFactory() { 
            ToolExecutionResult result = ToolExecutionResult.failed( 
                    "inv-003", "connection timeout", "corr-xyz", NOW, Duration.ofMillis(500)); 

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED); 
            assertThat(result.errorMessage()).isEqualTo("connection timeout");
            assertThat(result.correlationId()).isEqualTo("corr-xyz");
        }

        @Test
        @DisplayName("pendingApproval factory produces APPROVAL_PENDING with CONDITIONAL policy")
        void pendingApprovalFactory() { 
            ToolExecutionResult result = ToolExecutionResult.pendingApproval("inv-004", NOW); 

            assertThat(result.status()).isEqualTo(ToolExecutionStatus.APPROVAL_PENDING); 
            assertThat(result.policyDecision()).isEqualTo("CONDITIONAL");
            assertThat(result.approvalDecision()).isEqualTo("PENDING");
            assertThat(result.output()).isNull(); 
        }

        @Test
        @DisplayName("null invocationId throws NullPointerException")
        void nullInvocationIdThrows() { 
            assertThatThrownBy(() -> new ToolExecutionResult( 
                    null, ToolExecutionStatus.SUCCESS, null, "ALLOW", "N/A",
                    Map.of(), null, null, NOW, Duration.ZERO)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("sideEffectSummary is unmodifiable after construction")
        void sideEffectSummaryImmutable() { 
            Map<String, Object> mutableEffects = new java.util.HashMap<>(); 
            mutableEffects.put("records", 5); 
            ToolExecutionResult result = new ToolExecutionResult( 
                    "inv-001", ToolExecutionStatus.SUCCESS, null,
                    "ALLOW", "N/A", mutableEffects, null, null, NOW, Duration.ZERO);

            assertThatThrownBy(() -> result.sideEffectSummary().put("extra", "x")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("null sideEffectSummary defaults to empty map")
        void nullSideEffectsDefaultsToEmpty() { 
            ToolExecutionResult result = new ToolExecutionResult( 
                    "inv-001", ToolExecutionStatus.SUCCESS, null,
                    "ALLOW", "N/A", null, null, null, NOW, Duration.ZERO);
            assertThat(result.sideEffectSummary()).isEmpty(); 
        }
    }
}
