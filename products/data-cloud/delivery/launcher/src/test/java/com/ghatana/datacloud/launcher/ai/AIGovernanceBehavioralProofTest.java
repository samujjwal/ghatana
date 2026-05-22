/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

import com.ghatana.platform.ai.integration.LlmGateway;
import com.ghatana.platform.ai.integration.ModelRegistry;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-3: Real executable AI governance behavioral proof tests.
 *
 * <p>Validates comprehensive AI governance with behavioral verification:
 * <ul>
 *   <li>Model availability proof</li>
 *   <li>Fallback prevention proof</li>
 *   <li>Privacy redaction before model calls</li>
 *   <li>Prompt/input/output provenance tracking</li>
 *   <li>Cost budget enforcement</li>
 *   <li>Evaluation quality thresholds</li>
 *   <li>Human approval for risky AI actions</li>
 *   <li>Audit evidence for AI-generated recommendations/actions</li>
 * </ul>
 *
 * <p>This replaces shallow posture checks with deep behavioral verification that
 * AI operations are governed, safe, and auditable.
 *
 * @doc.type class
 * @doc.purpose Real executable AI governance behavioral proof tests (P1-3)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AI Governance Behavioral Proof Tests (P1-3)")
@Tag("production")
@Tag("ai-governance")
@Tag("behavioral-proof")
@ExtendWith(MockitoExtension.class)
class AIGovernanceBehavioralProofTest extends EventloopTestBase {

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private ModelRegistry modelRegistry;

    @Mock
    private AuditService auditService;

    private AIGovernanceManager aiGovernanceManager;

    @BeforeEach
    void setUp() {
        aiGovernanceManager = new AIGovernanceManager(llmGateway, modelRegistry, auditService);
    }

    // ==================== Model Availability Proof ====================

    @Test
    @DisplayName("P1-3: Model availability is checked before AI operations")
    void modelAvailabilityIsCheckedBeforeAIOperations() {
        // Given: Model is available
        when(modelRegistry.isModelAvailable("gpt-4"))
            .thenReturn(Promise.of(true));

        // When: Execute AI operation
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithAvailabilityCheck("gpt-4", "test prompt")
        );

