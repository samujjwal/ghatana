/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.ModelGovernanceService;
import io.activej.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Finance model governance.
 *
 * <p>Covers the governance invariants not addressed by {@link FinanceModelGovernanceImplTest}:
 * <ul>
 *   <li>Multi-version model registration and selective validation.</li>
 *   <li>Operation-level approval gating (approved for A, rejected for B).</li>
 *   <li>Performance metrics persistence and alert triggering.</li>
 *   <li>Compliance policy evaluation against registered model metadata.</li>
 *   <li>Concurrent validation calls do not corrupt governance state.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Finance model governance integration contract validation
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Finance Model Governance — Integration")
class FinanceModelGovernanceIntegrationTest {

    private ModelGovernanceService governance;
    private ModelApprovalRepository approvalRepository;
    private ModelPerformanceRepository performanceRepository;
    private ModelRepository modelRepository;
    private CapturingAlertService alertService;

    @BeforeEach
    void setUp() {
        approvalRepository = new ModelApprovalRepository();
        performanceRepository = new ModelPerformanceRepository();
        modelRepository = new ModelRepository();
        alertService = new CapturingAlertService();
        governance = new FinanceModelGovernanceImpl(
            approvalRepository,
            performanceRepository,
            modelRepository,
            alertService
        );
    }

    // -----------------------------------------------------------------------
    // Version tracking
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("model versioning tracks v1 and v2 registrations independently")
    void modelVersioningTracksV1AndV2() {
        governance.registerModel(new ModelGovernanceService.ModelRegistration(
            "risk-model-v1", "Risk Model", "1.0.0", "risk", Map.of("jurisdiction", "np")));
        governance.registerModel(new ModelGovernanceService.ModelRegistration(
            "risk-model-v2", "Risk Model", "2.0.0", "risk", Map.of("jurisdiction", "np", "region", "apac")));

        ModelGovernanceService.ModelMetadata v1 = governance.getModelMetadata("risk-model-v1");
        ModelGovernanceService.ModelMetadata v2 = governance.getModelMetadata("risk-model-v2");

        assertNotNull(v1);
        assertNotNull(v2);
        assertEquals("1.0.0", v1.getVersion());
        assertEquals("2.0.0", v2.getVersion());
    }

    @Test
    @DisplayName("unapproved model id returns null approval")
    void unapprovedModelReturnsNullApproval() {
        ModelGovernanceService.ModelApproval approval = governance.getModelApproval("never-registered");
        assertNull(approval);
    }

    @Test
    @DisplayName("approved model approval record contains correct metadata")
    void approvedModelApprovalRecordContainsCorrectMetadata() {
        ModelApprovalRecord record = buildApproval("classify-v1", true, "compliance-team",
            List.of("classify", "score"));
        approvalRepository.save(record);

        ModelGovernanceService.ModelApproval approval = governance.getModelApproval("classify-v1");

        assertNotNull(approval);
        assertTrue(approval.isApproved());
        assertEquals("compliance-team", approval.getApprover());
        assertEquals("1.0", approval.getVersion());
    }

    // -----------------------------------------------------------------------
    // Operation-level approval gating
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("model approved for detect_fraud only rejects assess_risk operation")
    void unapprovedOperationIsRejected() {
        ModelApprovalRecord record = buildApproval("fraud-only-model", true, "risk-desk",
            List.of("detect_fraud"));
        approvalRepository.save(record);

        AgentOrchestrator.AgentRequest allowedRequest = new AgentOrchestrator.AgentRequest(
            "req-ok", "detect_fraud", Map.of(), Map.of());
        AgentOrchestrator.AgentRequest rejectedRequest = new AgentOrchestrator.AgentRequest(
            "req-bad", "assess_risk", Map.of(), Map.of());

        assertDoesNotThrow(() ->
            governance.validateModelUsage("fraud-only-model", allowedRequest));
        assertThrows(ModelNotApprovedException.class, () ->
            governance.validateModelUsage("fraud-only-model", rejectedRequest));
    }

    @Test
    @DisplayName("model with no approved_operations condition accepts all operations")
    void modelWithNoOperationConditionAcceptsAnyOperation() {
        ModelApprovalRecord record = buildApprovalNoOps("open-model", true, "auto-approver");
        approvalRepository.save(record);

        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-any", "any_operation", Map.of(), Map.of());

        assertDoesNotThrow(() -> governance.validateModelUsage("open-model", request));
    }

    @Test
    @DisplayName("completely unapproved model is rejected for any operation")
    void completelyUnapprovedModelIsRejected() {
        ModelApprovalRecord record = buildApproval("unapproved-x", false, "nobody", List.of());
        approvalRepository.save(record);

        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", "detect_fraud", Map.of(), Map.of());

        assertThrows(ModelNotApprovedException.class,
            () -> governance.validateModelUsage("unapproved-x", request));
    }

    // -----------------------------------------------------------------------
    // Performance monitoring
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("recordModelPerformance persists metrics in repository")
    void performanceMonitoringPersistsMetrics() {
        governance.recordModelPerformance(
            "analytics-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.92, 0.88, 800L, Map.of())
        );

        List<ModelPerformanceRecord> records = performanceRepository.findByModelId("analytics-v1");

        assertEquals(1, records.size());
        assertEquals("analytics-v1", records.get(0).getModelId());
    }

