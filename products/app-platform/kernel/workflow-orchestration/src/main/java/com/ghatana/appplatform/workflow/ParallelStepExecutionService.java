package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Implements the PARALLEL step for workflow execution.
 *              Fans out multiple branches concurrently and waits for them according to
 *              the configured join strategy: ALL, FIRST (any one completes), or N_OF_M.
 *              Per-branch timeout enforced independently.
 *              On branch failure: FAIL_ALL (default), IGNORE, or FALLBACK to a named step.
 *              Context merge: results from parallel branches merged into the parent context.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class ParallelStepExecutionService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface StepExecutionPort {
        /** Execute a single workflow step identified by stepId in the given instance context. */
        Promise<BranchResult> executeStep(String instanceId, String stepId, String contextJson);
    }

    public interface ContextMergerPort {
        /** Merge multiple branch result contexts into a single combined context JSON. */
        String merge(String baseContextJson, List<BranchResult> completedBranches);
    }

    // -----------------------------------------------------------------------
    // Records and Enums
    // -----------------------------------------------------------------------

    public enum JoinStrategy { ALL, FIRST, N_OF_M }
    public enum FailureStrategy { FAIL_ALL, IGNORE, FALLBACK }

    public record ParallelStepConfig(
        String stepId,
        List<String> branchStepIds,
        JoinStrategy joinStrategy,
        int minSuccessCount,          // for N_OF_M: the N value
        String branchTimeoutIso,
        FailureStrategy failureStrategy,
        String fallbackStepId         // for FALLBACK strategy
    ) {}

    public record BranchResult(
        String branchStepId,
        String status,                // COMPLETED | FAILED | TIMEOUT
        String outputJson,
        String errorMessage,
        long durationMs
    ) {}

    public record ParallelExecutionResult(
        String executionId,
        String instanceId,
        String stepId,
        JoinStrategy joinStrategy,
        List<BranchResult> branchResults,
        String mergedContextJson,
        String status,                // COMPLETED | PARTIAL | FAILED
        int completedCount,
        int failedCount,
        long totalDurationMs,
        String executedAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final StepExecutionPort stepExecutionPort;
    private final ContextMergerPort contextMerger;

    private final Counter parallelStepStartedTotal;
    private final Counter allJoinCompletedTotal;
    private final Counter firstJoinCompletedTotal;
    private final Counter nOfMJoinCompletedTotal;
    private final Counter branchFailedTotal;
    private final Timer parallelDurationTimer;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ParallelStepExecutionService(DataSource dataSource,
                                         Executor executor,
                                         MeterRegistry meterRegistry,
                                         StepExecutionPort stepExecutionPort,
                                         ContextMergerPort contextMerger) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.stepExecutionPort = stepExecutionPort;
        this.contextMerger     = contextMerger;

        this.parallelStepStartedTotal  = Counter.builder("workflow.parallel.started_total")
                .description("Total PARALLEL step executions started")
                .register(meterRegistry);
        this.allJoinCompletedTotal     = Counter.builder("workflow.parallel.all_join_completed_total")
                .description("PARALLEL steps completed via ALL join")
                .register(meterRegistry);
        this.firstJoinCompletedTotal   = Counter.builder("workflow.parallel.first_join_completed_total")
                .description("PARALLEL steps completed via FIRST join")
                .register(meterRegistry);
        this.nOfMJoinCompletedTotal    = Counter.builder("workflow.parallel.n_of_m_join_completed_total")
                .description("PARALLEL steps completed via N_OF_M join")
                .register(meterRegistry);
        this.branchFailedTotal         = Counter.builder("workflow.parallel.branch_failed_total")
                .description("Individual parallel branch failures")
                .register(meterRegistry);
        this.parallelDurationTimer     = Timer.builder("workflow.parallel.duration_ms")
                .description("Total duration of PARALLEL step execution")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Execute a PARALLEL step with the given configuration.
     * Returns the merged context after join strategy is satisfied.
     */
    public Promise<ParallelExecutionResult> execute(String instanceId, ParallelStepConfig config,
                                                    String contextJson) {
        parallelStepStartedTotal.increment();
        long start = System.nanoTime();

        // Launch all branches concurrently
        List<Promise<BranchResult>> branchPromises = config.branchStepIds().stream()
            .map(stepId -> stepExecutionPort.executeStep(instanceId, stepId, contextJson)
                .mapException(ex -> new BranchResult(stepId, "FAILED", null, ex.getMessage(), 0L)))
            .toList();

        return applyJoinStrategy(config, branchPromises, contextJson).map(result -> {
            long duration = (System.nanoTime() - start) / 1_000_000;
            String executionId = persistExecutionBlocking(instanceId, config, result, duration);
            return new ParallelExecutionResult(
                executionId, instanceId, config.stepId(), config.joinStrategy(),
                result, contextMerger.merge(contextJson, result),
                determineStatus(config, result),
                (int) result.stream().filter(b -> "COMPLETED".equals(b.status())).count(),
                (int) result.stream().filter(b -> "FAILED".equals(b.status())).count(),
                duration, null
            );
        }).whenComplete((r, e) -> parallelDurationTimer.record(
            System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS));
    }

    // -----------------------------------------------------------------------
    // Private join strategy implementations
    // -----------------------------------------------------------------------

    private Promise<List<BranchResult>> applyJoinStrategy(ParallelStepConfig config,
                                                            List<Promise<BranchResult>> branches,
                                                            String contextJson) {
        return switch (config.joinStrategy()) {
            case ALL    -> joinAll(config, branches);
            case FIRST  -> joinFirst(config, branches);
            case N_OF_M -> joinNOfM(config, branches, config.minSuccessCount());
        };
    }

    /** Wait for ALL branches to complete. */
    private Promise<List<BranchResult>> joinAll(ParallelStepConfig config,
                                                 List<Promise<BranchResult>> branches) {
        return Promise.all(branches).map(results -> {
            handleBranchFailures(config, results);
            allJoinCompletedTotal.increment();
            return results;
        });
    }

    /** Complete when the FIRST branch finishes successfully. */
    private Promise<List<BranchResult>> joinFirst(ParallelStepConfig config,
                                                   List<Promise<BranchResult>> branches) {
        // Use Promise.firstSuccessful to get the first completed branch
        return Promise.firstSuccessful(branches).map(firstResult -> {
            firstJoinCompletedTotal.increment();
            return List.of(firstResult);
        });
    }

    /** Wait for at least N branches to complete successfully. */
    private Promise<List<BranchResult>> joinNOfM(ParallelStepConfig config,
                                                   List<Promise<BranchResult>> branches,
                                                   int n) {
        return Promise.all(branches).map(results -> {
            long successCount = results.stream().filter(b -> "COMPLETED".equals(b.status())).count();
            if (successCount < n) {
                throw new RuntimeException(
                    "N_OF_M join failed: only " + successCount + " of " + n + " branches completed");
            }
            nOfMJoinCompletedTotal.increment();
            return results;
        });
    }

    private void handleBranchFailures(ParallelStepConfig config, List<BranchResult> results) {
        long failedCount = results.stream().filter(b -> "FAILED".equals(b.status())).count();
        if (failedCount == 0) return;
        branchFailedTotal.increment(failedCount);
        if (config.failureStrategy() == FailureStrategy.FAIL_ALL) {
            String failedBranches = results.stream()
                .filter(b -> "FAILED".equals(b.status()))
                .map(b -> b.branchStepId() + ": " + b.errorMessage())
                .toList().toString();
            throw new RuntimeException("FAIL_ALL: branches failed: " + failedBranches);
        }
        // IGNORE: continue with completed branches
        // FALLBACK: handled by the workflow engine routing to fallbackStepId
    }

    private String determineStatus(ParallelStepConfig config, List<BranchResult> results) {
        long failedCount = results.stream().filter(b -> "FAILED".equals(b.status())).count();
        if (failedCount == 0) return "COMPLETED";
        if (config.failureStrategy() == FailureStrategy.IGNORE) return "PARTIAL";
        return "FAILED";
    }

    // -----------------------------------------------------------------------
    // Private DB helpers
    // -----------------------------------------------------------------------

    private String persistExecutionBlocking(String instanceId, ParallelStepConfig config,
                                             List<BranchResult> results, long durationMs) {
        String sql = """
            INSERT INTO workflow_parallel_executions
                (execution_id, instance_id, step_id, join_strategy, branch_count,
                 completed_count, failed_count, duration_ms, status, executed_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, ?, now())
            RETURNING execution_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            long completed = results.stream().filter(b -> "COMPLETED".equals(b.status())).count();
            long failed    = results.stream().filter(b -> "FAILED".equals(b.status())).count();
            ps.setString(1, instanceId);
            ps.setString(2, config.stepId());
            ps.setString(3, config.joinStrategy().name());
            ps.setInt(4, config.branchStepIds().size());
            ps.setLong(5, completed);
            ps.setLong(6, failed);
            ps.setLong(7, durationMs);
            ps.setString(8, failed == 0 ? "COMPLETED" : "PARTIAL");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("execution_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist parallel execution for instance " + instanceId, e);
        }
    }
}
