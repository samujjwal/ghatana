/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.ModelGovernanceService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finance implementation of the ModelGovernanceService.
 *
 * @doc.type class
 * @doc.purpose Finance model governance using approval repository
 * @doc.layer product
 * @doc.pattern Service
 */
public class FinanceModelGovernanceImpl implements ModelGovernanceService {

    private static final double MIN_CONFIDENCE = 0.70;
    private static final double MIN_ACCURACY = 0.80;
    private static final long MAX_LATENCY_MILLIS = 2_500L;

    private final ModelApprovalRepository approvalRepository;
    private final ModelPerformanceRepository performanceRepository;
    private final AlertService alertService;
    private final Map<String, ModelRecord> modelRegistry = new ConcurrentHashMap<>();

    public FinanceModelGovernanceImpl(
            ModelApprovalRepository approvalRepository,
            ModelPerformanceRepository performanceRepository,
            AlertService alertService) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository cannot be null");
        this.performanceRepository = Objects.requireNonNull(performanceRepository, "performanceRepository cannot be null");
        this.alertService = Objects.requireNonNull(alertService, "alertService cannot be null");
    }

    @Override
    public ModelApproval getModelApproval(String modelId) {
        ModelApprovalRecord record = approvalRepository.findByModelId(modelId);
        if (record == null) {
            return null;
        }
        return ModelApproval.builder()
            .modelId(record.getModelId())
            .approved(record.isApproved())
            .approver(record.getApprover())
            .approvalDate(record.getApprovalDate() != null ? record.getApprovalDate().toEpochMilli() : 0L)
            .version(record.getVersion())
            .conditions(record.getConditions() != null ? record.getConditions() : Map.of())
            .build();
    }

    @Override
    public void validateModelUsage(String modelId, AgentOrchestrator.AgentRequest request) {
        ModelApprovalRecord record = approvalRepository.findByModelId(modelId);
        if (record == null || !record.isApproved()) {
            throw new com.ghatana.finance.ai.ModelNotApprovedException("Model not approved for use: " + modelId);
        }
        // Check operation-level approval if conditions specify allowed operations
        if (record.getConditions() != null && record.getConditions().containsKey("approved_operations")) {
            @SuppressWarnings("unchecked")
            java.util.List<String> approvedOps = (java.util.List<String>) record.getConditions().get("approved_operations");
            String operation = request.getOperation();
            if (!approvedOps.contains(operation)) {
                throw new com.ghatana.finance.ai.ModelNotApprovedException(
                    "Model '" + modelId + "' is not approved for operation: " + operation);
            }
        }
    }

    @Override
    public void recordModelPerformance(String modelId, ModelPerformanceMetrics metrics) {
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");

        ModelPerformanceRecord record = new ModelPerformanceRecord();
        record.setModelId(modelId);
        record.setConfidence(metrics.getConfidence());
        record.setAccuracy(metrics.getAccuracy());
        record.setLatency(metrics.getLatencyMillis());
        record.setTimestamp(Instant.now());
        performanceRepository.save(record);

        List<String> degradations = new java.util.ArrayList<>();
        if (metrics.getConfidence() < MIN_CONFIDENCE) {
            degradations.add("confidence=" + metrics.getConfidence());
        }
        if (metrics.getAccuracy() < MIN_ACCURACY) {
            degradations.add("accuracy=" + metrics.getAccuracy());
        }
        if (metrics.getLatencyMillis() > MAX_LATENCY_MILLIS) {
            degradations.add("latencyMillis=" + metrics.getLatencyMillis());
        }

        if (!degradations.isEmpty()) {
            alertService.sendAlert(
                "Model performance degradation",
                "Model " + modelId + " breached thresholds: " + String.join(", ", degradations)
            );
        }
    }

    @Override
    public boolean isModelCompliant(String modelId, CompliancePolicy policy) {
        ModelMetadata metadata = getModelMetadata(modelId);
        return metadata != null && policy.evaluate(metadata);
    }

    @Override
    public void registerModel(ModelRegistration model) {
        ModelRecord record = new ModelRecord();
        record.setModelId(model.getModelId());
        record.setName(model.getName());
        record.setVersion(model.getVersion());
        record.setType(model.getType());
        record.setMetadata(model.getMetadata());
        modelRegistry.put(model.getModelId(), record);
    }

    @Override
    public ModelMetadata getModelMetadata(String modelId) {
        ModelRecord record = modelRegistry.get(modelId);
        if (record == null) {
            return null;
        }
        return new FinanceModelMetadata(record);
    }
}