        // Then: Operation should succeed
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getModelAvailable()).isTrue();
    }

    @Test
    @DisplayName("P1-3: AI operations fail when model is unavailable")
    void aiOperationsFailWhenModelUnavailable() {
        // Given: Model is unavailable
        when(modelRegistry.isModelAvailable("gpt-4"))
            .thenReturn(Promise.of(false));

        // When: Execute AI operation
        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithAvailabilityCheck("gpt-4", "test prompt")
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Model unavailable")
         .hasMessageContaining("gpt-4");
    }

    // ==================== Fallback Prevention Proof ====================

    @Test
    @DisplayName("P1-3: Fallback to weaker models is prevented for critical operations")
    void fallbackToWeakerModelsIsPreventedForCriticalOperations() {
        // Given: Primary model is unavailable
        when(modelRegistry.isModelAvailable("gpt-4"))
            .thenReturn(Promise.of(false));

        // And: Fallback model is available
        when(modelRegistry.isModelAvailable("gpt-3.5"))
            .thenReturn(Promise.of(true));

        // When: Execute critical AI operation
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "test prompt",
            AIOperationContext.RiskLevel.CRITICAL
        );

        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithFallbackPrevention(context)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Fallback prevented")
         .hasMessageContaining("critical operation");
    }

    @Test
    @DisplayName("P1-3: Fallback is allowed for non-critical operations")
    void fallbackIsAllowedForNonCriticalOperations() {
        // Given: Primary model is unavailable
        when(modelRegistry.isModelAvailable("gpt-4"))
            .thenReturn(Promise.of(false));

        // And: Fallback model is available
        when(modelRegistry.isModelAvailable("gpt-3.5"))
            .thenReturn(Promise.of(true));

        // And: Fallback model returns response
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("fallback response"));

        // When: Execute non-critical AI operation
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "test prompt",
            AIOperationContext.RiskLevel.LOW
        );

        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithFallbackPrevention(context)
        );

        // Then: Should use fallback model
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS_WITH_FALLBACK);
        assertThat(result.getFallbackUsed()).isTrue();
        assertThat(result.getModelUsed()).isEqualTo("gpt-3.5");
    }

    // ==================== Privacy Redaction Before Model Calls ====================

    @Test
    @DisplayName("P1-3: PII is redacted before sending to model")
    void piiIsRedactedBeforeSendingToModel() {
        // Given: Prompt contains PII
        String promptWithPII = "User email is john@example.com and phone is 555-1234";

        // When: Execute AI operation with privacy redaction
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithPrivacyRedaction("gpt-4", promptWithPII)
        );

        // Then: PII should be redacted in the sent prompt
        assertThat(result.getRedactedPrompt()).doesNotContain("john@example.com");
        assertThat(result.getRedactedPrompt()).doesNotContain("555-1234");
        assertThat(result.getRedactedPrompt()).contains("[REDACTED_EMAIL]");
        assertThat(result.getRedactedPrompt()).contains("[REDACTED_PHONE]");
    }

    @Test
    @DisplayName("P1-3: Privacy redaction fails if sensitive data cannot be removed")
    void privacyRedactionFailsIfSensitiveDataCannotBeRemoved() {
        // Given: Prompt contains unredactable sensitive data
        String promptWithSensitiveData = "SSN is 123-45-6789";

        // When: Execute AI operation with strict privacy mode
        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithStrictPrivacyRedaction("gpt-4", promptWithSensitiveData)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Unable to redact sensitive data")
         .hasMessageContaining("operation blocked");
    }

    // ==================== Prompt/Input/Output Provenance Tracking ====================

    @Test
    @DisplayName("P1-3: Prompt, input, and output are tracked for provenance")
    void promptInputOutputAreTrackedForProvenance() {
        // Given: AI operation context
        String prompt = "test prompt";
        String model = "gpt-4";

        // When: Execute AI operation with provenance tracking
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("AI response"));

        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithProvenanceTracking(model, prompt)
        );

        // Then: Provenance should be tracked
        assertThat(result.getProvenanceId()).isNotNull();
        assertThat(result.getProvenance().getPrompt()).isEqualTo(prompt);
        assertThat(result.getProvenance().getModel()).isEqualTo(model);
        assertThat(result.getProvenance().getOutput()).isEqualTo("AI response");
        assertThat(result.getProvenance().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("P1-3: Provenance tracking includes cost and token usage")
    void provenanceTrackingIncludesCostAndTokenUsage() {
        // Given: AI operation
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("AI response"));

        // When: Execute with provenance tracking
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithProvenanceTracking("gpt-4", "test prompt")
        );

        // Then: Provenance should include cost and token usage
        assertThat(result.getProvenance().getTokensUsed()).isGreaterThan(0);
        assertThat(result.getProvenance().getCost()).isGreaterThan(0.0);
    }

    // ==================== Cost Budget Enforcement ====================

    @Test
    @DisplayName("P1-3: AI operations are blocked when cost budget is exceeded")
    void aiOperationsAreBlockedWhenCostBudgetExceeded() {
        // Given: Cost budget is exceeded
        when(modelRegistry.getCostBudget("tenant-123"))
            .thenReturn(100.0);
        when(modelRegistry.getCurrentCost("tenant-123"))
            .thenReturn(150.0);

        // When: Execute AI operation
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "test prompt",
            "tenant-123"
        );

        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithCostBudget(context)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Cost budget exceeded")
         .hasMessageContaining("operation blocked");
    }

    @Test
    @DisplayName("P1-3: AI operations succeed when within cost budget")
    void aiOperationsSucceedWhenWithinCostBudget() {
        // Given: Cost budget is not exceeded
        when(modelRegistry.getCostBudget("tenant-123"))
            .thenReturn(100.0);
        when(modelRegistry.getCurrentCost("tenant-123"))
            .thenReturn(50.0);
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute AI operation
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "test prompt",
            "tenant-123"
        );

        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithCostBudget(context)
        );

        // Then: Should succeed
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getCostEnforced()).isTrue();
    }

    // ==================== Evaluation Quality Thresholds ====================

    @Test
    @DisplayName("P1-3: AI operations fail when model quality is below threshold")
    void aiOperationsFailWhenModelQualityBelowThreshold() {
        // Given: Model quality is below threshold
        when(modelRegistry.getModelQuality("gpt-4"))
            .thenReturn(0.75); // Below 0.8 threshold

        // When: Execute AI operation with quality threshold
        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithQualityThreshold("gpt-4", "test prompt", 0.8)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Model quality below threshold")
         .hasMessageContaining("0.75")
         .hasMessageContaining("0.8");
    }

    @Test
    @DisplayName("P1-3: AI operations succeed when model quality meets threshold")
    void aiOperationsSucceedWhenModelQualityMeetsThreshold() {
        // Given: Model quality meets threshold
        when(modelRegistry.getModelQuality("gpt-4"))
            .thenReturn(0.85); // Above 0.8 threshold
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute AI operation with quality threshold
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithQualityThreshold("gpt-4", "test prompt", 0.8)
        );

        // Then: Should succeed
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getQualityScore()).isEqualTo(0.85);
    }

    // ==================== Human Approval for Risky AI Actions ====================

    @Test
    @DisplayName("P1-3: Risky AI actions require human approval")
    void riskyAIActionsRequireHumanApproval() {
        // Given: Risky AI action
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "delete all user data",
            AIOperationContext.RiskLevel.CRITICAL
        );

        // When: Execute without approval
        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithHumanApproval(context)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Human approval required")
         .hasMessageContaining("risky action");
    }

    @Test
    @DisplayName("P1-3: Risky AI actions succeed with human approval")
    void riskyAIActionsSucceedWithHumanApproval() {
        // Given: Risky AI action with approval
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "delete all user data",
            AIOperationContext.RiskLevel.CRITICAL
        );
        context.setHumanApprovalId("approval-123");
        context.setApprovedBy("admin-user");

        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute with approval
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithHumanApproval(context)
        );

        // Then: Should succeed
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getApprovalRequired()).isTrue();
        assertThat(result.getApprovalId()).isEqualTo("approval-123");
    }

    // ==================== Audit Evidence for AI-Generated Recommendations/Actions ====================

    @Test
    @DisplayName("P1-3: AI-generated recommendations are audited")
    void aiGeneratedRecommendationsAreAudited() {
        // Given: AI operation generates recommendation
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("recommendation: upgrade to premium"));

        // When: Execute with audit
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithAudit("gpt-4", "generate recommendation")
        );

        // Then: Audit event should be created
        verify(auditService, times(1)).record(any(AuditEvent.class));
        assertThat(result.getAudited()).isTrue();
        assertThat(result.getAuditId()).isNotNull();
    }

    @Test
    @DisplayName("P1-3: AI-generated actions are audited with full context")
    void aiGeneratedActionsAreAuditedWithFullContext() {
        // Given: AI operation generates action
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("action: delete user"));

        // When: Execute with audit
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithAudit("gpt-4", "generate action")
        );

        // Then: Audit should include full context
        verify(auditService).record(argThat(event -> 
            event.getEventType().equals("AI_ACTION") &&
            event.getDetail("model").equals("gpt-4") &&
            event.getDetail("action").equals("delete user") &&
            event.getDetail("provenanceId") != null
        ));
    }

    // ==================== AI Safety Guardrails ====================

    @Test
    @DisplayName("P1-3: AI operations are blocked for harmful prompts")
    void aiOperationsAreBlockedForHarmfulPrompts() {
        // Given: Harmful prompt
        String harmfulPrompt = "how to create a virus";

        // When: Execute with safety guardrails
        assertThatThrownBy(() -> runPromise(() -> 
            aiGovernanceManager.executeWithSafetyGuardrails("gpt-4", harmfulPrompt)
        )).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("P1-3")
         .hasMessageContaining("Harmful content detected")
         .hasMessageContaining("operation blocked");
    }

    @Test
    @DisplayName("P1-3: AI operations succeed for safe prompts")
    void aiOperationsSucceedForSafePrompts() {
        // Given: Safe prompt
        String safePrompt = "how to write a hello world program";

        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute with safety guardrails
        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithSafetyGuardrails("gpt-4", safePrompt)
        );

        // Then: Should succeed
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getSafetyCheckPassed()).isTrue();
    }

    // ==================== Success Path ====================

    @Test
    @DisplayName("P1-3: AI operations succeed when all governance checks pass")
    void aiOperationsSucceedWhenAllGovernanceChecksPass() {
        // Given: All governance checks pass
        when(modelRegistry.isModelAvailable("gpt-4"))
            .thenReturn(Promise.of(true));
        when(modelRegistry.getModelQuality("gpt-4"))
            .thenReturn(0.9);
        when(modelRegistry.getCostBudget("tenant-123"))
            .thenReturn(100.0);
        when(modelRegistry.getCurrentCost("tenant-123"))
            .thenReturn(10.0);
        when(llmGateway.complete(any(), any()))
            .thenReturn(Promise.of("response"));

        // When: Execute with full governance
        AIOperationContext context = new AIOperationContext(
            "gpt-4",
            "safe prompt",
            AIOperationContext.RiskLevel.LOW,
            "tenant-123"
        );

        AIOperationResult result = runPromise(() -> 
            aiGovernanceManager.executeWithFullGovernance(context)
        );

        // Then: Should succeed with all checks verified
        assertThat(result.getStatus()).isEqualTo(AIOperationResult.Status.SUCCESS);
        assertThat(result.getModelAvailable()).isTrue();
        assertThat(result.getQualityScore()).isEqualTo(0.9);
        assertThat(result.getCostEnforced()).isTrue();
        assertThat(result.getSafetyCheckPassed()).isTrue();
        assertThat(result.getAudited()).isTrue();
        assertThat(result.getProvenanceId()).isNotNull();
    }
}
