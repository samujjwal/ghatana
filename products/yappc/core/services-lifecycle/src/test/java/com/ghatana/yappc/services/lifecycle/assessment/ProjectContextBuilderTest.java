/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("ProjectContextBuilder Tests [GH-90000]")
class ProjectContextBuilderTest extends EventloopTestBase {

    @Mock
    private ProjectContextBuilder.RequirementsAdapter requirementsAdapter;

    @Mock
    private ProjectContextBuilder.CodeRepoAdapter codeRepoAdapter;

    @Mock
    private ProjectContextBuilder.KgAdapter kgAdapter;

    @Mock
    private ProjectContextBuilder.AgentAdapter agentAdapter;

    // ─── No adapters (all defaults) ─────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("should build context with all defaults when no adapters configured [GH-90000]")
    void shouldBuildContextWithDefaults() { // GH-90000
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build(); // GH-90000

        ProjectContext ctx = runPromise(() -> // GH-90000
                builder.buildContext("proj-1", "tenant-1", "intent")); // GH-90000

        assertThat(ctx.projectId()).isEqualTo("proj-1 [GH-90000]");
        assertThat(ctx.tenantId()).isEqualTo("tenant-1 [GH-90000]");
        assertThat(ctx.currentPhase()).isEqualTo("intent [GH-90000]");
        assertThat(ctx.requirementCount()).isZero(); // GH-90000
        assertThat(ctx.averageClarityScore()).isEqualTo(0.0); // GH-90000
        assertThat(ctx.codeCommitCount()).isZero(); // GH-90000
        assertThat(ctx.testCoveragePercent()).isEqualTo(-1); // GH-90000
        assertThat(ctx.buildPassing()).isNull(); // GH-90000
        assertThat(ctx.decisionCount()).isZero(); // GH-90000
        assertThat(ctx.activeAgentCount()).isZero(); // GH-90000
    }

    // ─── All adapters ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should aggregate values from all adapters [GH-90000]")
    void shouldAggregateAllAdapters() { // GH-90000
        when(requirementsAdapter.requirementCount(anyString(), anyString())).thenReturn(Promise.of(5)); // GH-90000
        when(requirementsAdapter.averageClarityScore(anyString(), anyString())).thenReturn(Promise.of(0.85)); // GH-90000
        when(codeRepoAdapter.commitCount(anyString(), anyString())).thenReturn(Promise.of(42)); // GH-90000
        when(codeRepoAdapter.testCoveragePercent(anyString(), anyString())).thenReturn(Promise.of(78)); // GH-90000
        when(codeRepoAdapter.buildPassing(anyString(), anyString())).thenReturn(Promise.of(true)); // GH-90000
        when(kgAdapter.decisionCount(anyString(), anyString())).thenReturn(Promise.of(3)); // GH-90000
        when(agentAdapter.activeAgentCount(anyString(), anyString())).thenReturn(Promise.of(2)); // GH-90000

        ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                .requirementsAdapter(requirementsAdapter) // GH-90000
                .codeRepoAdapter(codeRepoAdapter) // GH-90000
                .kgAdapter(kgAdapter) // GH-90000
                .agentAdapter(agentAdapter) // GH-90000
                .build(); // GH-90000

        ProjectContext ctx = runPromise(() -> // GH-90000
                builder.buildContext("proj-2", "tenant-2", "shape")); // GH-90000

        assertThat(ctx.projectId()).isEqualTo("proj-2 [GH-90000]");
        assertThat(ctx.tenantId()).isEqualTo("tenant-2 [GH-90000]");
        assertThat(ctx.currentPhase()).isEqualTo("shape [GH-90000]");
        assertThat(ctx.requirementCount()).isEqualTo(5); // GH-90000
        assertThat(ctx.averageClarityScore()).isEqualTo(0.85); // GH-90000
        assertThat(ctx.codeCommitCount()).isEqualTo(42); // GH-90000
        assertThat(ctx.testCoveragePercent()).isEqualTo(78); // GH-90000
        assertThat(ctx.buildPassing()).isTrue(); // GH-90000
        assertThat(ctx.decisionCount()).isEqualTo(3); // GH-90000
        assertThat(ctx.activeAgentCount()).isEqualTo(2); // GH-90000
    }

