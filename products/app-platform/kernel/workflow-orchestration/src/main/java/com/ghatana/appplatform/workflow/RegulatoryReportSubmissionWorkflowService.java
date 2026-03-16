package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Pre-built Regulatory Report Submission workflow with maker-checker gate.
 *              Schedule → Data extraction → Report rendering → WAIT maker-checker →
 *              Submit via adapter → WAIT ACK/NACK → ACK: archive + notify / NACK: retry (max 3).
 *              SLA deadline enforcement.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; Promise.ofBlocking; Saga
 *
 * STORY-W01-013: Regulatory report submission workflow
 */
public class RegulatoryReportSubmissionWorkflowService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface ReportDataExtractPort {
        Map<String, Object> extractData(String reportType, String reportingPeriod) throws Exception;
    }

    public interface ReportRenderPort {
        byte[] render(String reportType, Map<String, Object> data) throws Exception;
    }

    public interface MakerCheckerPort {
        String submitForApproval(String taskType, Map<String, Object> payload) throws Exception;
        MakerCheckerDecision awaitDecision(String taskId, long timeoutMs) throws Exception;
    }

    public interface RegulatorSubmissionPort {
        SubmissionResult submit(String reportType, byte[] reportBytes, Map<String, String> metadata) throws Exception;
        AckNackResult pollAck(String submissionId, long timeoutMs) throws Exception;
    }

    public interface ReportArchivePort {
        String archive(String reportType, String reportingPeriod, byte[] reportBytes, String submissionRef) throws Exception;
    }

    public interface AuditNotificationPort {
        void notifySubmitted(String reportType, String reportingPeriod, String archiveRef) throws Exception;
        void notifyRejected(String reportType, String reportingPeriod, String reason) throws Exception;
    }

    public interface WorkflowInstancePort {
        String startInstance(String workflowName, Map<String, Object> ctx) throws Exception;
        void completeStep(String instanceId, String stepId, Map<String, Object> output) throws Exception;
        void failStep(String instanceId, String stepId, String reason) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum MakerCheckerDecision { APPROVED, REJECTED }

    public record SubmissionResult(String submissionId, boolean accepted, String rejectionReason) {}
    public record AckNackResult(String submissionId, boolean acked, String reason) {}

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
        this.dataExtract        = dataExtract;
        this.reportRender       = reportRender;
        this.makerChecker       = makerChecker;
        this.regulatorSubmission = regulatorSubmission;
        this.reportArchive      = reportArchive;
        this.auditNotification  = auditNotification;
        this.workflowInstance   = workflowInstance;
        this.executor           = executor;
        this.submittedCounter  = Counter.builder("regulatory.report.submitted").register(registry);
        this.rejectedCounter   = Counter.builder("regulatory.report.rejected").register(registry);
        this.resubmitCounter   = Counter.builder("regulatory.report.resubmitted").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute the regulatory report submission workflow.
     * Called by the K-15 cron trigger for scheduled reports.
     */
    public Promise<ReportSubmissionRun> submitReport(String reportType, String reportingPeriod, long slaDeadlineMs) {
        return Promise.ofBlocking(executor, () -> {
            String instanceId = workflowInstance.startInstance("RegulatoryReportSubmission",
                Map.of("reportType", reportType, "reportingPeriod", reportingPeriod));

            try {
                // Step 1: Data extraction
                Map<String, Object> data = dataExtract.extractData(reportType, reportingPeriod);
                workflowInstance.completeStep(instanceId, "DATA_EXTRACT", Map.of("rowCount", data.size()));

                // Step 2: Render report
                byte[] reportBytes = reportRender.render(reportType, data);
                workflowInstance.completeStep(instanceId, "RENDER", Map.of("sizeBytes", reportBytes.length));

                // Step 3: WAIT maker-checker approval
                String taskId = makerChecker.submitForApproval("REGULATORY_REPORT",
                    Map.of("reportType", reportType, "period", reportingPeriod, "sizeBytes", reportBytes.length));
                MakerCheckerDecision decision = makerChecker.awaitDecision(taskId, slaDeadlineMs);

                if (decision == MakerCheckerDecision.REJECTED) {
                    workflowInstance.failStep(instanceId, "MAKER_CHECKER", "Rejected by checker");
                    return new ReportSubmissionRun(instanceId, reportType, reportingPeriod, "REJECTED_BY_CHECKER", null, 0);
                }
                workflowInstance.completeStep(instanceId, "MAKER_CHECKER", Map.of("decision", decision.name()));

                // Steps 4+5: Submit with up to 3 resubmissions on NACK
                for (int attempt = 1; attempt <= MAX_RESUBMISSION_ATTEMPTS; attempt++) {
                    SubmissionResult submission = regulatorSubmission.submit(reportType, reportBytes,
                        Map.of("reportingPeriod", reportingPeriod, "attempt", String.valueOf(attempt)));

                    if (!submission.accepted()) {
                        workflowInstance.failStep(instanceId, "SUBMIT_ATTEMPT_" + attempt, submission.rejectionReason());
                        continue;
                    }

                    AckNackResult ack = regulatorSubmission.pollAck(submission.submissionId(), 300_000L); // 5 min
                    if (ack.acked()) {
                        String archiveRef = reportArchive.archive(reportType, reportingPeriod, reportBytes, submission.submissionId());
                        auditNotification.notifySubmitted(reportType, reportingPeriod, archiveRef);
                        workflowInstance.completeStep(instanceId, "SUBMIT", Map.of("ref", submission.submissionId(), "archive", archiveRef));
                        submittedCounter.increment();
                        return new ReportSubmissionRun(instanceId, reportType, reportingPeriod, "SUBMITTED", archiveRef, attempt);
                    }

                    // NACK — retry
                    rejectedCounter.increment();
                    if (attempt < MAX_RESUBMISSION_ATTEMPTS) resubmitCounter.increment();
                }

                auditNotification.notifyRejected(reportType, reportingPeriod, "Max resubmissions reached");
                workflowInstance.failStep(instanceId, "SUBMIT", "Max " + MAX_RESUBMISSION_ATTEMPTS + " submissions exhausted");
                return new ReportSubmissionRun(instanceId, reportType, reportingPeriod, "FAILED", null, MAX_RESUBMISSION_ATTEMPTS);

            } catch (Exception e) {
                workflowInstance.failStep(instanceId, "SUBMISSION_WORKFLOW", e.getMessage());
                throw e;
            }
        });
    }
}
