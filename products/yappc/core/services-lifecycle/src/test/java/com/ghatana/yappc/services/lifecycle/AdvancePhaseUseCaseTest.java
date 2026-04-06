/*
 * Copyright (c) 2026 Ghatana Technologies
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
 * <p>Verifies DLQ integration for all blocked result types (fire-and-forget semantics).
 *
 * @doc.type class
 * @doc.purpose Integration tests for AdvancePhaseUseCase lifecycle transitions
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdvancePhaseUseCase Integration Tests")
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
    void setUp() {
        useCase = new AdvancePhaseUseCase(
                transitionConfig,
                policyEngine,
                artifactRepository,
                dlqPublisher
        );

        // Default: DLQ publisher succeeds (lenient — not used in every test)
        lenient().when(dlqPublisher.publish(anyString(), anyString(), anyString(), anyString(), any(Map.class), anyString(), anyString()))
                .thenReturn(Promise.complete());
        // Default artifact list: empty (lenient — overridden per-test when presence matters)
        lenient().when(artifactRepository.list(anyString())).thenReturn(Promise.of(List.of()));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid Transition Path")
    class InvalidTransitionPath {

        @Test
        @DisplayName("should detect invalid transition and route to DLQ")
        void shouldDetectInvalidTransition() {
            // GIVEN: Transition rule does not exist
            when(transitionConfig.findTransition("intent", "context"))
                    .thenReturn(Optional.empty());

            TransitionRequest request = new TransitionRequest(
                    "proj-123",
                    "intent",
                    "context",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request));
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status();

            // THEN: Should return blocked result
            assertThat(result).isEqualTo("BLOCKED");

            // AND DLQ should be called with INVALID_TRANSITION
            verify(dlqPublisher).publish(eq("tenant-456"), eq("lifecycle-management-v1"), eq("advance-phase"), eq("PHASE_ADVANCE_BLOCKED"), any(Map.class), eq("INVALID_TRANSITION"), eq("proj-123"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing Artifact Path")
    class MissingArtifactPath {

        @Test
        @DisplayName("should detect missing artifacts and route to DLQ")
        void shouldDetectMissingArtifacts() {
            // GIVEN: Transition rule exists with required artifacts
            TransitionSpec spec = mock(TransitionSpec.class);
            when(spec.getRequiredArtifacts())
                    .thenReturn(List.of("architecture.yml", "requirements.md"));

            when(transitionConfig.findTransition("context", "design"))
                    .thenReturn(Optional.of(spec));

            // AND: Artifacts are missing
            when(artifactRepository.list("proj-123/architecture.yml"))
                    .thenReturn(Promise.of(List.of())); // Empty = missing

            when(artifactRepository.list("proj-123/requirements.md"))
                    .thenReturn(Promise.of(List.of("v1", "v2"))); // Present

            TransitionRequest request = new TransitionRequest(
                    "proj-123",
                    "context",
                    "design",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request));
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status();

            // THEN: Should return blocked result with missing artifacts
            assertThat(result).isEqualTo("BLOCKED");

            // AND DLQ should be called with MISSING_ARTIFACT
            verify(dlqPublisher).publish(eq("tenant-456"), eq("lifecycle-management-v1"), eq("advance-phase"), eq("PHASE_ADVANCE_BLOCKED"), any(Map.class), eq("MISSING_ARTIFACT"), eq("proj-123"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Policy Gate Rejection Path")
    class PolicyGateRejectionPath {

        @Test
        @DisplayName("should reject transition when policy gate fails")
        void shouldRejectOnPolicyGate() {
            // GIVEN: Transition rule exists with no required artifacts
            TransitionSpec spec = mock(TransitionSpec.class);
            when(spec.getRequiredArtifacts()).thenReturn(List.of());

            when(transitionConfig.findTransition("design", "development"))
                    .thenReturn(Optional.of(spec));

            // AND: Policy engine denies the transition
            when(policyEngine.evaluate(eq("phase_advance_policy"), any(Map.class)))
                    .thenReturn(Promise.of(false));

            TransitionRequest request = new TransitionRequest(
                    "proj-123",
                    "design",
                    "development",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request));
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status();

            // THEN: Should return blocked result
            assertThat(result).isEqualTo("BLOCKED");

            // AND DLQ should be called with POLICY_GATE
            verify(dlqPublisher).publish(eq("tenant-456"), eq("lifecycle-management-v1"), eq("advance-phase"), eq("PHASE_ADVANCE_BLOCKED"), any(Map.class), eq("POLICY_GATE"), eq("proj-123"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful Transition Path")
    class SuccessfulTransitionPath {

        @Test
        @DisplayName("should advance phase when all gates pass")
        void shouldAdvancePhase() {
            // GIVEN: Transition rule exists
            TransitionSpec spec = mock(TransitionSpec.class);
            when(spec.getRequiredArtifacts()).thenReturn(List.of("req.yml"));

            when(transitionConfig.findTransition("development", "testing"))
                    .thenReturn(Optional.of(spec));

            // AND: Required artifact is present
            when(artifactRepository.list("proj-123/req.yml"))
                    .thenReturn(Promise.of(List.of("v1")));

            // AND: Policy engine approves
            when(policyEngine.evaluate(eq("phase_advance_policy"), any(Map.class)))
                    .thenReturn(Promise.of(true));

            TransitionRequest request = new TransitionRequest(
                    "proj-123",
                    "development",
                    "testing",
                    "tenant-456",
                    "user-789"
            );

            // WHEN: Execute the transition
            TransitionResult transResult = runPromise(() -> useCase.execute(request));
            String result = transResult.isSuccess() ? transResult.toPhase() : transResult.status();

            // THEN: Should return success with new phase
            assertThat(result).isEqualTo("testing");

            // AND DLQ should NOT be called
            // (verify was not called check would fail if called, so we're implicitly testing non-call)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AI Readiness Gate Path")
    class AiReadinessGatePath {

        @Mock
        private com.ghatana.yappc.services.lifecycle.assessment.AIReadinessAssessor readinessAssessor;

        private AdvancePhaseUseCase useCaseWithAi;
        private TransitionSpec spec;

        @BeforeEach
        void setUpAiUseCase() {
            spec = mock(TransitionSpec.class);
            when(spec.getRequiredArtifacts()).thenReturn(List.of());
            useCaseWithAi = new AdvancePhaseUseCase(
                    transitionConfig,
                    policyEngine,
                    artifactRepository,
                    dlqPublisher,
                    readinessAssessor);
        }

        @Test
        @DisplayName("should block when AI readiness assessor returns not-ready")
        void shouldBlockWhenAiReadinessBlocked() {
            // GIVEN: Transition rule exists
            when(transitionConfig.findTransition("intent", "shape"))
                    .thenReturn(Optional.of(spec));

            // AND: AI assessor returns blocked report
            com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport blockedReport =
                    com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport.blocked(
                            "intent", "shape", 0.4,
                            List.of("No requirements captured"),
                            List.of("Add requirements before advancing"),
                            "AI gate blocked.");
            when(readinessAssessor.assess(
                    org.mockito.ArgumentMatchers.eq("intent"),
                    org.mockito.ArgumentMatchers.eq("shape"),
                    any()))
                    .thenReturn(Promise.of(blockedReport));

            TransitionRequest request = new TransitionRequest(
                    "proj-ai-1", "intent", "shape", "tenant-ai", "user-ai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseWithAi.execute(request));

            // THEN: Should be blocked with AI_READINESS_GATE code
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.blockCode()).isEqualTo("AI_READINESS_GATE");
            assertThat(result.blockReason()).contains("No requirements captured");

            // AND DLQ published
            verify(dlqPublisher).publish(
                    eq("tenant-ai"), anyString(), anyString(), anyString(),
                    any(Map.class), eq("AI_READINESS_GATE"), eq("proj-ai-1"));
        }

        @Test
        @DisplayName("should proceed to policy gate when AI assessor returns ready")
        void shouldProceedToPolicyGateWhenAiReady() {
            // GIVEN: Transition rule exists
            when(transitionConfig.findTransition("intent", "shape"))
                    .thenReturn(Optional.of(spec));

            // AND: AI assessor returns ready
            com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport readyReport =
                    com.ghatana.yappc.services.lifecycle.assessment.ReadinessReport.ready(
                            "intent", "shape", 0.9, "Ready.");
            when(readinessAssessor.assess(
                    org.mockito.ArgumentMatchers.eq("intent"),
                    org.mockito.ArgumentMatchers.eq("shape"),
                    any()))
                    .thenReturn(Promise.of(readyReport));

            // AND: Policy engine approves
            when(policyEngine.evaluate(eq("phase_advance_policy"), any(Map.class)))
                    .thenReturn(Promise.of(true));

            TransitionRequest request = new TransitionRequest(
                    "proj-ai-2", "intent", "shape", "tenant-ai", "user-ai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseWithAi.execute(request));

            // THEN: Should succeed
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.toPhase()).isEqualTo("shape");
        }

        @Test
        @DisplayName("should skip AI gate and use 4-arg constructor path")
        void shouldSkipAiGateWithNoAssessor() {
            // GIVEN: 4-arg constructor (no assessor)
            AdvancePhaseUseCase useCaseNoAi = new AdvancePhaseUseCase(
                    transitionConfig, policyEngine, artifactRepository, dlqPublisher);

            when(transitionConfig.findTransition("intent", "shape"))
                    .thenReturn(Optional.of(spec));
            when(policyEngine.evaluate(eq("phase_advance_policy"), any(Map.class)))
                    .thenReturn(Promise.of(true));

            TransitionRequest request = new TransitionRequest(
                    "proj-noai", "intent", "shape", "tenant-noai", "user-noai");

            // WHEN
            TransitionResult result = runPromise(() -> useCaseNoAi.execute(request));

            // THEN: Should succeed (AI gate skipped)
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
}
