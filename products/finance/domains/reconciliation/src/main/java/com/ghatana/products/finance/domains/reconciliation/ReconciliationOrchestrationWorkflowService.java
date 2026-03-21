package com.ghatana.products.finance.domains.reconciliation;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Daily Client Money Reconciliation workflow orchestration service.
 *
 * <p>Executes the following reconciliation pipeline:
 * <ol>
 *   <li>PARALLEL extraction: internal balances + external statements</li>
 *   <li>Run matching engine</li>
 *   <li>DECISION: breaks found?</li>
 *   <li>If breaks: classify + route to review team + WAIT (BreaksResolved signal)</li>
 *   <li>If clean: generate report + submit</li>
 * </ol>
 * Includes saga compensation on extraction or matching failure.</p>
 *
 * <p>Extracted from {@code products/app-platform/kernel/workflow-orchestration} per
 * KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md Workstream C (Day 12):
 * finance-shaped workflow services belong in the reconciliation domain pack,
 * not in the generic platform layer.</p>
 *
 * @doc.type    class
 * @doc.purpose Reconciliation workflow — parallel extraction, matching engine, break routing
 * @doc.layer   product
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga with compensation
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class ReconciliationOrchestrationWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    /**
     * Port for extracting internal balances for a given business date.
     */
    public interface BalanceExtractPort {
        List<BalanceEntry> extractInternalBalances(String businessDate) throws Exception;
    }

    /**
     * Port for fetching external statements for a given business date.
     */
    public interface StatementFetchPort {
        List<BalanceEntry> fetchExternalStatements(String businessDate) throws Exception;
    }

    /**
     * Port for running the matching engine over internal and external balances.
     */
    public interface MatchingEnginePort {
        MatchingResult runMatching(List<BalanceEntry> internal, List<BalanceEntry> external) throws Exception;
    }

    /**
     * Port for routing identified breaks to the review operations team.
     */
    public interface BreakRouterPort {
        void routeBreaksToReview(String reconciliationId, List<BreakItem> breaks) throws Exception;
    }

    /**
     * Port for submitting the reconciliation report to the downstream system.
     */
    public interface ReportSubmissionPort {
        String submitReport(ReconciliationReport report) throws Exception;
    }

    /**
     * Port for managing workflow instance lifecycle steps.
     */
    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> triggerContext) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    /**
     * A single balance entry from internal or external source.
     */
    public record BalanceEntry(
        String clientId, String accountId, String currency, long amountCents
    ) {}

    /**
     * A reconciliation break item (discrepancy between internal and external).
     */
    public record BreakItem(
        String clientId, String currency,
        long internalAmountCents, long externalAmountCents, long diffCents
    ) {}

    /**
     * Result of the matching engine run.
     */
    public record MatchingResult(boolean hasBreaks, List<BreakItem> breaks, int matchedCount) {}

    /**
     * Reconciliation report submitted to the downstream system.
     */
    public record ReconciliationReport(
        String reconciliationId,
        String businessDate,
        int totalBalances,
        int matched,
        int breaks,
        String status,
        String submittedAt
    ) {}

    /**
     * Final result of a complete reconciliation workflow run.
     */
    public record ReconciliationRun(
        String instanceId,
        String reconciliationId,
        String businessDate,
        MatchingResult matchingResult,
        ReconciliationReport report,
        String status
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final BalanceExtractPort balanceExtract;
    private final StatementFetchPort statementFetch;
    private final MatchingEnginePort matchingEngine;
    private final BreakRouterPort breakRouter;
    private final ReportSubmissionPort reportSubmission;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter reconRunCounter;
    private final Counter breakFoundCounter;

    /**
     * Creates a new reconciliation orchestration workflow service.
     *
     * @param balanceExtract   port for internal balance extraction
     * @param statementFetch   port for external statement fetching
     * @param matchingEngine   port for running the matching engine
     * @param breakRouter      port for routing breaks to review
     * @param reportSubmission port for report submission
     * @param workflowInstance port for workflow lifecycle
     * @param registry         Micrometer registry for metrics
     * @param executor         executor for blocking I/O operations
     */
    public ReconciliationOrchestrationWorkflowService(
        BalanceExtractPort balanceExtract,
        StatementFetchPort statementFetch,
        MatchingEnginePort matchingEngine,
        BreakRouterPort breakRouter,
        ReportSubmissionPort reportSubmission,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.balanceExtract    = Objects.requireNonNull(balanceExtract, "balanceExtract");
        this.statementFetch    = Objects.requireNonNull(statementFetch, "statementFetch");
        this.matchingEngine    = Objects.requireNonNull(matchingEngine, "matchingEngine");
        this.breakRouter       = Objects.requireNonNull(breakRouter, "breakRouter");
        this.reportSubmission  = Objects.requireNonNull(reportSubmission, "reportSubmission");
        this.workflowInstance  = Objects.requireNonNull(workflowInstance, "workflowInstance");
        this.executor          = Objects.requireNonNull(executor, "executor");
        this.reconRunCounter   = Counter.builder("recon.workflow.runs").register(registry);
        this.breakFoundCounter = Counter.builder("recon.workflow.breaks_found").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Executes the daily reconciliation workflow for the given business date.
     *
     * <p>Called by the K-15 cron trigger at 06:00. If breaks are found, the workflow
     * pauses and waits for a {@code BreaksResolved} correlation signal before
     * {@link #finalizeAfterBreakResolution} is called.</p>
     *
     * @param businessDate the business date in ISO format (yyyy-MM-dd)
     * @return Promise resolving to the reconciliation run result
     */
    public Promise<ReconciliationRun> runDailyReconciliation(String businessDate) {
        Objects.requireNonNull(businessDate, "businessDate");
        return Promise.ofBlocking(executor, () -> {
            String reconciliationId = UUID.randomUUID().toString();
            String instanceId = workflowInstance.startInstance(
                "DailyClientMoneyReconciliation",
                Map.of("businessDate", businessDate, "reconciliationId", reconciliationId)
            );

            try {
                // Step 1 PARALLEL: Extract internal + external concurrently
                List<BalanceEntry> internal;
                List<BalanceEntry> external;
                try (var exec = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                    var internalFuture = exec.submit(
                        () -> balanceExtract.extractInternalBalances(businessDate));
                    var externalFuture = exec.submit(
                        () -> statementFetch.fetchExternalStatements(businessDate));
                    internal = internalFuture.get();
                    external = externalFuture.get();
                }
                workflowInstance.completeStep(instanceId, "PARALLEL_EXTRACT", Map.of(
                    "internalCount", internal.size(), "externalCount", external.size()
                ));

                // Step 2: Run matching engine
                MatchingResult matching = matchingEngine.runMatching(internal, external);
                workflowInstance.completeStep(instanceId, "MATCH", Map.of(
                    "hasBreaks", matching.hasBreaks(), "breakCount", matching.breaks().size()
                ));
                reconRunCounter.increment();

                // Step 3: DECISION — breaks?
                if (matching.hasBreaks()) {
                    breakFoundCounter.increment(matching.breaks().size());
                    breakRouter.routeBreaksToReview(reconciliationId, matching.breaks());
                    workflowInstance.completeStep(instanceId, "ROUTE_BREAKS",
                        Map.of("breaks", matching.breaks().size()));
                    // WAIT state — WaitCorrelationStepService signal: BreaksResolved
                    return new ReconciliationRun(instanceId, reconciliationId, businessDate,
                        matching, null, "WAITING_FOR_BREAK_RESOLUTION");
                }

                // Step 4: No breaks — generate and submit report
                ReconciliationReport report = buildReport(reconciliationId, businessDate,
                    internal.size(), matching.matchedCount(), 0, "CLEAN");
                String submissionRef = reportSubmission.submitReport(report);
                workflowInstance.completeStep(instanceId, "SUBMIT",
                    Map.of("ref", submissionRef));

                return new ReconciliationRun(instanceId, reconciliationId, businessDate,
                    matching, report, "COMPLETED");

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "RECONCILIATION", e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Finalizes the reconciliation after all breaks have been resolved.
     *
     * <p>Called when the {@code BreaksResolved} correlation signal arrives from
     * the operations review team.</p>
     *
     * @param reconciliationId the reconciliation run ID
     * @param businessDate     the business date the reconciliation covers
     * @param totalBalances    total balance entries processed
     * @param matched          number of matched entries
     * @param resolved         number of break items resolved
     * @return Promise resolving to the final reconciliation report
     */
    public Promise<ReconciliationReport> finalizeAfterBreakResolution(
        String reconciliationId,
        String businessDate,
        int totalBalances,
        int matched,
        int resolved
    ) {
        Objects.requireNonNull(reconciliationId, "reconciliationId");
        Objects.requireNonNull(businessDate, "businessDate");
        return Promise.ofBlocking(executor, () -> {
            ReconciliationReport report = buildReport(reconciliationId, businessDate,
                totalBalances, matched, resolved, "RESOLVED");
            reportSubmission.submitReport(report);
            return report;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ReconciliationReport buildReport(
        String id, String date, int total, int matched, int breaks, String status
    ) {
        return new ReconciliationReport(id, date, total, matched, breaks, status,
            java.time.Instant.now().toString());
    }
}