    // ─── Adapter failures (graceful fallback) ───────────────────────────────── // GH-90000

    @Nested
    @DisplayName("Graceful fallback on adapter failure [GH-90000]")
    class GracefulFallback {

        @Test
        @DisplayName("should use default 0 when requirementsAdapter.requirementCount fails [GH-90000]")
        void shouldFallbackRequirementCount() { // GH-90000
            when(requirementsAdapter.requirementCount(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("DB unavailable [GH-90000]")));
            when(requirementsAdapter.averageClarityScore(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of(0.7)); // GH-90000

            ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                    .requirementsAdapter(requirementsAdapter) // GH-90000
                    .build(); // GH-90000

            ProjectContext ctx = runPromise(() -> // GH-90000
                    builder.buildContext("proj-3", "tenant-3", "intent")); // GH-90000

            assertThat(ctx.requirementCount()).isZero(); // GH-90000
            assertThat(ctx.averageClarityScore()).isEqualTo(0.7); // GH-90000
        }

        @Test
        @DisplayName("should use default 0.0 when averageClarityScore fails [GH-90000]")
        void shouldFallbackAverageClarityScore() { // GH-90000
            when(requirementsAdapter.requirementCount(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of(3)); // GH-90000
            when(requirementsAdapter.averageClarityScore(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("timeout [GH-90000]")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                    .requirementsAdapter(requirementsAdapter) // GH-90000
                    .build(); // GH-90000

            ProjectContext ctx = runPromise(() -> // GH-90000
                    builder.buildContext("proj-4", "tenant-4", "shape")); // GH-90000

            assertThat(ctx.requirementCount()).isEqualTo(3); // GH-90000
            assertThat(ctx.averageClarityScore()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("should use null for buildPassing when codeRepoAdapter fails [GH-90000]")
        void shouldFallbackBuildPassing() { // GH-90000
            when(codeRepoAdapter.commitCount(anyString(), anyString())).thenReturn(Promise.of(5)); // GH-90000
            when(codeRepoAdapter.testCoveragePercent(anyString(), anyString())).thenReturn(Promise.of(60)); // GH-90000
            when(codeRepoAdapter.buildPassing(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("CI unavailable [GH-90000]")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                    .codeRepoAdapter(codeRepoAdapter) // GH-90000
                    .build(); // GH-90000

            ProjectContext ctx = runPromise(() -> // GH-90000
                    builder.buildContext("proj-5", "tenant-5", "run")); // GH-90000

            assertThat(ctx.buildPassing()).isNull(); // GH-90000
            assertThat(ctx.codeCommitCount()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("should use 0 for decisionCount when kgAdapter fails [GH-90000]")
        void shouldFallbackDecisionCount() { // GH-90000
            when(kgAdapter.decisionCount(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("KG offline [GH-90000]")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                    .kgAdapter(kgAdapter) // GH-90000
                    .build(); // GH-90000

            ProjectContext ctx = runPromise(() -> // GH-90000
                    builder.buildContext("proj-6", "tenant-6", "review")); // GH-90000

            assertThat(ctx.decisionCount()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("should use 0 for activeAgentCount when agentAdapter fails [GH-90000]")
        void shouldFallbackActiveAgentCount() { // GH-90000
            when(agentAdapter.activeAgentCount(anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("agent runtime unavailable [GH-90000]")));

            ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                    .agentAdapter(agentAdapter) // GH-90000
                    .build(); // GH-90000

            ProjectContext ctx = runPromise(() -> // GH-90000
                    builder.buildContext("proj-7", "tenant-7", "generate")); // GH-90000

            assertThat(ctx.activeAgentCount()).isZero(); // GH-90000
        }
    }

    // ─── Partial adapters ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should use requirements adapter only and default others [GH-90000]")
    void shouldUseRequirementsAdapterOnly() { // GH-90000
        when(requirementsAdapter.requirementCount(anyString(), anyString())).thenReturn(Promise.of(7)); // GH-90000
        when(requirementsAdapter.averageClarityScore(anyString(), anyString())).thenReturn(Promise.of(0.9)); // GH-90000

        ProjectContextBuilder builder = ProjectContextBuilder.builder() // GH-90000
                .requirementsAdapter(requirementsAdapter) // GH-90000
                .build(); // GH-90000

        ProjectContext ctx = runPromise(() -> // GH-90000
                builder.buildContext("proj-8", "tenant-8", "intent")); // GH-90000

        assertThat(ctx.requirementCount()).isEqualTo(7); // GH-90000
        assertThat(ctx.averageClarityScore()).isEqualTo(0.9); // GH-90000
        assertThat(ctx.codeCommitCount()).isZero(); // GH-90000
        assertThat(ctx.testCoveragePercent()).isEqualTo(-1); // GH-90000
        assertThat(ctx.buildPassing()).isNull(); // GH-90000
        assertThat(ctx.decisionCount()).isZero(); // GH-90000
        assertThat(ctx.activeAgentCount()).isZero(); // GH-90000
    }

    // ─── Null argument guards ─────────────────────────────────────────────────

    @Test
    @DisplayName("should throw NullPointerException when projectId is null [GH-90000]")
    void shouldThrowOnNullProjectId() { // GH-90000
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build(); // GH-90000
        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                builder.buildContext(null, "tenant-1", "intent"))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should throw NullPointerException when tenantId is null [GH-90000]")
    void shouldThrowOnNullTenantId() { // GH-90000
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build(); // GH-90000
        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                builder.buildContext("proj-1", null, "intent"))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("should throw NullPointerException when currentPhase is null [GH-90000]")
    void shouldThrowOnNullCurrentPhase() { // GH-90000
        ProjectContextBuilder builder = ProjectContextBuilder.builder().build(); // GH-90000
        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                builder.buildContext("proj-1", "tenant-1", null))) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ─── ProjectContext helper methods ────────────────────────────────────────

    @Nested
    @DisplayName("ProjectContext helper methods [GH-90000]")
    class ProjectContextHelpers {

        @Test
        @DisplayName("hasRequirements() should return false when requirementCount is 0 [GH-90000]")
        void hasRequirements_falseWhenZero() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "intent", // GH-90000
                    0, 0.0, 0, -1, null, 0, 0);
            assertThat(ctx.hasRequirements()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasRequirements() should return true when requirementCount > 0 [GH-90000]")
        void hasRequirements_trueWhenPositive() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "intent", // GH-90000
                    1, 0.0, 0, -1, null, 0, 0);
            assertThat(ctx.hasRequirements()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("meetsClarity() should return true when score meets threshold [GH-90000]")
        void meetsClarity_trueAboveThreshold() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "shape", // GH-90000
                    3, 0.8, 0, -1, null, 0, 0);
            assertThat(ctx.meetsClarity(0.7)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("meetsClarity() should return false when score below threshold [GH-90000]")
        void meetsClarity_falseBelowThreshold() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "shape", // GH-90000
                    3, 0.5, 0, -1, null, 0, 0);
            assertThat(ctx.meetsClarity(0.7)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("hasCoverage() should return true when coverage meets minimum [GH-90000]")
        void hasCoverage_trueAboveMin() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "run", // GH-90000
                    3, 0.9, 10, 70, true, 0, 0);
            assertThat(ctx.hasCoverage(60)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("hasCoverage() should return false when coverage below minimum [GH-90000]")
        void hasCoverage_falseBelowMin() { // GH-90000
            ProjectContext ctx = new ProjectContext("p", "t", "run", // GH-90000
                    3, 0.9, 10, 40, true, 0, 0);
            assertThat(ctx.hasCoverage(60)).isFalse(); // GH-90000
        }
    }
}
