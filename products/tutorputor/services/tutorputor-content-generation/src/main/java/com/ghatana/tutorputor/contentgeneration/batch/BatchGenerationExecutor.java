package com.ghatana.tutorputor.contentgeneration.batch;

import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.Domain;
import com.ghatana.tutorputor.contentgeneration.generators.AnimationGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.AssessmentGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.ClaimGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.ExampleGenerator;
import com.ghatana.tutorputor.contentgeneration.generators.SimulationGenerator;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Batch executor for generation jobs produced by the Fastify planner.
 *
 * <p>Receives a list of {@link GenerationJobInput} items and routes each
 * to the appropriate generator. Returns typed {@link GenerationJobResult}
 * items that the Fastify control plane persists back into the GenerationJob
 * table.
 *
 * <p>Jobs execute sequentially with claim generation first (other jobs may
 * depend on claims). The executor does not manage state — it is a pure
 * processing unit invoked by gRPC or the orchestrator.
 *
 * @doc.type class
 * @doc.purpose Execute batches of generation jobs using existing generators
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BatchGenerationExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(BatchGenerationExecutor.class);

    private final ClaimGenerator claimGenerator;
    private final ExampleGenerator exampleGenerator;
    private final SimulationGenerator simulationGenerator;
    private final AnimationGenerator animationGenerator;
    private final AssessmentGenerator assessmentGenerator;
    private final MetricsCollector metrics;

    /**
     * Creates a BatchGenerationExecutor.
     *
     * @param claimGenerator      claim generator (non-null)
     * @param exampleGenerator    example generator (non-null)
     * @param simulationGenerator simulation generator (non-null)
     * @param animationGenerator  animation generator (non-null)
     * @param assessmentGenerator assessment generator (non-null)
     * @param metrics             metrics collector (non-null)
     */
    public BatchGenerationExecutor(
            ClaimGenerator claimGenerator,
            ExampleGenerator exampleGenerator,
            SimulationGenerator simulationGenerator,
            AnimationGenerator animationGenerator,
            AssessmentGenerator assessmentGenerator,
            MetricsCollector metrics) {
        this.claimGenerator = Objects.requireNonNull(claimGenerator);
        this.exampleGenerator = Objects.requireNonNull(exampleGenerator);
        this.simulationGenerator = Objects.requireNonNull(simulationGenerator);
        this.animationGenerator = Objects.requireNonNull(animationGenerator);
        this.assessmentGenerator = Objects.requireNonNull(assessmentGenerator);
        this.metrics = Objects.requireNonNull(metrics);
    }

    /**
     * Execute a batch of generation jobs.
     *
     * <p>Claim jobs are executed first since other job types may depend on
     * their output. Each job failure is captured without aborting the batch.
     *
     * @param jobs list of job inputs (non-null, non-empty)
     * @return Promise of results in the same order as input jobs
     */
    public Promise<List<GenerationJobResult>> executeBatch(List<GenerationJobInput> jobs) {
        Objects.requireNonNull(jobs, "jobs cannot be null");
        if (jobs.isEmpty()) {
            return Promise.of(List.of());
        }

        LOG.info("Executing batch of {} generation jobs for request {}",
                jobs.size(), jobs.get(0).requestId());

        // Execute claims first, then the rest
        List<GenerationJobInput> claimJobs = new ArrayList<>();
        List<GenerationJobInput> otherJobs = new ArrayList<>();

        for (GenerationJobInput job : jobs) {
            if (job.jobType() == GenerationJobType.CLAIM) {
                claimJobs.add(job);
            } else {
                otherJobs.add(job);
            }
        }

        return executeSequentially(claimJobs)
                .then(claimResults -> executeSequentially(otherJobs)
                        .map(otherResults -> {
                            List<GenerationJobResult> all = new ArrayList<>(claimResults);
                            all.addAll(otherResults);
                            return all;
                        }));
    }

    /**
     * Execute a single generation job.
     *
     * @param input job input descriptor
     * @return Promise of the job result
     */
    public Promise<GenerationJobResult> executeJob(GenerationJobInput input) {
        long startTime = System.currentTimeMillis();

        return routeToGenerator(input)
                .map(outputData -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.incrementCounter(
                            "batch.generation.job.success",
                            "tenant", input.tenantId(),
                            "type", input.jobType().name()
                    );
                    metrics.recordTimer(
                            "batch.generation.job.duration",
                            duration,
                            "type", input.jobType().name()
                    );

                    Map<String, Object> diagnostics = Map.of(
                            "durationMs", duration,
                            "jobType", input.jobType().name()
                    );

                    return GenerationJobResult.success(
                            input.jobId(), input.jobType(), outputData, diagnostics, duration);
                })
                .then((result, exception) -> {
                    if (exception != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.warn("Generation job {} failed: {}",
                                input.jobId(), exception.getMessage());
                        metrics.incrementCounter(
                                "batch.generation.job.failure",
                                "tenant", input.tenantId(),
                                "type", input.jobType().name()
                        );
                        return Promise.of(GenerationJobResult.failure(
                                input.jobId(), input.jobType(),
                                exception.getMessage(), duration));
                    }
                    return Promise.of(result);
                });
    }

    // -----------------------------------------------------------------------
    // Internal routing
    // -----------------------------------------------------------------------

    private Promise<Map<String, Object>> routeToGenerator(GenerationJobInput input) {
        ContentGenerationRequest request = buildRequest(input);

        return switch (input.jobType()) {
            case CLAIM -> claimGenerator.generateClaims(request)
                    .map(claims -> {
                        Map<String, Object> out = new HashMap<>();
                        out.put("claims", claims);
                        out.put("count", claims.size());
                        return out;
                    });

            case EXPLAINER, WORKED_EXAMPLE -> claimGenerator.generateClaims(request)
                    .then(claims -> exampleGenerator.generateExamples(claims, request))
                    .map(examples -> {
                        Map<String, Object> out = new HashMap<>();
                        out.put("examples", examples);
                        out.put("count", examples.size());
                        return out;
                    });

            case SIMULATION -> claimGenerator.generateClaims(request)
                    .then(claims -> simulationGenerator.generateSimulations(claims, request))
                    .map(sims -> {
                        Map<String, Object> out = new HashMap<>();
                        out.put("simulations", sims);
                        out.put("count", sims.size());
                        return out;
                    });

            case ANIMATION -> claimGenerator.generateClaims(request)
                    .then(claims -> animationGenerator.generateAnimations(claims, request))
                    .map(anims -> {
                        Map<String, Object> out = new HashMap<>();
                        out.put("animations", anims);
                        out.put("count", anims.size());
                        return out;
                    });

            case ASSESSMENT -> claimGenerator.generateClaims(request)
                    .then(claims -> assessmentGenerator.generateAssessments(claims, request))
                    .map(items -> {
                        Map<String, Object> out = new HashMap<>();
                        out.put("assessments", items);
                        out.put("count", items.size());
                        return out;
                    });

            case EVALUATION -> Promise.of(Map.of(
                    "evaluationStatus", "pending_external",
                    "note", "Evaluation executed after all artifacts are generated"
            ));
        };
    }

    private ContentGenerationRequest buildRequest(GenerationJobInput input) {
        Domain domain;
        try {
            domain = Domain.valueOf(input.domain().toUpperCase());
        } catch (IllegalArgumentException e) {
            domain = Domain.GENERAL;
        }

        return ContentGenerationRequest.builder()
                .topic(input.topic())
                .gradeLevel(input.gradeLevel() != null ? input.gradeLevel() : "GRADE_8")
                .domain(domain)
                .tenantId(input.tenantId())
                .maxClaims(2)
                .maxExamples(3)
                .maxSimulations(1)
                .maxAnimations(1)
                .maxAssessments(3)
                .build();
    }

    private Promise<List<GenerationJobResult>> executeSequentially(
            List<GenerationJobInput> jobs) {
        if (jobs.isEmpty()) {
            return Promise.of(List.of());
        }

        List<GenerationJobResult> results = new ArrayList<>();

        return executeSequentiallyRecursive(jobs, 0, results)
                .map(ignored -> results);
    }

    private Promise<Void> executeSequentiallyRecursive(
            List<GenerationJobInput> jobs,
            int index,
            List<GenerationJobResult> results) {
        if (index >= jobs.size()) {
            return Promise.of(null);
        }

        return executeJob(jobs.get(index))
                .then(result -> {
                    results.add(result);
                    return executeSequentiallyRecursive(jobs, index + 1, results);
                });
    }
}
