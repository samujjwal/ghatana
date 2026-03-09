/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request DTO for creating an approval workflow.
 *
 * @doc.type record
 * @doc.purpose Create approval workflow request
 * @doc.layer api
 * @doc.pattern DTO
 */
public record CreateWorkflowRequest(
    @NotBlank @JsonProperty("resourceType") String resourceType,
    @NotBlank @JsonProperty("resourceId") String resourceId,
    @NotBlank @JsonProperty("workflowType") String workflowType,
    @NotEmpty @JsonProperty("stages") List<StageRequest> stages,
    @JsonProperty("config") ConfigRequest config) {
  /** Stage configuration for the workflow. */
  public record StageRequest(
      @NotBlank @JsonProperty("name") String name,
      @JsonProperty("approvers") List<String> approvers,
      @JsonProperty("approverPersonas") List<String> approverPersonas,
      @JsonProperty("requiredApprovals") int requiredApprovals,
      @JsonProperty("parallel") boolean parallel) {}

  /** Workflow configuration. */
  public record ConfigRequest(
      @JsonProperty("timeoutHours") int timeoutHours,
      @JsonProperty("autoEscalate") boolean autoEscalate,
      @JsonProperty("escalationTarget") String escalationTarget,
      @JsonProperty("notifyOnComplete") boolean notifyOnComplete) {}
}