    @Test
    @DisplayName("recordModelPerformance triggers alert when confidence drops below 0.70")
    void lowConfidenceTriggersAlert() {
        governance.recordModelPerformance(
            "degraded-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.55, 0.88, 800L, Map.of())
        );

        assertEquals(1, alertService.alertCount);
        assertTrue(alertService.lastMessage.contains("degraded-v1"));
    }

    @Test
    @DisplayName("recordModelPerformance triggers alert when accuracy drops below 0.80")
    void lowAccuracyTriggersAlert() {
        governance.recordModelPerformance(
            "low-accuracy-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.80, 0.72, 800L, Map.of())
        );

        assertEquals(1, alertService.alertCount);
        assertTrue(alertService.lastMessage.contains("low-accuracy-v1"));
    }

    @Test
    @DisplayName("recordModelPerformance triggers alert when latency exceeds 2500ms")
    void highLatencyTriggersAlert() {
        governance.recordModelPerformance(
            "slow-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.85, 0.90, 3_500L, Map.of())
        );

        assertEquals(1, alertService.alertCount);
        assertTrue(alertService.lastMessage.contains("slow-v1"));
    }

    @Test
    @DisplayName("recordModelPerformance does not alert when all metrics are within bounds")
    void healthyMetricsDoNotTriggerAlert() {
        governance.recordModelPerformance(
            "healthy-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.90, 0.88, 1_200L, Map.of())
        );

        assertEquals(0, alertService.alertCount);
    }

    // -----------------------------------------------------------------------
    // Compliance policy evaluation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isModelCompliant returns true when jurisdiction policy matches model metadata")
    void compliancePolicyMatchingJurisdiction() {
        governance.registerModel(new ModelGovernanceService.ModelRegistration(
            "nepal-model", "Nepal Risk Model", "1.0.0", "risk",
            Map.of("jurisdiction", "np")
        ));

        assertTrue(governance.isModelCompliant("nepal-model", new JurisdictionPolicy("np")));
        assertFalse(governance.isModelCompliant("nepal-model", new JurisdictionPolicy("us")));
    }

    @Test
    @DisplayName("isModelCompliant returns false for unknown model id")
    void compliancePolicyReturnsFalseForUnknownModel() {
        assertFalse(governance.isModelCompliant("ghost-model", new JurisdictionPolicy("np")));
    }

    // -----------------------------------------------------------------------
    // Concurrent validation (thread safety)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("concurrent validateModelUsage calls do not corrupt approval state")
    void concurrentValidationsAreThreadSafe() throws InterruptedException {
        // Seed an approved model
        ModelApprovalRecord record = buildApproval("concurrent-model", true, "ops-team",
            List.of("detect_fraud"));
        approvalRepository.save(record);

        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
                        "req-t", "detect_fraud", Map.of(), Map.of());
                    governance.validateModelUsage("concurrent-model", request);
                } catch (ModelNotApprovedException e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Timed out waiting for concurrent calls");
        pool.shutdownNow();

        assertTrue(errors.isEmpty(),
            "Expected no ModelNotApprovedException but got: " + errors.size() + " errors");
    }

    // -----------------------------------------------------------------------
    // DI wiring (FinanceAIModule)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FinanceAIModule wires ModelGovernanceService via injector")
    void financeAiModuleWiresGovernance() {
        Injector injector = Injector.of(FinanceAIModule.create());

        ModelGovernanceService injectedGovernance = injector.getInstance(ModelGovernanceService.class);

        assertNotNull(injectedGovernance);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ModelApprovalRecord buildApproval(
            String modelId, boolean approved, String approver, List<String> ops) {
        ModelApprovalRecord record = new ModelApprovalRecord();
        record.setModelId(modelId);
        record.setApproved(approved);
        record.setApprover(approver);
        record.setApprovalDate(Instant.now());
        record.setVersion("1.0");
        record.setConditions(Map.of("approved_operations", ops));
        return record;
    }

    private static ModelApprovalRecord buildApprovalNoOps(
            String modelId, boolean approved, String approver) {
        ModelApprovalRecord record = new ModelApprovalRecord();
        record.setModelId(modelId);
        record.setApproved(approved);
        record.setApprover(approver);
        record.setApprovalDate(Instant.now());
        record.setVersion("1.0");
        record.setConditions(Map.of()); // no approved_operations key
        return record;
    }

    private static final class CapturingAlertService extends AlertService {
        int alertCount;
        String lastMessage = "";

        @Override
        public void sendAlert(String title, String message) {
            alertCount++;
            lastMessage = title + ":" + message;
        }
    }

    private record JurisdictionPolicy(String expectedJurisdiction)
            implements ModelGovernanceService.CompliancePolicy {

        @Override
        public String getPolicyId() {
            return "jurisdiction";
        }

        @Override
        public String getName() {
            return "Jurisdiction policy";
        }

        @Override
        public boolean evaluate(ModelGovernanceService.ModelMetadata metadata) {
            return expectedJurisdiction.equals(metadata.getAttributes().get("jurisdiction"));
        }

        @Override
        public List<String> getRequirements() {
            return List.of("jurisdiction=" + expectedJurisdiction);
        }
    }
}
