/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for workflow information.
 *
 * @doc.type record
 * @doc.purpose Workflow response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record WorkflowResponse(
    @JsonProperty("id") String id,
    @JsonProperty("resourceType") String resourceType,
    @JsonProperty("resourceId") String resourceId,
    @JsonProperty("workflowType") String workflowType,
    @JsonProperty("status") String status,
    @JsonProperty("initiator") String initiator,
    @JsonProperty("stages") List<StageResponse> stages,
    @JsonProperty("approvalRecords") List<ApprovalRecordResponse> approvalRecords,
    @JsonProperty("currentStageIndex") int currentStageIndex,
    @JsonProperty("currentStageName") String currentStageName,
    @JsonProperty("createdAt") String createdAt,
    @JsonProperty("updatedAt") String updatedAt) {}
