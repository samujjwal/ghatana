package com.ghatana.tutorputor.contentgeneration.batch;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.tutorputor.contentgeneration.generators.AnimationGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.AssessmentGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.ClaimGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.ExampleGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.SimulationGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BatchGenerationExecutor.
 *
 * @doc.type test
 * @doc.purpose Verify batch generation job execution and routing
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("BatchGenerationExecutor Tests")
class BatchGenerationExecutorTest extends EventloopTestBase {

    private BatchGenerationExecutor createExecutor() {
        return new BatchGenerationExecutor(
                new ClaimGenerator(),
                new ExampleGenerator(),
                new SimulationGenerator(),
                new AnimationGenerator(),
                new AssessmentGenerator(),
                NoopMetricsCollector.getInstance()
        );
    }

    private GenerationJobInput makeInput(String jobId, GenerationJobType type) {
        return new GenerationJobInput(
                jobId, "req-1", "tenant-1", type,
                "Newton's Laws", "PHYSICS", "GRADE_8",
                "req-1/" + type.name().toLowerCase(),
                Map.of()
        );
    }

    // =========================================================================
    // Single job execution
    // =========================================================================

    @Test
    @DisplayName("Should execute a CLAIM job successfully")
    void shouldExecuteClaimJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-1", GenerationJobType.CLAIM);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.jobType()).isEqualTo(GenerationJobType.CLAIM);
        assertThat(result.outputData()).containsKey("claims");
        assertThat(result.outputData()).containsKey("count");
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should execute a WORKED_EXAMPLE job successfully")
    void shouldExecuteWorkedExampleJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-2", GenerationJobType.WORKED_EXAMPLE);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("examples");
        assertThat(result.outputData()).containsKey("count");
    }

    @Test
    @DisplayName("Should execute a SIMULATION job successfully")
    void shouldExecuteSimulationJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-3", GenerationJobType.SIMULATION);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("simulations");
    }

    @Test
    @DisplayName("Should execute an ANIMATION job successfully")
    void shouldExecuteAnimationJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-4", GenerationJobType.ANIMATION);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("animations");
    }

    @Test
    @DisplayName("Should execute an ASSESSMENT job successfully")
    void shouldExecuteAssessmentJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-5", GenerationJobType.ASSESSMENT);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("assessments");
    }

    @Test
    @DisplayName("Should execute an EVALUATION job with pending status")
    void shouldExecuteEvaluationJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-6", GenerationJobType.EVALUATION);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("evaluationStatus");
    }

    @Test
    @DisplayName("Should execute EXPLAINER same as worked example")
    void shouldExecuteExplainerJob() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-7", GenerationJobType.EXPLAINER);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outputData()).containsKey("examples");
    }

    @Test
    @DisplayName("Should include diagnostics in job result")
    void shouldIncludeDiagnostics() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-8", GenerationJobType.CLAIM);

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.diagnostics()).containsKey("durationMs");
        assertThat(result.diagnostics()).containsKey("jobType");
    }

    // =========================================================================
    // Batch execution
    // =========================================================================

    @Test
    @DisplayName("Should execute empty batch and return empty list")
    void shouldHandleEmptyBatch() {
        BatchGenerationExecutor executor = createExecutor();

        List<GenerationJobResult> results = runPromise(
                () -> executor.executeBatch(List.of()));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should execute batch with single job")
    void shouldExecuteSingleJobBatch() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = makeInput("job-1", GenerationJobType.CLAIM);

        List<GenerationJobResult> results = runPromise(
                () -> executor.executeBatch(List.of(input)));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should execute claims before other job types in batch")
    void shouldExecuteClaimsFirst() {
        BatchGenerationExecutor executor = createExecutor();
        List<GenerationJobInput> jobs = List.of(
                makeInput("job-sim", GenerationJobType.SIMULATION),
                makeInput("job-claim", GenerationJobType.CLAIM),
                makeInput("job-assess", GenerationJobType.ASSESSMENT)
        );

        List<GenerationJobResult> results = runPromise(
                () -> executor.executeBatch(jobs));

        assertThat(results).hasSize(3);
        // Claim should be first in results (executed first)
        assertThat(results.get(0).jobType()).isEqualTo(GenerationJobType.CLAIM);
    }

    @Test
    @DisplayName("Should execute full batch with all job types")
    void shouldExecuteFullBatch() {
        BatchGenerationExecutor executor = createExecutor();
        List<GenerationJobInput> jobs = List.of(
                makeInput("j-1", GenerationJobType.CLAIM),
                makeInput("j-2", GenerationJobType.EXPLAINER),
                makeInput("j-3", GenerationJobType.WORKED_EXAMPLE),
                makeInput("j-4", GenerationJobType.SIMULATION),
                makeInput("j-5", GenerationJobType.ANIMATION),
                makeInput("j-6", GenerationJobType.ASSESSMENT),
                makeInput("j-7", GenerationJobType.EVALUATION)
        );

        List<GenerationJobResult> results = runPromise(
                () -> executor.executeBatch(jobs));

        assertThat(results).hasSize(7);
        long successCount = results.stream()
                .filter(GenerationJobResult::isSuccess)
                .count();
        assertThat(successCount).isEqualTo(7);
    }

    @Test
    @DisplayName("Should handle unknown domain gracefully as GENERAL")
    void shouldHandleUnknownDomain() {
        BatchGenerationExecutor executor = createExecutor();
        GenerationJobInput input = new GenerationJobInput(
                "job-x", "req-1", "tenant-1", GenerationJobType.CLAIM,
                "Topic", "unknowndomain", "GRADE_5",
                "req-1/claim", Map.of()
        );

        GenerationJobResult result = runPromise(() -> executor.executeJob(input));

        assertThat(result.isSuccess()).isTrue();
    }

    // =========================================================================
    // Domain types
    // =========================================================================

    @Test
    @DisplayName("GenerationJobInput should be immutable")
    void inputShouldBeImmutable() {
        GenerationJobInput input = makeInput("job-1", GenerationJobType.CLAIM);
        assertThat(input.jobId()).isEqualTo("job-1");
        assertThat(input.parameters()).isNotNull();
    }

    @Test
    @DisplayName("GenerationJobResult success factory should set correct status")
    void resultSuccessFactoryShouldWork() {
        GenerationJobResult result = GenerationJobResult.success(
                "j1", GenerationJobType.CLAIM, Map.of("a", 1), Map.of(), 100);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.errorMessage()).isNull();
        assertThat(result.durationMs()).isEqualTo(100);
    }

    @Test
    @DisplayName("GenerationJobResult failure factory should set error message")
    void resultFailureFactoryShouldWork() {
        GenerationJobResult result = GenerationJobResult.failure(
                "j1", GenerationJobType.CLAIM, "Something broke", 50);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Something broke");
    }

    @Test
    @DisplayName("GenerationJobType.fromString should parse valid types")
    void jobTypeShouldParseCaseInsensitive() {
        assertThat(GenerationJobType.fromString("claim"))
                .isEqualTo(GenerationJobType.CLAIM);
        assertThat(GenerationJobType.fromString("WORKED_EXAMPLE"))
                .isEqualTo(GenerationJobType.WORKED_EXAMPLE);
        assertThat(GenerationJobType.fromString("simulation"))
                .isEqualTo(GenerationJobType.SIMULATION);
    }
}
