package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.ModelGovernanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies kernel fraud agent execution and governance integration
 * @doc.layer product
 * @doc.pattern Test
 */
class FinanceFraudDetectionKernelAgentTest {

    private ModelApprovalRepository approvalRepository;
    private ModelPerformanceRepository performanceRepository;
    private ModelRepository modelRepository;
    private FinanceFraudDetectionKernelAgent agent;

    @BeforeEach
    void setUp() {
        approvalRepository = new ModelApprovalRepository();
        performanceRepository = new ModelPerformanceRepository();
        modelRepository = new ModelRepository();

        FinanceModelGovernanceImpl governance = new FinanceModelGovernanceImpl(
            approvalRepository,
            performanceRepository,
            modelRepository,
            new AlertService()
        );

        ModelApprovalRecord approval = new ModelApprovalRecord();
        approval.setModelId("fraud-detection-v2");
        approval.setApproved(true);
        approval.setApprover("qa");
        approval.setApprovalDate(Instant.now());
        approval.setVersion("2.0");
        approval.setConditions(Map.of(
            "approved_operations", List.of("detect_fraud", "assess_risk", "analyze_transaction")
        ));
        approvalRepository.save(approval);

        agent = new FinanceFraudDetectionKernelAgent(governance, modelRepository);
    }

    @Test
    void executesFraudCheckAndRecordsPerformance() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1",
            "detect_fraud",
            Map.of(
                "id", "txn-1",
                "tenant_id", "tenant-1",
                "amount", 60000.0,
                "currency", "BTC",
                "location", "US-NY",
                "merchant_category", "CRYPTO_EXCHANGE",
                "counterparty_country", "RU",
                "payment_method", "WIRE_TRANSFER",
                "velocity", 12.0,
                "timestamp", "2026-04-06T20:15:30Z"
            ),
            Map.of("tenant_id", "tenant-1")
        );

        AgentOrchestrator.AgentResponse response = agent.execute(request);

        assertTrue(response.isSuccess());
        assertEquals("finance.fraud-detection", agent.getAgentId());
        assertEquals("Finance Fraud Detection Agent", agent.getName());
        assertEquals("req-1", response.getRequestId());

        FraudDetectionResult result = (FraudDetectionResult) response.getResult();
        assertEquals("txn-1", result.getTradeId());
        assertEquals("tenant-1", result.getAccountId());
        assertTrue(result.isFraudulent());
        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.getLatencyMs() >= 0L);
        assertEquals("FALLBACK", result.getInferenceSource());
        assertEquals(0L, result.getInferenceLatencyMs());
        assertEquals(18, result.getFeatures().size());
        assertEquals("BTC", result.getFeatures().get("currency"));
        assertEquals("RU", result.getFeatures().get("counterparty_country"));
        assertNotNull(result.getExplanation());
        assertTrue(result.getExplanation().getSummary().contains("HIGH fraud risk"));
        assertFalse(result.getExplanation().getTopFactors().isEmpty());

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("fraud-detection-v2", metadata.get("model_id"));
        assertEquals("HIGH", metadata.get("risk_level"));
        assertEquals(18, metadata.get("feature_count"));
        assertEquals("FALLBACK", metadata.get("inference_source"));
        assertEquals(0L, metadata.get("inference_latency_ms"));
        assertEquals(result.getFraudType(), metadata.get("fraud_type"));
        assertEquals(result.getExplanation().getSummary(), metadata.get("explanation_summary"));
        assertEquals(result.getExplanation().getPrimaryReason(), metadata.get("explanation_primary_reason"));
        assertEquals(
            result.getExplanation().getTopFactors().stream().map(FraudDecisionExplanation.Factor::key).toList(),
            metadata.get("explanation_top_factors")
        );

        assertEquals(1, performanceRepository.findByModelId("fraud-detection-v2").size());
        assertEquals(result.getConfidence(), performanceRepository.findByModelId("fraud-detection-v2").get(0).getConfidence());
    }

    @Test
    void exposesCapabilitiesMetadata() {
        AgentOrchestrator.AgentCapabilities capabilities = agent.getCapabilities();

        assertTrue(capabilities.supportsOperation("detect_fraud"));
        assertFalse(capabilities.supportsOperation("settle_trade"));
        assertEquals(List.of("detect_fraud", "assess_risk", "analyze_transaction"), capabilities.getSupportedOperations());
        assertEquals("SOX", capabilities.getMetadata().get("compliance"));
    }

    @Test
    void rejectsUnapprovedOperation() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-2",
            "financial_reporting",
            Map.of("amount", 1000.0),
            Map.of()
        );

        assertThrows(ModelNotApprovedException.class, () -> agent.execute(request));
    }

    @Test
    void defaultsMissingTransactionFields() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-3",
            "detect_fraud",
            Map.of("amount", 50.0),
            Map.of()
        );

        FraudDetectionResult result = (FraudDetectionResult) agent.execute(request).getResult();

        assertEquals("unknown", result.getTradeId());
        assertEquals("unknown", result.getAccountId());
        assertNull(result.getFraudType());
        assertFalse(result.isFraudulent());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals("FALLBACK", result.getInferenceSource());
        assertEquals("balanced transaction signals", result.getExplanation().getPrimaryReason());
    }
}