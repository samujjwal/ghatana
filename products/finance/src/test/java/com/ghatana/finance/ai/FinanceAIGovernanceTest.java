/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.*;
import io.activej.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Finance AI Governance
 */
public class FinanceAIGovernanceTest {

    private ModelGovernanceService governance;
    private AgentOrchestrator orchestrator;
    private AutonomyManager autonomyManager;
    private FinanceFraudDetectionKernelAgent fraudDetectionAgent;
    private ModelApprovalRepository approvalRepository;
    private ModelRepository modelRepository;

    @BeforeEach
    public void setUp() {
        // Create ActiveJ injector with Finance AI module
        Injector injector = Injector.of(FinanceAIModule.create());

        // Get dependencies from injector
        governance = injector.getInstance(ModelGovernanceService.class);
        orchestrator = injector.getInstance(AgentOrchestrator.class);
        autonomyManager = injector.getInstance(AutonomyManager.class);
        approvalRepository = injector.getInstance(ModelApprovalRepository.class);
        modelRepository = injector.getInstance(ModelRepository.class);

        // Create fraud detection agent with injector-managed inference wiring
        fraudDetectionAgent = injector.getInstance(FinanceFraudDetectionKernelAgent.class);
        
        // Register fraud detection agent
        orchestrator.registerAgent(fraudDetectionAgent);
        
        // Set up approved model
        ModelApprovalRecord approval = new ModelApprovalRecord();
        approval.setModelId("fraud-detection-v2");
        approval.setApproved(true);
        approval.setApprover("system");
        approval.setApprovalDate(Instant.now());
        approval.setVersion("2.0");
        approval.setConditions(Map.of(
            "approved_operations", List.of("detect_fraud", "assess_risk", "analyze_transaction")
        ));
        approvalRepository.save(approval);

        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("prediction_endpoint", "http://127.0.0.1:1/unreachable"));
        modelRepository.save(model);
    }

    @Test
    public void testModelApproval_ApprovedModel_ShouldAllow() {
        ModelGovernanceService.ModelApproval approval = governance.getModelApproval("fraud-detection-v2");
        
        assertNotNull(approval);
        assertTrue(approval.isApproved());
        assertEquals("system", approval.getApprover());
        assertEquals("2.0", approval.getVersion());
    }

    @Test
    public void testModelUsage_UnapprovedModel_ShouldThrow() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", 
            "detect_fraud", 
            Map.of("amount", 1000), 
            Map.of()
        );
        
        assertThrows(ModelNotApprovedException.class, () -> {
            governance.validateModelUsage("unapproved-model", request);
        });
    }
    
    @Test
    public void testModelUsage_ApprovedModel_ShouldSucceed() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-1", 
            "detect_fraud", 
            Map.of("amount", 1000), 
            Map.of()
        );
        
        assertDoesNotThrow(() -> {
            governance.validateModelUsage("fraud-detection-v2", request);
        });
    }

    @Test
    public void testFraudDetection_LowRiskTransaction_ShouldApprove() {
        Map<String, Object> transactionData = Map.of(
            "id", "txn-001",
            "amount", 100.0,
            "currency", "USD",
            "location", "NEW_YORK"
        );
        
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            UUID.randomUUID().toString(),
            "detect_fraud",
            transactionData,
            Map.of("tenant_id", "tenant-1")
        );
        
        AgentOrchestrator.KernelAgent agent = orchestrator.getAgent("finance.fraud-detection");
        assertNotNull(agent);
        
        AgentOrchestrator.AgentResponse response = agent.execute(request);
        
        assertTrue(response.isSuccess());
        assertNotNull(response.getResult());
        
        FraudDetectionResult result = (FraudDetectionResult) response.getResult();
        assertFalse(result.isFraudulent());
        assertEquals("LOW", result.getRiskLevel());
    }
    
    @Test
    public void testFraudDetection_HighRiskTransaction_ShouldDetect() {
        Map<String, Object> transactionData = Map.of(
            "id", "txn-002",
            "amount", 100000.0,
            "currency", "BTC",
            "location", "UNKNOWN"
        );
        
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            UUID.randomUUID().toString(),
            "detect_fraud",
            transactionData,
            Map.of("tenant_id", "tenant-1")
        );
        
        AgentOrchestrator.KernelAgent agent = orchestrator.getAgent("finance.fraud-detection");
        AgentOrchestrator.AgentResponse response = agent.execute(request);
        
        assertTrue(response.isSuccess());
        
        FraudDetectionResult result = (FraudDetectionResult) response.getResult();
        assertTrue(result.isFraudulent());
        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.getFraudScore() > 0.8);
    }
    
    @Test
    public void testAutonomyManager_HighValueTransaction_RequiresReview() {
        Map<String, Object> transactionData = Map.of(
            "id", "txn-003",
            "amount", 150000.0,
            "currency", "USD"
        );
        
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            UUID.randomUUID().toString(),
            "detect_fraud",
            transactionData,
            Map.of("tenant_id", "tenant-1", "amount", 150000.0)
        );
        
        AgentOrchestrator.KernelAgent agent = orchestrator.getAgent("finance.fraud-detection");
        
        boolean requiresReview = autonomyManager.requiresHumanReview(request, agent);
        assertTrue(requiresReview, "High-value transactions should require human review");
    }
    
    @Test
    public void testAutonomyManager_LowValueTransaction_NoReviewRequired() {
        Map<String, Object> transactionData = Map.of(
            "id", "txn-004",
            "amount", 500.0,
            "currency", "USD"
        );
        
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            UUID.randomUUID().toString(),
            "detect_fraud",
            transactionData,
            Map.of("tenant_id", "tenant-1", "amount", 500.0)
        );
        
        AgentOrchestrator.KernelAgent agent = orchestrator.getAgent("finance.fraud-detection");
        
        boolean requiresReview = autonomyManager.requiresHumanReview(request, agent);
        assertFalse(requiresReview, "Low-value transactions should not require human review");
    }
    
    @Test
    public void testModelPerformanceRecording() {
        ModelGovernanceService.ModelPerformanceMetrics metrics = 
            new ModelGovernanceService.ModelPerformanceMetrics(
                0.92,  // confidence
                0.96,  // accuracy
                45L,   // latency
                Map.of("test_run", 1.0)
            );
        
        assertDoesNotThrow(() -> {
            governance.recordModelPerformance("fraud-detection-v2", metrics);
        });
    }
    
    @Test
    public void testModelRegistration() {
        ModelGovernanceService.ModelRegistration registration = 
            new ModelGovernanceService.ModelRegistration(
                "new-fraud-model-v3",
                "Advanced Fraud Detection Model",
                "3.0",
                "classification",
                Map.of("sox_compliant", true, "accuracy", 0.97)
            );
        
        assertDoesNotThrow(() -> {
            governance.registerModel(registration);
        });
        
        ModelGovernanceService.ModelMetadata metadata = governance.getModelMetadata("new-fraud-model-v3");
        assertNotNull(metadata);
        assertEquals("new-fraud-model-v3", metadata.getModelId());
        assertEquals("Advanced Fraud Detection Model", metadata.getName());
        assertEquals("classification", modelRepository.findByModelId("new-fraud-model-v3").getType());
    }
    
    @Test
    public void testSOXComplianceValidation() {
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            "req-sox",
            "financial_reporting",
            Map.of("report_type", "quarterly"),
            Map.of()
        );
        
        // Should throw because model lacks SOX compliance for financial operations
        assertThrows(ModelNotApprovedException.class, () -> {
            governance.validateModelUsage("fraud-detection-v2", request);
        });
    }
}
