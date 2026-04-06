/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — ProjectContextBuilder Tests
 */
package com.ghatana.yappc.services.lifecycle.assessment;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectContextBuilder}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for project context aggregation with adapter composition
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectContextBuilder Tests")
class ProjectContextBuilderTest extends EventloopTestBase {

    @Mock
    private ProjectContextBuilder.RequirementsAdapter requirementsAdapter;

    @Mock
    private ProjectContextBuilder.CodeRepoAdapter codeRepoAdapter;

    @Mock
    private ProjectContextBuilder.KgAdapter kgAdapter;

    @Mock
    private ProjectContextBuilder.AgentAdapter agentAdapter;

    // ─── No adapters (all defaults) ───────────────────────────────────────────

    @Test
    @DisplayName("should build context with all defaults when no adapters configured")
    void shouldBuildContextWithDefaults() {
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build();

        ProjectContext ctx = runPromise(() ->
                builder.buildContext("proj-1", "tenant-1", "intent"));

        assertThat(ctx.projectId()).isEqualTo("proj-1");
        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.currentPhase()).isEqualTo("intent");
        assertThat(ctx.requirementCount()).isZero();
        assertThat(ctx.averageClarityScore()).isEqualTo(0.0);
        assertThat(ctx.codeCommitCount()).isZero();
        assertThat(ctx.testCoveragePercent()).isEqualTo(-1);
        assertThat(ctx.buildPassing()).isNull();
        assertThat(ctx.decisionCount()).isZero();
        assertThat(ctx.activeAgentCount()).isZero();
    }

    // ─── All adapters ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should aggregate values from all adapters")
    void shouldAggregateAllAdapters() {
        when(requirementsAdapter.requirementCount(anyString(), anyString())).thenReturn(Promise.of(5));
        when(requirementsAdapter.averageClarityScore(anyString(), anyString())).thenReturn(Promise.of(0.85));
        when(codeRepoAdapter.commitCount(anyString(), anyString())).thenReturn(Promise.of(42));
        when(codeRepoAdapter.testCoveragePercent(anyString(), anyString())).thenReturn(Promise.of(78));
        when(codeRepoAdapter.buildPassing(anyString(), anyString())).thenReturn(Promise.of(true));
        when(kgAdapter.decisionCount(anyString(), anyString())).thenReturn(Promise.of(3));
        when(agentAdapter.activeAgentCount(anyString(), anyString())).thenReturn(Promise.of(2));

        ProjectContextBuilder builder = ProjectContextBuilder.builder()
                .requirementsAdapter(requirementsAdapter)
                .codeRepoAdapter(codeRepoAdapter)
                .kgAdapter(kgAdapter)
                .agentAdapter(agentAdapter)
                .build();

        ProjectContext ctx = runPromise(() ->
                builder.buildContext("proj-2", "tenant-2", "shape"));

        assertThat(ctx.projectId()).isEqualTo("proj-2");
        assertThat(ctx.tenantId()).isEqualTo("tenant-2");
        assertThat(ctx.currentPhase()).isEqualTo("shape");
        assertThat(ctx.requirementCount()).isEqualTo(5);
        assertThat(ctx.averageClarityScore()).isEqualTo(0.85);
        assertThat(ctx.codeCommitCount()).isEqualTo(42);
        assertThat(ctx.testCoveragePercent()).isEqualTo(78);
        assertThat(ctx.buildPassing()).isTrue();
        assertThat(ctx.decisionCount()).isEqualTo(3);
        assertThat(ctx.activeAgentCount()).isEqualTo(2);
    }

    // ─── Adapter failures (graceful fallback) ─────────────────────────────────

    @Nested
    @DisplayName("Graceful fallback on adapter failure")
    class GracefulFallback {

        @Test
        @DisplayName("should use default 0 when requirementsAdapter.requirementCount fails")
        void shouldFallbackRequirementCount() {
            when(requirementsAdapter.requirementCount(anyString(), anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("DB unavailable")));
            when(requirementsAdapter.averageClarityScore(anyString(), anyString()))
                    .thenReturn(Promise.of(0.7));

            ProjectContextBuilder builder = ProjectContextBuilder.builder()
                    .requirementsAdapter(requirementsAdapter)
                    .build();

            ProjectContext ctx = runPromise(() ->
                    builder.buildContext("proj-3", "tenant-3", "intent"));

            assertThat(ctx.requirementCount()).isZero();
            assertThat(ctx.averageClarityScore()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("should use default 0.0 when averageClarityScore fails")
        void shouldFallbackAverageClarityScore() {
            when(requirementsAdapter.requirementCount(anyString(), anyString()))
                    .thenReturn(Promise.of(3));
            when(requirementsAdapter.averageClarityScore(anyString(), anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("timeout")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder()
                    .requirementsAdapter(requirementsAdapter)
                    .build();

            ProjectContext ctx = runPromise(() ->
                    builder.buildContext("proj-4", "tenant-4", "shape"));

            assertThat(ctx.requirementCount()).isEqualTo(3);
            assertThat(ctx.averageClarityScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should use null for buildPassing when codeRepoAdapter fails")
        void shouldFallbackBuildPassing() {
            when(codeRepoAdapter.commitCount(anyString(), anyString())).thenReturn(Promise.of(5));
            when(codeRepoAdapter.testCoveragePercent(anyString(), anyString())).thenReturn(Promise.of(60));
            when(codeRepoAdapter.buildPassing(anyString(), anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("CI unavailable")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder()
                    .codeRepoAdapter(codeRepoAdapter)
                    .build();

            ProjectContext ctx = runPromise(() ->
                    builder.buildContext("proj-5", "tenant-5", "run"));

            assertThat(ctx.buildPassing()).isNull();
            assertThat(ctx.codeCommitCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("should use 0 for decisionCount when kgAdapter fails")
        void shouldFallbackDecisionCount() {
            when(kgAdapter.decisionCount(anyString(), anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("KG offline")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder()
                    .kgAdapter(kgAdapter)
                    .build();

            ProjectContext ctx = runPromise(() ->
                    builder.buildContext("proj-6", "tenant-6", "review"));

            assertThat(ctx.decisionCount()).isZero();
        }

        @Test
        @DisplayName("should use 0 for activeAgentCount when agentAdapter fails")
        void shouldFallbackActiveAgentCount() {
            when(agentAdapter.activeAgentCount(anyString(), anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("agent runtime unavailable")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder()
                    .agentAdapter(agentAdapter)
                    .build();

            ProjectContext ctx = runPromise(() ->
                    builder.buildContext("proj-7", "tenant-7", "generate"));

            assertThat(ctx.activeAgentCount()).isZero();
        }
    }

    // ─── Partial adapters ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should use requirements adapter only and default others")
    void shouldUseRequirementsAdapterOnly() {
        when(requirementsAdapter.requirementCount(anyString(), anyString())).thenReturn(Promise.of(7));
        when(requirementsAdapter.averageClarityScore(anyString(), anyString())).thenReturn(Promise.of(0.9));

        ProjectContextBuilder builder = ProjectContextBuilder.builder()
                .requirementsAdapter(requirementsAdapter)
                .build();

        ProjectContext ctx = runPromise(() ->
                builder.buildContext("proj-8", "tenant-8", "intent"));

        assertThat(ctx.requirementCount()).isEqualTo(7);
        assertThat(ctx.averageClarityScore()).isEqualTo(0.9);
        assertThat(ctx.codeCommitCount()).isZero();
        assertThat(ctx.testCoveragePercent()).isEqualTo(-1);
        assertThat(ctx.buildPassing()).isNull();
        assertThat(ctx.decisionCount()).isZero();
        assertThat(ctx.activeAgentCount()).isZero();
    }

    // ─── Null argument guards ─────────────────────────────────────────────────

    @Test
    @DisplayName("should throw NullPointerException when projectId is null")
    void shouldThrowOnNullProjectId() {
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build();
        assertThatThrownBy(() -> runPromise(() ->
                builder.buildContext(null, "tenant-1", "intent")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NullPointerException when tenantId is null")
    void shouldThrowOnNullTenantId() {
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build();
        assertThatThrownBy(() -> runPromise(() ->
                builder.buildContext("proj-1", null, "intent")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw NullPointerException when currentPhase is null")
    void shouldThrowOnNullCurrentPhase() {
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build();
        assertThatThrownBy(() -> runPromise(() ->
                builder.buildContext("proj-1", "tenant-1", null)))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── ProjectContext helper methods ────────────────────────────────────────

    @Nested
    @DisplayName("ProjectContext helper methods")
    class ProjectContextHelpers {

        @Test
        @DisplayName("hasRequirements() should return false when requirementCount is 0")
        void hasRequirements_falseWhenZero() {
            ProjectContext ctx = new ProjectContext("p", "t", "intent",
                    0, 0.0, 0, -1, null, 0, 0);
            assertThat(ctx.hasRequirements()).isFalse();
        }

        @Test
        @DisplayName("hasRequirements() should return true when requirementCount > 0")
        void hasRequirements_trueWhenPositive() {
            ProjectContext ctx = new ProjectContext("p", "t", "intent",
                    1, 0.0, 0, -1, null, 0, 0);
            assertThat(ctx.hasRequirements()).isTrue();
        }

        @Test
        @DisplayName("meetsClarity() should return true when score meets threshold")
        void meetsClarity_trueAboveThreshold() {
            ProjectContext ctx = new ProjectContext("p", "t", "shape",
                    3, 0.8, 0, -1, null, 0, 0);
            assertThat(ctx.meetsClarity(0.7)).isTrue();
        }

        @Test
        @DisplayName("meetsClarity() should return false when score below threshold")
        void meetsClarity_falseBelowThreshold() {
            ProjectContext ctx = new ProjectContext("p", "t", "shape",
                    3, 0.5, 0, -1, null, 0, 0);
            assertThat(ctx.meetsClarity(0.7)).isFalse();
        }

        @Test
        @DisplayName("hasCoverage() should return true when coverage meets minimum")
        void hasCoverage_trueAboveMin() {
            ProjectContext ctx = new ProjectContext("p", "t", "run",
                    3, 0.9, 10, 70, true, 0, 0);
            assertThat(ctx.hasCoverage(60)).isTrue();
        }

        @Test
        @DisplayName("hasCoverage() should return false when coverage below minimum")
        void hasCoverage_falseBelowMin() {
            ProjectContext ctx = new ProjectContext("p", "t", "run",
                    3, 0.9, 10, 40, true, 0, 0);
            assertThat(ctx.hasCoverage(60)).isFalse();
        }
    }
}
