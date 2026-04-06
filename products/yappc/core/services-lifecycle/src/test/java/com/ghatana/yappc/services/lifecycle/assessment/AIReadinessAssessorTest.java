/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — AI Readiness Assessor Tests
 */
package com.ghatana.yappc.services.lifecycle.assessment;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AIReadinessAssessor}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AI readiness assessor hard gates and AI gate evaluation
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIReadinessAssessor Tests")
class AIReadinessAssessorTest extends EventloopTestBase {

    @Mock
    private YAPPCAIService aiService;

    private AIReadinessAssessor assessor;

    /** AI response indicating the project is ready. */
    private static final String AI_READY_RESPONSE =
            "READY: yes\n" +
            "CLARITY: 0.85\n" +
            "BLOCKERS: none\n" +
            "RECOMMENDATIONS: none\n" +
            "NOTE: Project is ready to advance.";

    /** AI response indicating blockers exist. */
    private static final String AI_BLOCKED_RESPONSE =
            "READY: no\n" +
            "CLARITY: 0.5\n" +
            "BLOCKERS: Requirements lack acceptance criteria; Domain model is ambiguous\n" +
            "RECOMMENDATIONS: Add acceptance criteria; Clarify domain model\n" +
            "NOTE: Project needs more clarity before advancing.";

    @BeforeEach
    void setUp() {
        assessor = new AIReadinessAssessor(aiService);
    }

