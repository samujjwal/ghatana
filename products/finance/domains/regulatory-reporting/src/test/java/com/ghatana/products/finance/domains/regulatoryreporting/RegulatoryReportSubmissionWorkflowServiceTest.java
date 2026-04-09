package com.ghatana.products.finance.domains.regulatoryreporting;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Verifies the finance regulatory submission workflow end-to-end control flow and retry semantics
 * @doc.layer product
 * @doc.pattern Test
 */
class RegulatoryReportSubmissionWorkflowServiceTest extends EventloopTestBase {

    @Test
    void submitReportResubmitsAfterNacksAndArchivesSuccessfulSubmission() {
        AtomicInteger submissionAttempts = new AtomicInteger();
        AtomicInteger submittedNotifications = new AtomicInteger();
        List<String> completedSteps = new ArrayList<>();
        List<String> failedSteps = new ArrayList<>();

        RegulatoryReportSubmissionWorkflowService service = new RegulatoryReportSubmissionWorkflowService(
            (reportType, reportingPeriod) -> Map.of("tradeCount", 42, "reportingPeriod", reportingPeriod),
            (reportType, data) -> "<report />".getBytes(),
            new RegulatoryReportSubmissionWorkflowService.MakerCheckerPort() {
                @Override
                public String submitForApproval(String taskType, Map<String, Object> payload) {
                    return "task-1";
                }

                @Override
                public RegulatoryReportSubmissionWorkflowService.MakerCheckerDecision awaitDecision(String taskId, long timeoutMs) {
                    return RegulatoryReportSubmissionWorkflowService.MakerCheckerDecision.APPROVED;
                }
            },
            new RegulatoryReportSubmissionWorkflowService.RegulatorSubmissionPort() {
                @Override
                public RegulatoryReportSubmissionWorkflowService.SubmissionResult submit(String reportType, byte[] reportBytes, Map<String, String> metadata) {
                    int attempt = submissionAttempts.incrementAndGet();
                    return new RegulatoryReportSubmissionWorkflowService.SubmissionResult("submission-" + attempt, true, null);
                }

                @Override
                public RegulatoryReportSubmissionWorkflowService.AckNackResult pollAck(String submissionId, long timeoutMs) {
                    return new RegulatoryReportSubmissionWorkflowService.AckNackResult(
                        submissionId,
                        "submission-3".equals(submissionId),
                        "submission-3".equals(submissionId) ? null : "NACK"
                    );
                }
            },
            (reportType, reportingPeriod, reportBytes, submissionRef) -> "archive-" + submissionRef,
            new RegulatoryReportSubmissionWorkflowService.AuditNotificationPort() {
                @Override
                public void notifySubmitted(String reportType, String reportingPeriod, String archiveRef) {
                    submittedNotifications.incrementAndGet();
                }

                @Override
                public void notifyRejected(String reportType, String reportingPeriod, String reason) {
                }
            },
            new RegulatoryReportSubmissionWorkflowService.WorkflowInstancePort() {
                @Override
                public String startInstance(String workflowName, Map<String, Object> ctx) {
                    return "instance-1";
                }

                @Override
                public void completeStep(String instanceId, String stepId, Map<String, Object> output) {
                    completedSteps.add(stepId);
                }

                @Override
                public void failStep(String instanceId, String stepId, String reason) {
                    failedSteps.add(stepId);
                }
            },
            new SimpleMeterRegistry(),
            Runnable::run
        );

        RegulatoryReportSubmissionWorkflowService.ReportSubmissionRun run = runPromise(
            () -> service.submitReport("SEBON_DAILY_TRADE", "2026-04-07", 30_000L)
        );

        assertEquals("SUBMITTED", run.status());
        assertEquals("archive-submission-3", run.archiveRef());
        assertEquals(3, run.submissionAttempts());
        assertEquals(1, submittedNotifications.get());
        assertEquals(List.of("DATA_EXTRACT", "RENDER", "MAKER_CHECKER", "SUBMIT"), completedSteps);
        assertEquals(List.of(), failedSteps);
    }

    @Test
    void submitReportStopsWhenMakerCheckerRejects() {
        AtomicInteger submissionAttempts = new AtomicInteger();
        List<String> failedSteps = new ArrayList<>();

        RegulatoryReportSubmissionWorkflowService service = new RegulatoryReportSubmissionWorkflowService(
            (reportType, reportingPeriod) -> Map.of("tradeCount", 5),
            (reportType, data) -> "<report />".getBytes(),
            new RegulatoryReportSubmissionWorkflowService.MakerCheckerPort() {
                @Override
                public String submitForApproval(String taskType, Map<String, Object> payload) {
                    return "task-2";
                }

                @Override
                public RegulatoryReportSubmissionWorkflowService.MakerCheckerDecision awaitDecision(String taskId, long timeoutMs) {
                    return RegulatoryReportSubmissionWorkflowService.MakerCheckerDecision.REJECTED;
                }
            },
            new RegulatoryReportSubmissionWorkflowService.RegulatorSubmissionPort() {
                @Override
                public RegulatoryReportSubmissionWorkflowService.SubmissionResult submit(String reportType, byte[] reportBytes, Map<String, String> metadata) {
                    submissionAttempts.incrementAndGet();
                    return new RegulatoryReportSubmissionWorkflowService.SubmissionResult("submission-rejected", true, null);
                }

                @Override
                public RegulatoryReportSubmissionWorkflowService.AckNackResult pollAck(String submissionId, long timeoutMs) {
                    return new RegulatoryReportSubmissionWorkflowService.AckNackResult(submissionId, false, "not-called");
                }
            },
            (reportType, reportingPeriod, reportBytes, submissionRef) -> "archive-unused",
            new RegulatoryReportSubmissionWorkflowService.AuditNotificationPort() {
                @Override
                public void notifySubmitted(String reportType, String reportingPeriod, String archiveRef) {
                }

                @Override
                public void notifyRejected(String reportType, String reportingPeriod, String reason) {
                }
            },
            new RegulatoryReportSubmissionWorkflowService.WorkflowInstancePort() {
                @Override
                public String startInstance(String workflowName, Map<String, Object> ctx) {
                    return "instance-2";
                }

                @Override
                public void completeStep(String instanceId, String stepId, Map<String, Object> output) {
                }

                @Override
                public void failStep(String instanceId, String stepId, String reason) {
                    failedSteps.add(stepId + ":" + reason);
                }
            },
            new SimpleMeterRegistry(),
            Runnable::run
        );

        RegulatoryReportSubmissionWorkflowService.ReportSubmissionRun run = runPromise(
            () -> service.submitReport("SEBON_DAILY_TRADE", "2026-04-07", 30_000L)
        );

        assertEquals("REJECTED_BY_CHECKER", run.status());
        assertEquals(0, run.submissionAttempts());
        assertEquals(0, submissionAttempts.get());
        assertEquals(List.of("MAKER_CHECKER:Rejected by checker"), failedSteps);
    }
}
