package com.ghatana.orchestrator.models;

import java.time.Instant;

public class OrchestratorPipelineState {
    public String pipelineId;
    public String executionId;
    public String status; // PENDING, RUNNING, COMPLETED, FAILED, PAUSED, CANCELLED
    public Instant startTime;
    public Instant endTime;
    public String currentStep;
    public int progress; // 0-100
    public String errorMessage;
    public String inputData;  // JSON string
    public String outputData; // JSON string
    public String contextData; // JSON string for additional context
    public String createdBy;
    public String tenantId = "default";
    public Instant createdAt;
    public Instant updatedAt;
}