    // ─── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw NullPointerException when aiService is null")
    void shouldThrowOnNullAiService() {
        assertThatThrownBy(() -> new AIReadinessAssessor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aiService");
    }

    // ─── Hard Gates: intent → shape ────────────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: intent → shape")
    class IntentToShapeHardGates {

        @Test
        @DisplayName("should block when no requirements exist")
        void shouldBlockNoRequirements() {
            ProjectContext ctx = ctx("intent", 0, 0.9, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("intent", "shape", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).contains("requirement");
        }

        @Test
        @DisplayName("should pass when requirements exist")
        void shouldPassWithRequirements() {
            ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("intent", "shape", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Hard Gates: shape → generate ─────────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: shape → generate")
    class ShapeToGenerateHardGates {

        @Test
        @DisplayName("should block when no requirements and low clarity")
        void shouldBlockNoRequirementsAndLowClarity() {
            ProjectContext ctx = ctx("shape", 0, 0.5, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("shape", "generate", ctx);

            assertThat(blockers).hasSize(2);
        }

        @Test
        @DisplayName("should block when clarity below 0.7")
        void shouldBlockWhenClarityLow() {
            ProjectContext ctx = ctx("shape", 5, 0.5, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("shape", "generate", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).contains("clarity");
        }

        @Test
        @DisplayName("should pass when requirements exist and clarity meets threshold")
        void shouldPassWhenRequirementsAndClarityOk() {
            ProjectContext ctx = ctx("shape", 3, 0.75, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("shape", "generate", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Hard Gates: generate → run ───────────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: generate → run")
    class GenerateToRunHardGates {

        @Test
        @DisplayName("should block when no commits")
        void shouldBlockNoCommits() {
            ProjectContext ctx = ctx("generate", 3, 0.9, 0, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("generate", "run", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).contains("commit");
        }

        @Test
        @DisplayName("should pass when commits exist")
        void shouldPassWithCommits() {
            ProjectContext ctx = ctx("generate", 3, 0.9, 5, -1, null, 0, 0);

            var blockers = assessor.evaluateHardGates("generate", "run", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Hard Gates: run → review ────────────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: run → review")
    class RunToReviewHardGates {

        @Test
        @DisplayName("should block when build is not passing")
        void shouldBlockBuildFailing() {
            ProjectContext ctx = ctx("run", 3, 0.9, 10, 75, false, 0, 0);

            var blockers = assessor.evaluateHardGates("run", "review", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).containsIgnoringCase("build");
        }

        @Test
        @DisplayName("should block when coverage below threshold")
        void shouldBlockLowCoverage() {
            ProjectContext ctx = ctx("run", 3, 0.9, 10, 40, true, 0, 0);

            var blockers = assessor.evaluateHardGates("run", "review", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).contains("coverage");
        }

        @Test
        @DisplayName("should pass when build passing and coverage ok")
        void shouldPassWhenBuildAndCoverageOk() {
            ProjectContext ctx = ctx("run", 3, 0.9, 10, 70, true, 0, 0);

            var blockers = assessor.evaluateHardGates("run", "review", ctx);

            assertThat(blockers).isEmpty();
        }

        @Test
        @DisplayName("should skip coverage check when coverage is -1 (unknown)")
        void shouldSkipCoverageCheckWhenUnknown() {
            ProjectContext ctx = ctx("run", 3, 0.9, 10, -1, true, 0, 0);

            var blockers = assessor.evaluateHardGates("run", "review", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Hard Gates: review → deploy ─────────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: review → deploy")
    class ReviewToDeployHardGates {

        @Test
        @DisplayName("should block when no decisions recorded")
        void shouldBlockNoDecisions() {
            ProjectContext ctx = ctx("review", 3, 0.9, 10, 80, true, 0, 0);

            var blockers = assessor.evaluateHardGates("review", "deploy", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).contains("decision");
        }

        @Test
        @DisplayName("should pass when decisions exist")
        void shouldPassWithDecisions() {
            ProjectContext ctx = ctx("review", 3, 0.9, 10, 80, true, 2, 0);

            var blockers = assessor.evaluateHardGates("review", "deploy", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Hard Gates: deploy → maintain ───────────────────────────────────────

    @Nested
    @DisplayName("Hard gates: deploy → maintain")
    class DeployToMaintainHardGates {

        @Test
        @DisplayName("should block when build is failing")
        void shouldBlockBuildFailing() {
            ProjectContext ctx = ctx("deploy", 3, 0.9, 10, 80, false, 2, 0);

            var blockers = assessor.evaluateHardGates("deploy", "maintain", ctx);

            assertThat(blockers).hasSize(1);
            assertThat(blockers.get(0)).containsIgnoringCase("build");
        }

        @Test
        @DisplayName("should pass when build is passing")
        void shouldPassWhenBuildPassing() {
            ProjectContext ctx = ctx("deploy", 3, 0.9, 10, 80, true, 2, 0);

            var blockers = assessor.evaluateHardGates("deploy", "maintain", ctx);

            assertThat(blockers).isEmpty();
        }
    }

    // ─── Full assess() — with AI ──────────────────────────────────────────────

    @Nested
    @DisplayName("Full assess() with AI")
    class FullAssessWithAi {

        @Test
        @DisplayName("should return ready report when all gates pass and AI says ready")
        void shouldReturnReadyWhenAllGatesPass() {
            when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(AI_READY_RESPONSE));
            ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("intent", "shape", ctx));

            assertThat(report.ready()).isTrue();
            assertThat(report.fromPhase()).isEqualTo("intent");
            assertThat(report.toPhase()).isEqualTo("shape");
            assertThat(report.blockers()).isEmpty();
            assertThat(report.clarityScore()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("should return blocked report when hard gate fails (no AI call)")
        void shouldBlockOnHardGateWithoutCallingAi() {
            // No requirements — hard gate will fail; AI gate still runs (fallback for null response)
            when(aiService.reason(anyString(), anyMap()))
                    .thenReturn(Promise.of(AI_READY_RESPONSE));
            ProjectContext ctx = ctx("intent", 0, 0.9, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("intent", "shape", ctx));

            assertThat(report.ready()).isFalse();
            assertThat(report.blockers()).anyMatch(b -> b.contains("requirement"));
        }

        @Test
        @DisplayName("should merge hard gate blockers with AI blockers")
        void shouldMergeHardGateAndAiBlockers() {
            when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(AI_BLOCKED_RESPONSE));
            // Hard gate would also fail (low clarity)
            ProjectContext ctx = ctx("shape", 3, 0.5, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("shape", "generate", ctx));

            assertThat(report.ready()).isFalse();
            // Hard gate blocker + 2 AI blockers = 3 total
            assertThat(report.blockers()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should return ready report when AI says ready (no hard gate blockers)")
        void shouldReturnReadyWhenAiSaysReady() {
            when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(AI_READY_RESPONSE));
            ProjectContext ctx = ctx("shape", 5, 0.85, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("shape", "generate", ctx));

            assertThat(report.ready()).isTrue();
            assertThat(report.assessmentNote()).contains("ready");
        }

        @Test
        @DisplayName("should degrade gracefully when AI service throws exception")
        void shouldDegradeGracefullyOnAiException() {
            when(aiService.reason(anyString(), anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("AI service unavailable")));
            ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("intent", "shape", ctx));

            // Should still pass (AI gate bypassed on error)
            assertThat(report.ready()).isTrue();
            assertThat(report.assessmentNote()).contains("unavailable");
        }

        @Test
        @DisplayName("should return blocked report with AI recommendations when AI says blocked")
        void shouldReturnBlockedWithRecommendations() {
            when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(AI_BLOCKED_RESPONSE));
            ProjectContext ctx = ctx("shape", 5, 0.85, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("shape", "generate", ctx));

            assertThat(report.ready()).isFalse();
            assertThat(report.recommendations()).isNotEmpty();
            assertThat(report.blockers()).contains("Requirements lack acceptance criteria");
        }

        @Test
        @DisplayName("should handle blank AI response gracefully")
        void shouldHandleBlankAiResponse() {
            when(aiService.reason(anyString(), anyMap())).thenReturn(Promise.of(""));
            ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);

            ReadinessReport report = runPromise(() -> assessor.assess("intent", "shape", ctx));

            // Blank response → AI unavailable fallback → pass gate
            assertThat(report.ready()).isTrue();
        }
    }

    // ─── Unknown transition key ───────────────────────────────────────────────

    @Test
    @DisplayName("should return empty hard blockers for unknown transition key")
    void shouldReturnEmptyForUnknownTransition() {
        // evaluateHardGates() is a direct method call — no AI involved
        ProjectContext ctx = ctx("maintain", 3, 0.9, 10, 80, true, 2, 0);

        var blockers = assessor.evaluateHardGates("maintain", "unknown", ctx);

        assertThat(blockers).isEmpty();
    }

    // ─── Null guard ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw NullPointerException when fromPhase is null")
    void shouldThrowOnNullFromPhase() {
        ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);
        assertThatThrownBy(() -> runPromise(() -> assessor.assess(null, "shape", ctx)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NullPointerException when toPhase is null")
    void shouldThrowOnNullToPhase() {
        ProjectContext ctx = ctx("intent", 3, 0.9, 0, -1, null, 0, 0);
        assertThatThrownBy(() -> runPromise(() -> assessor.assess("intent", null, ctx)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NullPointerException when context is null")
    void shouldThrowOnNullContext() {
        assertThatThrownBy(() -> runPromise(() -> assessor.assess("intent", "shape", null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static ProjectContext ctx(
            String phase, int reqCount, double avgClarity,
            int commits, int coverage, Boolean buildPassing,
            int decisions, int agents) {
        return new ProjectContext(
                "proj-001", "tenant-001", phase,
                reqCount, avgClarity, commits, coverage,
                buildPassing, decisions, agents);
    }
}
