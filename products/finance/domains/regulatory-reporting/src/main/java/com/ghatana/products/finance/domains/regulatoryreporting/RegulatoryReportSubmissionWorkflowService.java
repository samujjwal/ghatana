package com.ghatana.products.finance.domains.regulatoryreporting;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Regulatory Report Submission workflow service with maker-checker gate.
 *
 * <p>Executes the complete regulatory report submission pipeline:
 * <ol>
 *   <li>Schedule trigger → data extraction</li>
 *   <li>Report rendering</li>
 *   <li>WAIT maker-checker approval</li>
 *   <li>Submit via regulator adapter (up to 3 attempts on NACK)</li>
 *   <li>WAIT ACK/NACK per attempt</li>
 *   <li>ACK: archive + audit notification</li>
 *   <li>NACK (max retries exhausted): fail + audit notification</li>
 * </ol>
 * Enforces SLA deadlines at the maker-checker step.</p>
 *
 * <p>Extracted from {@code products/app-platform/kernel/workflow-orchestration} per
 * KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md Workstream C (Day 12):
 * finance-shaped workflow services belong in the regulatory-reporting domain pack,
 * not in the generic platform layer.</p>
 *
 * @doc.type    class
 * @doc.purpose Regulatory report submission workflow — maker-checker, SLA enforcement, retry
 * @doc.layer   product
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class RegulatoryReportSubmissionWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    /**
     * Port for extracting source data for a report type and reporting period.
     */
    public interface ReportDataExtractPort {
        Map<String, Object> extractData(String reportType, String reportingPeriod) throws Exception;
    }

    /**
     * Port for rendering the report into submission-ready bytes.
     */
    public interface ReportRenderPort {
        byte[] render(String reportType, Map<String, Object> data) throws Exception;
    }

    /**
     * Port for the maker-checker approval gate.
     */
    public interface MakerCheckerPort {
        String submitForApproval(String taskType, Map<String, Object> payload) throws Exception;
        MakerCheckerDecision awaitDecision(String taskId, long timeoutMs) throws Exception;
    }

    /**
     * Port for submitting the report to the regulator and polling for acknowledgement.
     */
    public interface RegulatorSubmissionPort {
        SubmissionResult submit(String reportType, byte[] reportBytes, Map<String, String> metadata) throws Exception;
        AckNackResult pollAck(String submissionId, long timeoutMs) throws Exception;
    }

    /**
     * Port for archiving the submitted report.
     */
    public interface ReportArchivePort {
        String archive(String reportType, String reportingPeriod, byte[] reportBytes, String submissionRef) throws Exception;
    }

    /**
     * Port for emitting audit notifications on submission outcome.
     */
    public interface AuditNotificationPort {
        void notifySubmitted(String reportType, String reportingPeriod, String archiveRef) throws Exception;
        void notifyRejected(String reportType, String reportingPeriod, String reason) throws Exception;
    }

    /**
     * Port for managing workflow instance lifecycle steps.
     */
    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> ctx) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    /**
     * Maker-checker approval outcome.
     */
    public enum MakerCheckerDecision { APPROVED, REJECTED }

    /**
     * Result of a single submission attempt.
     */
    public record SubmissionResult(String submissionId, boolean accepted, String rejectionReason) {}

    /**
     * ACK/NACK result from the regulator's acknowledgement channel.
     */
    public record AckNackResult(String submissionId, boolean acked, String reason) {}

    /**
     * Final result of a complete report submission workflow run.
     */
    public record ReportSubmissionRun(
        String instanceId, String reportType, String reportingPeriod,
        String status, String archiveRef, int submissionAttempts
    ) {}

    private static final int MAX_RESUBMISSION_ATTEMPTS = 3;

    // ── Fields ───────────────────────────────────────────────────────────────

    private final ReportDataExtractPort dataExtract;
    private final ReportRenderPort reportRender;
    private final MakerCheckerPort makerChecker;
    private final RegulatorSubmissionPort regulatorSubmission;
    private final ReportArchivePort reportArchive;
    private final AuditNotificationPort auditNotification;
    private final WorkflowInstancePort workflowInstance;
    private final Executor executor;
    private final Counter submittedCounter;
    private final Counter rejectedCounter;
    private final Counter resubmitCounter;

    /**
     * Creates a new regulatory report submission workflow service.
     *
     * @param dataExtract        port for data extraction
     * @param reportRender       port for report rendering
     * @param makerChecker       port for maker-checker approval
     * @param regulatorSubmission port for regulator submission and ACK polling
     * @param reportArchive      port for report archiving
     * @param auditNotification  port for audit notifications
     * @param workflowInstance   port for workflow lifecycle
     * @param registry           Micrometer registry for metrics
     * @param executor           executor for blocking I/O operations
     */
    public RegulatoryReportSubmissionWorkflowService(
        ReportDataExtractPort dataExtract,
        ReportRenderPort reportRender,
        MakerCheckerPort makerChecker,
        RegulatorSubmissionPort regulatorSubmission,
        ReportArchivePort reportArchive,
        AuditNotificationPort auditNotification,
        WorkflowInstancePort workflowInstance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.dataExtract         = Objects.requireNonNull(dataExtract, "dataExtract");
        this.reportRender        = Objects.requireNonNull(reportRender, "reportRender");
        this.makerChecker        = Objects.requireNonNull(makerChecker, "makerChecker");
        this.regulatorSubmission = Objects.requireNonNull(regulatorSubmission, "regulatorSubmission");
        this.reportArchive       = Objects.requireNonNull(reportArchive, "reportArchive");
        this.auditNotification   = Objects.requireNonNull(auditNotification, "auditNotification");
        this.workflowInstance    = Objects.requireNonNull(workflowInstance, "workflowInstance");
        this.executor            = Objects.requireNonNull(executor, "executor");
        this.submittedCounter    = Counter.builder("regulatory.report.submitted").register(registry);
        this.rejectedCounter     = Counter.builder("regulatory.report.rejected").register(registry);
        this.resubmitCounter     = Counter.builder("regulatory.report.resubmitted").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Executes the regulatory report submission workflow.
     *
     * <p>Called by the K-15 cron trigger for scheduled reports. Enforces an SLA deadline
     * at the maker-checker step.</p>
     *
     * @param reportType      the regulatory report type identifier (e.g., "SEBON_DAILY_TRADE")
     * @param reportingPeriod the reporting period (e.g., "2026-03-19")
     * @param slaDeadlineMs   maximum wait time in milliseconds for maker-checker approval
     * @return Promise resolving to the report submission run result
     */
    public Promise<ReportSubmissionRun> submitReport(
        String reportType, String reportingPeriod, long slaDeadlineMs
    ) {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(reportingPeriod, "reportingPeriod");
        return Promise.ofBlocking(executor, () -> {
            String instanceId = workflowInstance.startInstance("RegulatoryReportSubmission",
                Map.of("reportType", reportType, "reportingPeriod", reportingPeriod));

            try {
                // Step 1: Data extraction
                Map<String, Object> data = dataExtract.extractData(reportType, reportingPeriod);
                workflowInstance.completeStep(instanceId, "DATA_EXTRACT",
                    Map.of("rowCount", data.size()));

                // Step 2: Render report
                byte[] reportBytes = reportRender.render(reportType, data);
                workflowInstance.completeStep(instanceId, "RENDER",
                    Map.of("sizeBytes", reportBytes.length));

                // Step 3: WAIT maker-checker approval
                String taskId = makerChecker.submitForApproval("REGULATORY_REPORT",
                    Map.of("reportType", reportType, "period", reportingPeriod,
                        "sizeBytes", reportBytes.length));
                MakerCheckerDecision decision = makerChecker.awaitDecision(taskId, slaDeadlineMs);

                if (decision == MakerCheckerDecision.REJECTED) {
                    workflowInstance.failStep(instanceId, "MAKER_CHECKER", "Rejected by checker");
                    return new ReportSubmissionRun(instanceId, reportType, reportingPeriod,
                        "REJECTED_BY_CHECKER", null, 0);
                }
                workflowInstance.completeStep(instanceId, "MAKER_CHECKER",
                    Map.of("decision", decision.name()));

                // Steps 4+5: Submit with up to 3 resubmissions on NACK
                for (int attempt = 1; attempt <= MAX_RESUBMISSION_ATTEMPTS; attempt++) {
                    SubmissionResult submission = regulatorSubmission.submit(reportType, reportBytes,
                        Map.of("reportingPeriod", reportingPeriod,
                            "attempt", String.valueOf(attempt)));

                    if (!submission.accepted()) {
                        workflowInstance.failStep(instanceId, "SUBMIT_ATTEMPT_" + attempt,
                            submission.rejectionReason());
                        continue;
                    }

                    AckNackResult ack = regulatorSubmission.pollAck(
                        submission.submissionId(), 300_000L); // 5-min ACK timeout
                    if (ack.acked()) {
                        String archiveRef = reportArchive.archive(reportType, reportingPeriod,
                            reportBytes, submission.submissionId());
                        auditNotification.notifySubmitted(reportType, reportingPeriod, archiveRef);
                        workflowInstance.completeStep(instanceId, "SUBMIT",
                            Map.of("ref", submission.submissionId(), "archive", archiveRef));
                        submittedCounter.increment();
                        return new ReportSubmissionRun(instanceId, reportType, reportingPeriod,
                            "SUBMITTED", archiveRef, attempt);
                    }

                    // NACK — retry if attempts remain
                    rejectedCounter.increment();
                    if (attempt < MAX_RESUBMISSION_ATTEMPTS) resubmitCounter.increment();
                }

                auditNotification.notifyRejected(reportType, reportingPeriod,
                    "Max resubmissions reached");
                workflowInstance.failStep(instanceId, "SUBMIT",
                    "Max " + MAX_RESUBMISSION_ATTEMPTS + " submissions exhausted");
                return new ReportSubmissionRun(instanceId, reportType, reportingPeriod,
                    "FAILED", null, MAX_RESUBMISSION_ATTEMPTS);

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "SUBMISSION_WORKFLOW", e.getMessage());
                throw e;
            }
        });
    }
}
