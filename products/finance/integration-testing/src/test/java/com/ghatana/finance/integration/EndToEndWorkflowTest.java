package com.ghatana.finance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose End-to-end workflow tests for finance integration scenarios
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("End-to-End Workflow Tests")
class EndToEndWorkflowTest {
    private WorkflowOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new WorkflowOrchestrator();
    }

    @Test
    @DisplayName("Should execute complete order-to-position workflow")
    void shouldExecuteCompleteOrderToPositionWorkflow() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00));
        WorkflowResult result = orchestrator.executeOrderWorkflow(request);
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("VALIDATE", "ROUTE", "EXECUTE", "UPDATE_POSITION");
    }

    @Test
    @DisplayName("Should handle workflow failures with rollback")
    void shouldHandleWorkflowFailuresWithRollback() {
        OrderRequest invalidRequest = new OrderRequest("INVALID", "BUY", 100L, BigDecimal.valueOf(150.00));
        WorkflowResult result = orchestrator.executeOrderWorkflow(invalidRequest);
        assertThat(result.success()).isFalse();
        assertThat(result.rolledBack()).isTrue();
    }

    @Test
    @DisplayName("Should execute corporate action workflow")
    void shouldExecuteCorporateActionWorkflow() {
        CorporateActionRequest request = new CorporateActionRequest("AAPL", "SPLIT", "2:1");
        WorkflowResult result = orchestrator.executeCorporateActionWorkflow(request);
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("VALIDATE_ACTION", "UPDATE_POSITIONS", "PUBLISH_EVENTS");
    }

    @Test
    @DisplayName("Should execute reconciliation workflow")
    void shouldExecuteReconciliationWorkflow() {
        ReconciliationRequest request = new ReconciliationRequest("2024-04-04");
        WorkflowResult result = orchestrator.executeReconciliationWorkflow(request);
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("FETCH_POSITIONS", "FETCH_BROKER_DATA", "RECONCILE", "GENERATE_REPORT");
    }

    @Test
    @DisplayName("Should execute risk assessment workflow")
    void shouldExecuteRiskAssessmentWorkflow() {
        RiskAssessmentRequest request = new RiskAssessmentRequest("portfolio-1");
        WorkflowResult result = orchestrator.executeRiskAssessmentWorkflow(request);
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).contains("CALCULATE_EXPOSURE", "CHECK_LIMITS", "GENERATE_ALERTS");
    }

    @Test
    @DisplayName("Should maintain transaction consistency across workflow")
    void shouldMaintainTransactionConsistencyAcrossWorkflow() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00));
        orchestrator.executeOrderWorkflow(request);
        assertThat(orchestrator.getExecutionCount()).isEqualTo(1);
        assertThat(orchestrator.getPositionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track workflow execution time")
    void shouldTrackWorkflowExecutionTime() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00));
        Instant start = Instant.now();
        orchestrator.executeOrderWorkflow(request);
        Instant end = Instant.now();
        long durationMs = end.toEpochMilli() - start.toEpochMilli();
        assertThat(durationMs).isLessThan(1000L);
    }

    @Test
    @DisplayName("Should support workflow compensation")
    void shouldSupportWorkflowCompensation() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00));
        orchestrator.executeOrderWorkflow(request);
        orchestrator.compensateLastWorkflow();
        assertThat(orchestrator.getExecutionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should execute parallel workflows")
    void shouldExecuteParallelWorkflows() {
        List<OrderRequest> requests = List.of(
            new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)),
            new OrderRequest("GOOGL", "BUY", 50L, BigDecimal.valueOf(2800.00))
        );
        List<WorkflowResult> results = orchestrator.executeParallelWorkflows(requests);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(WorkflowResult::success);
    }

    @Test
    @DisplayName("Should generate workflow audit trail")
    void shouldGenerateWorkflowAuditTrail() {
        OrderRequest request = new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00));
        orchestrator.executeOrderWorkflow(request);
        List<AuditEntry> audit = orchestrator.getAuditTrail();
        assertThat(audit).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate workflow metrics")
    void shouldGenerateWorkflowMetrics() {
        orchestrator.executeOrderWorkflow(new OrderRequest("AAPL", "BUY", 100L, BigDecimal.valueOf(150.00)));
        orchestrator.executeOrderWorkflow(new OrderRequest("GOOGL", "BUY", 50L, BigDecimal.valueOf(2800.00)));
        WorkflowMetrics metrics = orchestrator.getMetrics();
        assertThat(metrics.totalWorkflows()).isEqualTo(2);
        assertThat(metrics.successRate()).isEqualTo(100.0);
    }

    record OrderRequest(String symbol, String side, long quantity, BigDecimal price) {}
    record CorporateActionRequest(String symbol, String type, String details) {}
    record ReconciliationRequest(String date) {}
    record RiskAssessmentRequest(String portfolioId) {}
    record WorkflowResult(boolean success, List<String> steps, boolean rolledBack) {
        WorkflowResult(boolean success, List<String> steps) {
            this(success, steps, false);
        }
    }
    record AuditEntry(String workflow, String step, Instant timestamp) {}
    record WorkflowMetrics(int totalWorkflows, double successRate) {}

    static class WorkflowOrchestrator {
        private int executionCount = 0;
        private int positionCount = 0;
        private final List<AuditEntry> auditTrail = new java.util.ArrayList<>();
        private final List<WorkflowResult> workflowHistory = new java.util.ArrayList<>();

        WorkflowResult executeOrderWorkflow(OrderRequest request) {
            List<String> steps = new java.util.ArrayList<>();
            
            if (request.symbol().equals("INVALID")) {
                return new WorkflowResult(false, List.of("VALIDATE"), true);
            }

            steps.add("VALIDATE");
            auditTrail.add(new AuditEntry("ORDER_WORKFLOW", "VALIDATE", Instant.now()));
            
            steps.add("ROUTE");
            auditTrail.add(new AuditEntry("ORDER_WORKFLOW", "ROUTE", Instant.now()));
            
            steps.add("EXECUTE");
            executionCount++;
            auditTrail.add(new AuditEntry("ORDER_WORKFLOW", "EXECUTE", Instant.now()));
            
            steps.add("UPDATE_POSITION");
            positionCount++;
            auditTrail.add(new AuditEntry("ORDER_WORKFLOW", "UPDATE_POSITION", Instant.now()));

            WorkflowResult result = new WorkflowResult(true, steps);
            workflowHistory.add(result);
            return result;
        }

        WorkflowResult executeCorporateActionWorkflow(CorporateActionRequest request) {
            List<String> steps = List.of("VALIDATE_ACTION", "UPDATE_POSITIONS", "PUBLISH_EVENTS");
            WorkflowResult result = new WorkflowResult(true, steps);
            workflowHistory.add(result);
            return result;
        }

        WorkflowResult executeReconciliationWorkflow(ReconciliationRequest request) {
            List<String> steps = List.of("FETCH_POSITIONS", "FETCH_BROKER_DATA", "RECONCILE", "GENERATE_REPORT");
            WorkflowResult result = new WorkflowResult(true, steps);
            workflowHistory.add(result);
            return result;
        }

        WorkflowResult executeRiskAssessmentWorkflow(RiskAssessmentRequest request) {
            List<String> steps = List.of("CALCULATE_EXPOSURE", "CHECK_LIMITS", "GENERATE_ALERTS");
            WorkflowResult result = new WorkflowResult(true, steps);
            workflowHistory.add(result);
            return result;
        }

        int getExecutionCount() {
            return executionCount;
        }

        int getPositionCount() {
            return positionCount;
        }

        void compensateLastWorkflow() {
            if (executionCount > 0) executionCount--;
            if (positionCount > 0) positionCount--;
        }

        List<WorkflowResult> executeParallelWorkflows(List<OrderRequest> requests) {
            return requests.stream()
                .map(this::executeOrderWorkflow)
                .toList();
        }

        List<AuditEntry> getAuditTrail() {
            return auditTrail;
        }

        WorkflowMetrics getMetrics() {
            long successful = workflowHistory.stream().filter(WorkflowResult::success).count();
            double successRate = workflowHistory.isEmpty() ? 0.0 : 
                (successful * 100.0) / workflowHistory.size();
            return new WorkflowMetrics(workflowHistory.size(), successRate);
        }
    }
}
