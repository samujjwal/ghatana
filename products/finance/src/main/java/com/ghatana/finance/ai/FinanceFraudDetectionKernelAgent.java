/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.ModelGovernanceService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kernel-orchestrated finance fraud detection entry point.
 *
 * @doc.type class
 * @doc.purpose Kernel agent facade for finance fraud detection
 * @doc.layer product
 * @doc.pattern Agent
 */
public class FinanceFraudDetectionKernelAgent implements AgentOrchestrator.KernelAgent {

    private static final String MODEL_ID = "fraud-detection-v2";

    private final ModelGovernanceService governance;
    private final FraudFeatureExtractor featureExtractor;
    private final FraudModelInferenceService inferenceService;

    public FinanceFraudDetectionKernelAgent(ModelGovernanceService governance) {
        this(governance, new ModelRepository());
    }

    public FinanceFraudDetectionKernelAgent(ModelGovernanceService governance,
                                            ModelRepository modelRepository) {
        this(governance, new FraudFeatureExtractor(), new DefaultFraudModelInferenceService(modelRepository));
    }

    public FinanceFraudDetectionKernelAgent(ModelGovernanceService governance,
                                            FraudFeatureExtractor featureExtractor,
                                            FraudModelInferenceService inferenceService) {
        this.governance = Objects.requireNonNull(governance, "governance cannot be null");
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor cannot be null");
        this.inferenceService = Objects.requireNonNull(inferenceService, "inferenceService cannot be null");
    }

    @Override
    public String getAgentId() {
        return "finance.fraud-detection";
    }

    @Override
    public String getName() {
        return "Finance Fraud Detection Agent";
    }

    @Override
    public String getDescription() {
        return "Detects fraudulent transactions using governed finance scoring";
    }

    @Override
    public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
        long startTime = System.currentTimeMillis();
        governance.validateModelUsage(MODEL_ID, request);

        @SuppressWarnings("unchecked")
        Map<String, Object> transactionData = (Map<String, Object>) request.getPayload();

        FraudDetectionResult result = runFraudDetection(transactionData, startTime);

        governance.recordModelPerformance(
            MODEL_ID,
            new ModelGovernanceService.ModelPerformanceMetrics(
                result.getConfidence(),
                result.getAccuracy(),
                result.getLatencyMs(),
                Map.of("fraud_score", result.getFraudScore())
            )
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("model_id", MODEL_ID);
        metadata.put("fraud_score", result.getFraudScore());
        metadata.put("risk_level", result.getRiskLevel());
        metadata.put("feature_count", result.getFeatures().size());
        metadata.put("inference_source", result.getInferenceSource());
        metadata.put("inference_latency_ms", result.getInferenceLatencyMs());
        metadata.put("explanation_summary", result.getExplanation().getSummary());
        metadata.put("explanation_primary_reason", result.getExplanation().getPrimaryReason());
        metadata.put(
            "explanation_top_factors",
            result.getExplanation().getTopFactors().stream().map(FraudDecisionExplanation.Factor::key).toList()
        );
        if (result.getModelVersion() != null) {
            metadata.put("model_version", result.getModelVersion());
        }
        if (result.getFraudType() != null) {
            metadata.put("fraud_type", result.getFraudType());
        }

        return AgentOrchestrator.AgentResponse.builder()
            .requestId(request.getRequestId())
            .success(true)
            .result(result)
            .confidence(result.getConfidence())
            .metadata(Map.copyOf(metadata))
            .build();
    }

    @Override
    public AgentOrchestrator.AgentCapabilities getCapabilities() {
        return new FraudDetectionCapabilities();
    }

    private FraudDetectionResult runFraudDetection(Map<String, Object> transactionData, long startTime) {
        String transactionId = String.valueOf(transactionData.getOrDefault("id", transactionData.getOrDefault("transaction_id", "unknown")));
        String accountId = String.valueOf(transactionData.getOrDefault("account_id", transactionData.getOrDefault("tenant_id", "unknown")));
        Map<String, Object> features = featureExtractor.extractTransactionFeatures(transactionData);
        FraudModelPrediction prediction = inferenceService.predict(MODEL_ID, features);

        return FraudDetectionResult.scored(
            transactionId,
            accountId,
            prediction.getFraudType(),
            prediction.getFraudScore(),
            prediction.getRiskLevel(),
            prediction.isFraudulent(),
            prediction.getConfidence(),
            prediction.getAccuracy(),
            features,
            System.currentTimeMillis() - startTime,
            prediction.getInferenceSource(),
            prediction.getModelVersion(),
            prediction.getLatencyMs()
        );
    }

    private static final class FraudDetectionCapabilities implements AgentOrchestrator.AgentCapabilities {
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
