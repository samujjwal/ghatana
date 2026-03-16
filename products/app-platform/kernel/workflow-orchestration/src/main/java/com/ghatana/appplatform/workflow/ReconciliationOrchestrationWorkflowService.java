package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Pre-built Daily Client Money Reconciliation workflow orchestration service.
 *              Triggers at 06:00, extracts internal + external data in parallel,
 *              runs matching engine, routes breaks to review, then finalises and submits.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga with compensation
 *
 * STORY-W01-010: Reconciliation orchestration workflow
 *
 * Workflow definition (YAML concept stored via WorkflowDefinitionService):
 *   name: DailyClientMoneyReconciliation
 *   trigger: SCHEDULE (cron: "0 6 * * *")
 *   steps:
 *     1. PARALLEL: ExtractInternalBalances + FetchExternalStatements
 *     2. RunMatchingEngine
 *     3. DECISION: hasBreaks?
 *        → false: GenerateReport → Submit
 *        → true:  ClassifyBreaks → RouteToReviewTeam → WAIT(signal:BreaksResolved) → Finalize
 */
public class ReconciliationOrchestrationWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface BalanceExtractPort {
        List<BalanceEntry> extractInternalBalances(String businessDate) throws Exception;
    }

    public interface StatementFetchPort {
        List<BalanceEntry> fetchExternalStatements(String businessDate) throws Exception;
    }

    public interface MatchingEnginePort {
        MatchingResult runMatching(List<BalanceEntry> internal, List<BalanceEntry> external) throws Exception;
    }

    public interface BreakRouterPort {
        void routeBreaksToReview(String reconciliationId, List<BreakItem> breaks) throws Exception;
    }

    public interface ReportSubmissionPort {
        String submitReport(ReconciliationReport report) throws Exception;
    }

    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> triggerContext) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record BalanceEntry(
        String clientId, String accountId, String currency, long amountCents
    ) {}

    public record BreakItem(
        String clientId, String currency, long internalAmountCents, long externalAmountCents, long diffCents
    ) {}

    public record MatchingResult(boolean hasBreaks, List<BreakItem> breaks, int matchedCount) {}

    public record ReconciliationReport(
        String reconciliationId,
        String businessDate,
        int totalBalances,
        int matched,
        int breaks,
        String status,
        String submittedAt
    ) {}

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
        this.balanceExtract   = balanceExtract;
        this.statementFetch   = statementFetch;
        this.matchingEngine   = matchingEngine;
        this.breakRouter      = breakRouter;
        this.reportSubmission = reportSubmission;
        this.workflowInstance = workflowInstance;
        this.executor         = executor;
        this.reconRunCounter  = Counter.builder("recon.workflow.runs").register(registry);
        this.breakFoundCounter = Counter.builder("recon.workflow.breaks_found").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute the daily reconciliation workflow for the given business date.
     * Called by the K-15 cron trigger at 06:00.
     */
    public Promise<ReconciliationRun> runDailyReconciliation(String businessDate) {
        return Promise.ofBlocking(executor, () -> {
            String reconciliationId = UUID.randomUUID().toString();
            String instanceId = workflowInstance.startInstance(
                "DailyClientMoneyReconciliation",
                Map.of("businessDate", businessDate, "reconciliationId", reconciliationId)
            );

            try {
                // Step 1 PARALLEL: Extract internal + external in parallel using virtual threads
                List<BalanceEntry> internal;
                List<BalanceEntry> external;
                try (var exec = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                    var internalFuture = exec.submit(() -> balanceExtract.extractInternalBalances(businessDate));
                    var externalFuture = exec.submit(() -> statementFetch.fetchExternalStatements(businessDate));
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

                // Step 3: DECISION
                if (matching.hasBreaks()) {
                    breakFoundCounter.increment(matching.breaks().size());
                    // Route breaks to review team
                    breakRouter.routeBreaksToReview(reconciliationId, matching.breaks());
                    workflowInstance.completeStep(instanceId, "ROUTE_BREAKS", Map.of(
                        "breaks", matching.breaks().size()
                    ));
                    // WAIT state handled by WaitCorrelationStepService (signal: BreaksResolved)
                    return new ReconciliationRun(instanceId, reconciliationId, businessDate, matching,
                        null, "WAITING_FOR_BREAK_RESOLUTION");
                }

                // No breaks — generate and submit report
                ReconciliationReport report = buildReport(reconciliationId, businessDate,
                    internal.size(), matching.matchedCount(), 0, "CLEAN");
                String submissionRef = reportSubmission.submitReport(report);
                workflowInstance.completeStep(instanceId, "SUBMIT", Map.of("ref", submissionRef));

                return new ReconciliationRun(instanceId, reconciliationId, businessDate, matching, report, "COMPLETED");

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "RECONCILIATION", e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Finalize reconciliation after breaks are resolved (called when BreaksResolved signal arrives).
     */
    public Promise<ReconciliationReport> finalizeAfterBreakResolution(
        String reconciliationId,
        String businessDate,
        int totalBalances,
        int matched,
        int resolved
    ) {
        return Promise.ofBlocking(executor, () -> {
            ReconciliationReport report = buildReport(reconciliationId, businessDate,
                totalBalances, matched, resolved, "RESOLVED");
            reportSubmission.submitReport(report);
            return report;
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ReconciliationReport buildReport(String id, String date, int total, int matched, int breaks, String status) {
        return new ReconciliationReport(id, date, total, matched, breaks, status,
            java.time.Instant.now().toString());
    }
}
