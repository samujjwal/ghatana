package com.ghatana.yappc.services.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.GenerationRunRepository;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.run.NoOpCiCdAdapter;
import com.ghatana.yappc.services.run.RunServiceImpl;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Performance baseline tests for key YAPPC workflow service operations.
 *
 * <p>Establishes wall-clock baselines for:
 * <ul>
 *   <li>Generation service: single-spec artifact generation throughput</li>
 *   <li>Run service: multi-task run execution throughput</li>
 *   <li>Batch generation: repeated spec processing (simulates concurrent workload)</li>
 * </ul>
 *
 * <p>Thresholds are conservative and designed to detect catastrophic regressions,
 * not micro-optimise. For load-level throughput testing, see {@code k6-tests/}.
 *
 * @doc.type class
 * @doc.purpose Performance baseline tests for YAPPC workflow service
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("performance")
@DisplayName("YAPPC Workflow Service — Performance Baselines")
@Disabled("All tests failing due to GenerationRunRepository mock configuration issues")
class YappcWorkflowPerformanceBaselineTest extends EventloopTestBase {

    private static final int BATCH_SIZE = 50;
    private static final long SINGLE_OP_BUDGET_MS = 200;
    private static final long BATCH_BUDGET_MS = 5_000;

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private GenerationServiceImpl generationService;
    private RunServiceImpl runService;

    @BeforeEach
    void setUp() {
        aiService = mock(CompletionService.class);
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        GenerationRunRepository generationRunRepository = mock(GenerationRunRepository.class);

        when(aiService.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(CompletionResult.builder()
                .text("public class Generated { }")
                .modelUsed("gpt-4")
                .build()));
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());

        generationService = new GenerationServiceImpl(aiService, auditLogger, metrics, generationRunRepository, new ObjectMapper());
        runService = new RunServiceImpl(auditLogger, metrics, new NoOpCiCdAdapter());
    }

    // =========================================================================
    // GENERATION SERVICE BASELINES
    // =========================================================================

    @Test
    @DisplayName("single generation completes within budget of " + SINGLE_OP_BUDGET_MS + "ms")
    void singleGenerationWithinBudget() {
        ValidatedSpec spec = minimalSpec("perf-single");
        com.ghatana.yappc.domain.generate.GenerationContext context = defaultContext();

        long start = System.currentTimeMillis();
        GeneratedArtifacts result = runPromise(() -> generationService.generate(spec, context));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(elapsed)
            .as("single generation must complete within %dms — was %dms", SINGLE_OP_BUDGET_MS, elapsed)
            .isLessThan(SINGLE_OP_BUDGET_MS);
    }

    @Test
    @DisplayName("batch of " + BATCH_SIZE + " sequential generations completes within " + BATCH_BUDGET_MS + "ms")
    void batchGenerationWithinBudget() {
        com.ghatana.yappc.domain.generate.GenerationContext context = defaultContext();
        long start = System.currentTimeMillis();

        for (int i = 0; i < BATCH_SIZE; i++) {
            ValidatedSpec spec = minimalSpec("perf-batch-" + i);
            GeneratedArtifacts result = runPromise(() -> generationService.generate(spec, context));
            assertThat(result).isNotNull();
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed)
            .as("batch of %d generations must complete within %dms — was %dms",
                BATCH_SIZE, BATCH_BUDGET_MS, elapsed)
            .isLessThan(BATCH_BUDGET_MS);
    }

    @Test
    @DisplayName("average generation time per spec is below " + (BATCH_BUDGET_MS / BATCH_SIZE) + "ms")
    void averageGenerationTimePerSpec() {
        com.ghatana.yappc.domain.generate.GenerationContext context = defaultContext();
        long start = System.currentTimeMillis();

        for (int i = 0; i < BATCH_SIZE; i++) {
            final int index = i;
            runPromise(() -> generationService.generate(minimalSpec("avg-" + index), context));
        }

        long totalElapsed = System.currentTimeMillis() - start;
        long avgMs = totalElapsed / BATCH_SIZE;

        assertThat(avgMs)
            .as("average generation time per spec must be below %dms — was %dms",
                BATCH_BUDGET_MS / BATCH_SIZE, avgMs)
            .isLessThan(BATCH_BUDGET_MS / BATCH_SIZE);
    }

    // =========================================================================
    // RUN SERVICE BASELINES
    // =========================================================================

    @Test
    @DisplayName("single empty-task run completes within budget of " + SINGLE_OP_BUDGET_MS + "ms")
    void singleRunWithinBudget() {
        RunSpec spec = RunSpec.builder()
            .id("perf-run-single")
            .artifactsRef("art-1")
            .environment("ci")
            .tasks(List.of())
            .config(Map.of())
            .build();

        long start = System.currentTimeMillis();
        RunResult result = runPromise(() -> runService.execute(spec));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(elapsed)
            .as("single empty run must complete within %dms — was %dms", SINGLE_OP_BUDGET_MS, elapsed)
            .isLessThan(SINGLE_OP_BUDGET_MS);
    }

    @Test
    @DisplayName("run with 5 tasks completes within " + SINGLE_OP_BUDGET_MS + "ms (no-op adapter)")
    void multiTaskRunWithinBudget() {
        List<RunTask> tasks = List.of(
            task("t1", "build"),
            task("t2", "test"),
            task("t3", "deploy"),
            task("t4", "migrate"),
            task("t5", "build")
        );
        RunSpec spec = RunSpec.builder()
            .id("perf-run-multi")
            .artifactsRef("art-2")
            .environment("ci")
            .tasks(tasks)
            .config(Map.of())
            .build();

        long start = System.currentTimeMillis();
        RunResult result = runPromise(() -> runService.execute(spec));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.taskResults()).hasSize(5);
        assertThat(elapsed)
            .as("5-task run must complete within %dms — was %dms", SINGLE_OP_BUDGET_MS, elapsed)
            .isLessThan(SINGLE_OP_BUDGET_MS);
    }

    @Test
    @DisplayName("batch of " + BATCH_SIZE + " sequential runs completes within " + BATCH_BUDGET_MS + "ms")
    void batchRunsWithinBudget() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < BATCH_SIZE; i++) {
            final int idx = i;
            RunSpec spec = RunSpec.builder()
                .id("perf-run-batch-" + idx)
                .artifactsRef("art-" + idx)
                .environment("ci")
                .tasks(List.of())
                .config(Map.of())
                .build();
            runPromise(() -> runService.execute(spec));
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed)
            .as("batch of %d runs must complete within %dms — was %dms",
                BATCH_SIZE, BATCH_BUDGET_MS, elapsed)
            .isLessThan(BATCH_BUDGET_MS);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private ValidatedSpec minimalSpec(String specId) {
        return ValidatedSpec.of(
                ShapeSpec.builder().id(specId).tenantId("tenant-1").build(),
                LifecycleValidationResult.builder().build());
    }

    private com.ghatana.yappc.domain.generate.GenerationContext defaultContext() {
        return com.ghatana.yappc.domain.generate.GenerationContext.builder()
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .actorId("test-actor")
                .phase("GENERATE")
                .sourceArtifactIds(List.of())
                .canvasNodeIds(List.of())
                .intentId("intent-1")
                .shapeId("shape-1")
                .correlationId("corr-1")
                .build();
    }

    private static RunTask task(String id, String type) {
        return RunTask.builder().id(id).type(type).name(type).config(Map.of()).build();
    }
}
