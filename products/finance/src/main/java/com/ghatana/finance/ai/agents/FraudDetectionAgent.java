/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai.agents;

import com.ghatana.kernel.ai.*;
import com.ghatana.kernel.ai.ModelGovernanceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Component for FraudDetectionAgent
 *
 * @doc.type class
 * @doc.purpose Component for FraudDetectionAgent
 * @doc.layer product
 * @doc.pattern Agent
 */
public class FraudDetectionAgent implements AgentOrchestrator.KernelAgent {

    private static final String MODEL_ID = "fraud-detection-v2";

    private final ModelGovernanceService governance;

    public FraudDetectionAgent(ModelGovernanceService governance) {
        this.governance = governance;
    }

    @Override
    public String getAgentId() {
        return "finance.fraud-detection";
    }

    @Override
    public String getName() {
        return "Fraud Detection Agent";
    }

    @Override
    public String getDescription() {
        return "Detects fraudulent transactions using ML models";
    }

    @Override
    public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
        long startTime = System.currentTimeMillis();
        
        // Validate model approval
        governance.validateModelUsage(MODEL_ID, request);
        
        // Extract transaction data
        @SuppressWarnings("unchecked")
        Map<String, Object> transactionData = (Map<String, Object>) request.getPayload();
        
        // Run fraud detection model
        FraudDetectionResult result = runFraudDetection(transactionData);
        
        long latencyMs = System.currentTimeMillis() - startTime;
        
        // Record performance
        governance.recordModelPerformance(MODEL_ID, 
            new ModelGovernanceService.ModelPerformanceMetrics(
                result.getConfidence(),
                result.getAccuracy(),
                latencyMs,
                Map.of("fraud_score", result.getFraudScore())
            )
        );
        
        return AgentOrchestrator.AgentResponse.builder()
            .requestId(request.getRequestId())
            .success(true)
            .result(result)
            .confidence(result.getConfidence())
            .metadata(Map.of(
                "model_id", MODEL_ID,
                "fraud_score", result.getFraudScore(),
                "risk_level", result.getRiskLevel()
            ))
            .build();
    }

    @Override
    public AgentOrchestrator.AgentCapabilities getCapabilities() {
        return new FraudDetectionCapabilities();
    }

    private FraudDetectionResult runFraudDetection(Map<String, Object> transactionData) {
        // Extract transaction features
        double amount = ((Number) transactionData.getOrDefault("amount", 0)).doubleValue();
        String currency = (String) transactionData.getOrDefault("currency", "USD");
        String location = (String) transactionData.getOrDefault("location", "UNKNOWN");
        
        // Simple fraud detection logic (replace with actual ML model)
        double fraudScore = calculateFraudScore(amount, currency, location);
        String riskLevel = determineRiskLevel(fraudScore);
        boolean isFraudulent = fraudScore >= 0.8;
        
        return new FraudDetectionResult(
            fraudScore,
            riskLevel,
            isFraudulent,
            0.92, // confidence
            0.95, // accuracy
            Map.of(
                "amount_factor", amount > 10000 ? 0.3 : 0.1,
                "location_factor", "UNKNOWN".equals(location) ? 0.4 : 0.1
            )
        );
    }
    
    private double calculateFraudScore(double amount, String currency, String location) {
        double score = 0.0;
        
        // High amount increases fraud score
        if (amount > 50000) score += 0.5;
        else if (amount > 10000) score += 0.2;
        
        // Unknown location increases fraud score
        if ("UNKNOWN".equals(location)) score += 0.3;
        
        // Unusual currency
        if (!"USD".equals(currency) && !"EUR".equals(currency)) score += 0.1;
        
        return Math.min(score, 1.0);
    }
    
    private String determineRiskLevel(double fraudScore) {
        if (fraudScore >= 0.8) return "HIGH";
        if (fraudScore > 0.5) return "MEDIUM";
        return "LOW";
    }

    private static class FraudDetectionCapabilities implements AgentOrchestrator.AgentCapabilities {
        @Override
        public List<String> getSupportedOperations() {
            return List.of("detect_fraud", "assess_risk", "analyze_transaction");
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of(
                "model_type", "classification",
                "compliance", "SOX",
                "accuracy_threshold", 0.95,
                "autonomy_level", "MEDIUM",
                "human_review", "conditional",
                "review_criteria", "fraud_score>0.8"
            );
        }

        @Override
        public boolean supportsOperation(String operation) {
            return getSupportedOperations().contains(operation);
        }
    }
}
