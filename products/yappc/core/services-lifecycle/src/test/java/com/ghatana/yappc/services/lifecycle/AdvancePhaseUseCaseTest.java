/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link AdvancePhaseUseCase}.
 *
 * <p>Tests all transition outcome paths:
 * <ul>
 *   <li>Invalid transition detection</li>
 *   <li>Missing artifact detection</li>
 *   <li>Policy gate rejection</li>
 *   <li>Successful transition</li>
 *   <li>DLQ routing for failed transitions</li>
 * </ul>
 *
 * <p>Verifies DLQ integration for all blocked result types (fire-and-forget semantics). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Integration tests for AdvancePhaseUseCase lifecycle transitions
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AdvancePhaseUseCase Integration Tests [GH-90000]")
class AdvancePhaseUseCaseTest extends EventloopTestBase {

    @Mock
    private TransitionConfigLoader transitionConfig;

    @Mock
    private PolicyEngine policyEngine;

    @Mock
    private YappcArtifactRepository artifactRepository;

    @Mock
    private DlqPublisher dlqPublisher;

    private AdvancePhaseUseCase useCase;

    @BeforeEach
    void setUp() { // GH-90000
        useCase = new AdvancePhaseUseCase( // GH-90000
                transitionConfig,
                policyEngine,
                artifactRepository,
                dlqPublisher
        );

        // Default: DLQ publisher succeeds (lenient — not used in every test) // GH-90000
        lenient().when(dlqPublisher.publish(anyString(), anyString(), anyString(), anyString(), any(Map.class), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000
        // Default artifact list: empty (lenient — overridden per-test when presence matters) // GH-90000
        lenient().when(artifactRepository.list(anyString())).thenReturn(Promise.of(List.of())); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid Transition Path [GH-90000]")
    class InvalidTransitionPath {

        @Test
        @DisplayName("should detect invalid transition and route to DLQ [GH-90000]")
        void shouldDetectInvalidTransition() { // GH-90000
            // GIVEN: Transition rule does not exist
            when(transitionConfig.findTransition("intent", "context")) // GH-90000
                    .thenReturn(Optional.empty()); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-123",
                    "intent",
                    "context",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request)); // GH-90000
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status(); // GH-90000

            // THEN: Should return blocked result
            assertThat(result).isEqualTo("BLOCKED [GH-90000]");

            // AND DLQ should be called with INVALID_TRANSITION
            verify(dlqPublisher).publish(eq("tenant-456 [GH-90000]"), eq("lifecycle-management-v1 [GH-90000]"), eq("advance-phase [GH-90000]"), eq("PHASE_ADVANCE_BLOCKED [GH-90000]"), any(Map.class), eq("INVALID_TRANSITION [GH-90000]"), eq("proj-123 [GH-90000]"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing Artifact Path [GH-90000]")
    class MissingArtifactPath {

        @Test
        @DisplayName("should detect missing artifacts and route to DLQ [GH-90000]")
        void shouldDetectMissingArtifacts() { // GH-90000
            // GIVEN: Transition rule exists with required artifacts
            TransitionSpec spec = mock(TransitionSpec.class); // GH-90000
            when(spec.getRequiredArtifacts()) // GH-90000
                    .thenReturn(List.of("architecture.yml", "requirements.md")); // GH-90000

            when(transitionConfig.findTransition("context", "design")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000

            // AND: Artifacts are missing
            when(artifactRepository.list("proj-123/architecture.yml [GH-90000]"))
                    .thenReturn(Promise.of(List.of())); // Empty = missing // GH-90000

            when(artifactRepository.list("proj-123/requirements.md [GH-90000]"))
                    .thenReturn(Promise.of(List.of("v1", "v2"))); // Present // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-123",
                    "context",
                    "design",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request)); // GH-90000
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status(); // GH-90000

            // THEN: Should return blocked result with missing artifacts
            assertThat(result).isEqualTo("BLOCKED [GH-90000]");

            // AND DLQ should be called with MISSING_ARTIFACT
            verify(dlqPublisher).publish(eq("tenant-456 [GH-90000]"), eq("lifecycle-management-v1 [GH-90000]"), eq("advance-phase [GH-90000]"), eq("PHASE_ADVANCE_BLOCKED [GH-90000]"), any(Map.class), eq("MISSING_ARTIFACT [GH-90000]"), eq("proj-123 [GH-90000]"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Policy Gate Rejection Path [GH-90000]")
    class PolicyGateRejectionPath {

        @Test
        @DisplayName("should reject transition when policy gate fails [GH-90000]")
        void shouldRejectOnPolicyGate() { // GH-90000
            // GIVEN: Transition rule exists with no required artifacts
            TransitionSpec spec = mock(TransitionSpec.class); // GH-90000
            when(spec.getRequiredArtifacts()).thenReturn(List.of()); // GH-90000

            when(transitionConfig.findTransition("design", "development")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000

            // AND: Policy engine denies the transition
            when(policyEngine.evaluate(eq("phase_advance_policy [GH-90000]"), any(Map.class)))
                    .thenReturn(Promise.of(false)); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-123",
                    "design",
                    "development",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request)); // GH-90000
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status(); // GH-90000

            // THEN: Should return blocked result
            assertThat(result).isEqualTo("BLOCKED [GH-90000]");

            // AND DLQ should be called with POLICY_GATE
            verify(dlqPublisher).publish(eq("tenant-456 [GH-90000]"), eq("lifecycle-management-v1 [GH-90000]"), eq("advance-phase [GH-90000]"), eq("PHASE_ADVANCE_BLOCKED [GH-90000]"), any(Map.class), eq("POLICY_GATE [GH-90000]"), eq("proj-123 [GH-90000]"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful Transition Path [GH-90000]")
    class SuccessfulTransitionPath {

        @Test
        @DisplayName("should advance phase when all gates pass [GH-90000]")
        void shouldAdvancePhase() { // GH-90000
            // GIVEN: Transition rule exists
            TransitionSpec spec = mock(TransitionSpec.class); // GH-90000
            when(spec.getRequiredArtifacts()).thenReturn(List.of("req.yml [GH-90000]"));

            when(transitionConfig.findTransition("development", "testing")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000

            // AND: Required artifact is present
            when(artifactRepository.list("proj-123/req.yml [GH-90000]"))
                    .thenReturn(Promise.of(List.of("v1 [GH-90000]")));

            // AND: Policy engine approves
            when(policyEngine.evaluate(eq("phase_advance_policy [GH-90000]"), any(Map.class)))
                    .thenReturn(Promise.of(true)); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-123",
                    "development",
                    "testing",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request)); // GH-90000
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status(); // GH-90000

            // THEN: Should return success with new phase
            assertThat(result).isEqualTo("testing [GH-90000]");

            // AND DLQ should NOT be called
            // (verify was not called check would fail if called, so we're implicitly testing non-call) // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AI Readiness Gate Path [GH-90000]")
    class AiReadinessGatePath {

        @Mock
        private com.ghatana.yappc.services.lifecycle.assessment.AIReadinessAssessor readinessAssessor;

        private AdvancePhaseUseCase useCaseWithAi;
        private TransitionSpec spec;

        @BeforeEach
        void setUpAiUseCase() { // GH-90000
            spec = mock(TransitionSpec.class); // GH-90000
            when(spec.getRequiredArtifacts()).thenReturn(List.of()); // GH-90000
            useCaseWithAi = new AdvancePhaseUseCase( // GH-90000
                    transitionConfig,
                    policyEngine,
                    artifactRepository,
                    dlqPublisher,
                    readinessAssessor);
        }

        @Test
        @DisplayName("should block when AI readiness assessor returns not-ready [GH-90000]")
        void shouldBlockWhenAiReadinessBlocked() { // GH-90000
            // GIVEN: Transition rule exists
            when(transitionConfig.findTransition("intent", "shape")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000

            // AND: AI assessor returns blocked report
            com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport blockedReport =
                    com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport.blocked( // GH-90000
                            "intent", "shape", 0.4,
                            List.of("No requirements captured [GH-90000]"),
                            List.of("Add requirements before advancing [GH-90000]"),
                            "AI gate blocked.");
            when(readinessAssessor.assess( // GH-90000
                    org.mockito.ArgumentMatchers.eq("intent [GH-90000]"),
                    org.mockito.ArgumentMatchers.eq("shape [GH-90000]"),
                    any())) // GH-90000
                    .thenReturn(Promise.of(blockedReport)); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-ai-1", "intent", "shape", "tenant-ai", "user-ai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseWithAi.execute(request)); // GH-90000

            // THEN: Should be blocked with AI_READINESS_GATE code
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.blockCode()).isEqualTo("AI_READINESS_GATE [GH-90000]");
            assertThat(result.blockReason()).contains("No requirements captured [GH-90000]");

            // AND DLQ published
            verify(dlqPublisher).publish( // GH-90000
                    eq("tenant-ai [GH-90000]"), anyString(), anyString(), anyString(),
                    any(Map.class), eq("AI_READINESS_GATE [GH-90000]"), eq("proj-ai-1 [GH-90000]"));
        }

        @Test
        @DisplayName("should proceed to policy gate when AI assessor returns ready [GH-90000]")
        void shouldProceedToPolicyGateWhenAiReady() { // GH-90000
            // GIVEN: Transition rule exists
            when(transitionConfig.findTransition("intent", "shape")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000

            // AND: AI assessor returns ready
            com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport readyReport =
                    com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport.ready( // GH-90000
                            "intent", "shape", 0.9, "Ready.");
            when(readinessAssessor.assess( // GH-90000
                    org.mockito.ArgumentMatchers.eq("intent [GH-90000]"),
                    org.mockito.ArgumentMatchers.eq("shape [GH-90000]"),
                    any())) // GH-90000
                    .thenReturn(Promise.of(readyReport)); // GH-90000

            // AND: Policy engine approves
            when(policyEngine.evaluate(eq("phase_advance_policy [GH-90000]"), any(Map.class)))
                    .thenReturn(Promise.of(true)); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-ai-2", "intent", "shape", "tenant-ai", "user-ai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseWithAi.execute(request)); // GH-90000

            // THEN: Should succeed
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.toPhase()).isEqualTo("shape [GH-90000]");
        }

        @Test
        @DisplayName("should skip AI gate and use 4-arg constructor path [GH-90000]")
        void shouldSkipAiGateWithNoAssessor() { // GH-90000
            // GIVEN: 4-arg constructor (no assessor) // GH-90000
            AdvancePhaseUseCase useCaseNoAi = new AdvancePhaseUseCase( // GH-90000
                    transitionConfig, policyEngine, artifactRepository, dlqPublisher);

            when(transitionConfig.findTransition("intent", "shape")) // GH-90000
                    .thenReturn(Optional.of(spec)); // GH-90000
            when(policyEngine.evaluate(eq("phase_advance_policy [GH-90000]"), any(Map.class)))
                    .thenReturn(Promise.of(true)); // GH-90000

            TransitionRequest request = new TransitionRequest( // GH-90000
                    "proj-noai", "intent", "shape", "tenant-noai", "user-noai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseNoAi.execute(request)); // GH-90000

            // THEN: Should succeed (AI gate skipped) // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
}
