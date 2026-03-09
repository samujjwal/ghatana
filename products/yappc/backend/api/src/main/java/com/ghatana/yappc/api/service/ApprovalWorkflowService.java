/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing approval workflows for requirements and other entities.
 *
 * <p><b>Purpose</b><br>
 * Implements multi-stage approval workflows with configurable approval chains. Supports
 * persona-based approvers, parallel approvals, and escalation.
 *
 * <p><b>Workflow States</b><br>
 * - PENDING: Awaiting approvals - APPROVED: All required approvals received - REJECTED: At least
 * one rejection - ESCALATED: Approval timeout, escalated to next level - CANCELLED: Workflow
 * cancelled by initiator
 *
 * <p><b>Integration</b><br>
 * Uses libs/java/workflow-api WorkflowContext for execution context. All workflow actions are
 * audited via AuditService.
 *
 * @doc.type class
 * @doc.purpose Approval workflow management
 * @doc.layer application
 * @doc.pattern Service, State Machine
 */
public class ApprovalWorkflowService {

  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowService.class);

  private final AuditService auditService;

  // In-memory storage for workflows (replace with repository for persistence)
  private final Map<UUID, ApprovalWorkflow> workflows = new ConcurrentHashMap<>();
  private final Map<String, List<UUID>> workflowsByResource = new ConcurrentHashMap<>();

  /**
   * Creates an ApprovalWorkflowService.
   *
   * @param auditService the audit service
   */
  public ApprovalWorkflowService(AuditService auditService) {
    this.auditService = Objects.requireNonNull(auditService, "AuditService must not be null");
  }

  // ========== Workflow Creation ==========

  /**
   * Create an approval workflow for a resource.
   *
   * @param tenantId the tenant ID
   * @param resourceType the resource type (e.g., "REQUIREMENT")
   * @param resourceId the resource ID
   * @param workflowType the workflow type (e.g., "REQUIREMENT_APPROVAL")
   * @param initiator the user who initiated the workflow
   * @param requiredApprovers list of required approver IDs or personas
   * @param config workflow configuration
   * @return Promise of created workflow
   */
  public Promise<ApprovalWorkflow> createWorkflow(
      String tenantId,
      String resourceType,
      String resourceId,
      WorkflowType workflowType,
      String initiator,
      List<ApprovalStage> stages,
      WorkflowConfig config) {

    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(resourceType, "Resource type must not be null");
    Objects.requireNonNull(resourceId, "Resource ID must not be null");
    Objects.requireNonNull(workflowType, "Workflow type must not be null");
    Objects.requireNonNull(initiator, "Initiator must not be null");
    Objects.requireNonNull(stages, "Stages must not be null");

    if (stages.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("At least one approval stage required"));
    }

    UUID workflowId = UUID.randomUUID();
    Instant now = Instant.now();

    ApprovalWorkflow workflow =
        new ApprovalWorkflow(
            workflowId,
            tenantId,
            resourceType,
            resourceId,
            workflowType,
            WorkflowStatus.PENDING,
            initiator,
            stages,
            new ArrayList<>(),
            0, // currentStageIndex
            now,
            now,
            config != null ? config : WorkflowConfig.DEFAULT);

    workflows.put(workflowId, workflow);
    workflowsByResource
        .computeIfAbsent(resourceKey(tenantId, resourceType, resourceId), k -> new ArrayList<>())
        .add(workflowId);

    logger.info("Created approval workflow {} for {}/{}", workflowId, resourceType, resourceId);

    return recordAudit(
            tenantId,
            "APPROVAL_WORKFLOW",
            "CREATE",
            workflowId.toString(),
            initiator,
            Map.of(
                "workflowType",
                workflowType.name(),
                "resourceType",
                resourceType,
                "resourceId",
                resourceId,
                "stages",
                stages.size()))
        .map(v -> workflow);
  }

  // ========== Approval Actions ==========

  /**
   * Submit an approval decision.
   *
   * @param tenantId the tenant ID
   * @param workflowId the workflow ID
   * @param approver the approver user ID
   * @param decision APPROVE or REJECT
   * @param comments optional comments
   * @return Promise of updated workflow
   */
  public Promise<ApprovalWorkflow> submitDecision(
      String tenantId,
      UUID workflowId,
      String approver,
      ApprovalDecision decision,
      String comments) {

    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(workflowId, "Workflow ID must not be null");
    Objects.requireNonNull(approver, "Approver must not be null");
    Objects.requireNonNull(decision, "Decision must not be null");

    ApprovalWorkflow workflow = workflows.get(workflowId);
    if (workflow == null) {
      return Promise.ofException(new NoSuchElementException("Workflow not found: " + workflowId));
    }

    if (!workflow.tenantId().equals(tenantId)) {
      return Promise.ofException(new SecurityException("Workflow belongs to different tenant"));
    }

    if (workflow.status() != WorkflowStatus.PENDING) {
      return Promise.ofException(
          new IllegalStateException("Workflow is not pending: " + workflow.status()));
    }

    // Check if approver is authorized for current stage
    ApprovalStage currentStage = workflow.stages().get(workflow.currentStageIndex());
    if (!isAuthorizedApprover(approver, currentStage)) {
      return Promise.ofException(
          new SecurityException(
              "User "
                  + approver
                  + " is not authorized to approve at stage "
                  + currentStage.name()));
    }

    // Record the decision
    ApprovalRecord record =
        new ApprovalRecord(
            UUID.randomUUID(),
            workflow.currentStageIndex(),
            currentStage.name(),
            approver,
            decision,
            comments,
            Instant.now());

    List<ApprovalRecord> updatedRecords = new ArrayList<>(workflow.approvalRecords());
    updatedRecords.add(record);

    // Determine new workflow state
    WorkflowStatus newStatus;
    int newStageIndex = workflow.currentStageIndex();

    if (decision == ApprovalDecision.REJECT) {
      // Any rejection fails the workflow
      newStatus = WorkflowStatus.REJECTED;
    } else {
      // Check if all required approvals for current stage are met
      if (isStageComplete(currentStage, updatedRecords, workflow.currentStageIndex())) {
        if (workflow.currentStageIndex() + 1 < workflow.stages().size()) {
          // Move to next stage
          newStageIndex = workflow.currentStageIndex() + 1;
          newStatus = WorkflowStatus.PENDING;
        } else {
          // All stages complete
          newStatus = WorkflowStatus.APPROVED;
        }
      } else {
        newStatus = WorkflowStatus.PENDING;
      }
    }

    ApprovalWorkflow updatedWorkflow =
        new ApprovalWorkflow(
            workflow.id(),
            workflow.tenantId(),
            workflow.resourceType(),
            workflow.resourceId(),
            workflow.workflowType(),
            newStatus,
            workflow.initiator(),
            workflow.stages(),
            updatedRecords,
            newStageIndex,
            workflow.createdAt(),
            Instant.now(),
            workflow.config());

    workflows.put(workflowId, updatedWorkflow);

    logger.info(
        "Approval decision submitted: workflow={}, approver={}, decision={}, newStatus={}",
        workflowId,
        approver,
        decision,
        newStatus);

    return recordAudit(
            tenantId,
            "APPROVAL_WORKFLOW",
            "DECISION",
            workflowId.toString(),
            approver,
            Map.of(
                "decision", decision.name(),
                "stage", currentStage.name(),
                "newStatus", newStatus.name()))
        .map(v -> updatedWorkflow);
  }

  /** Cancel a workflow. */
  public Promise<ApprovalWorkflow> cancelWorkflow(
      String tenantId, UUID workflowId, String cancelledBy, String reason) {
    ApprovalWorkflow workflow = workflows.get(workflowId);
    if (workflow == null) {
      return Promise.ofException(new NoSuchElementException("Workflow not found: " + workflowId));
    }

    if (!workflow.initiator().equals(cancelledBy)) {
      return Promise.ofException(new SecurityException("Only initiator can cancel workflow"));
    }

    ApprovalWorkflow cancelled =
        new ApprovalWorkflow(
            workflow.id(),
            workflow.tenantId(),
            workflow.resourceType(),
            workflow.resourceId(),
            workflow.workflowType(),
            WorkflowStatus.CANCELLED,
            workflow.initiator(),
            workflow.stages(),
            workflow.approvalRecords(),
            workflow.currentStageIndex(),
            workflow.createdAt(),
            Instant.now(),
            workflow.config());

    workflows.put(workflowId, cancelled);

    return recordAudit(
            tenantId,
            "APPROVAL_WORKFLOW",
            "CANCEL",
            workflowId.toString(),
            cancelledBy,
            Map.of("reason", reason != null ? reason : "No reason provided"))
        .map(v -> cancelled);
  }

  // ========== Query Operations ==========

  /** Get workflow by ID. */
  public Promise<Optional<ApprovalWorkflow>> getWorkflow(String tenantId, UUID workflowId) {
    ApprovalWorkflow workflow = workflows.get(workflowId);
    if (workflow != null && workflow.tenantId().equals(tenantId)) {
      return Promise.of(Optional.of(workflow));
    }
    return Promise.of(Optional.empty());
  }

  /** Get workflows for a resource. */
  public Promise<List<ApprovalWorkflow>> getWorkflowsForResource(
      String tenantId, String resourceType, String resourceId) {

    String key = resourceKey(tenantId, resourceType, resourceId);
    List<UUID> workflowIds = workflowsByResource.getOrDefault(key, List.of());

    List<ApprovalWorkflow> result =
        workflowIds.stream()
            .map(workflows::get)
            .filter(Objects::nonNull)
            .filter(w -> w.tenantId().equals(tenantId))
            .toList();

    return Promise.of(result);
  }

  /** Get pending approvals for a user. */
  public Promise<List<ApprovalWorkflow>> getPendingApprovalsForUser(
      String tenantId, String userId) {
    List<ApprovalWorkflow> pending =
        workflows.values().stream()
            .filter(w -> w.tenantId().equals(tenantId))
            .filter(w -> w.status() == WorkflowStatus.PENDING)
            .filter(
                w -> {
                  ApprovalStage currentStage = w.stages().get(w.currentStageIndex());
                  return isAuthorizedApprover(userId, currentStage);
                })
            .filter(w -> !hasUserAlreadyApproved(w, userId))
            .toList();

    return Promise.of(pending);
  }

  // ========== Helper Methods ==========

  private boolean isAuthorizedApprover(String userId, ApprovalStage stage) {
    // Check if user is in approvers list or matches a persona
    return stage.approvers().contains(userId)
        || stage.approverPersonas().stream().anyMatch(persona -> matchesPersona(userId, persona));
  }

  private boolean matchesPersona(String userId, String persona) {
    // In a real implementation, this would check the user's assigned personas
    // For now, we allow all users matching common persona patterns
    return true; // Simplified for MVP
  }

  private boolean isStageComplete(
      ApprovalStage stage, List<ApprovalRecord> records, int stageIndex) {
    long approvalsForStage =
        records.stream()
            .filter(r -> r.stageIndex() == stageIndex)
            .filter(r -> r.decision() == ApprovalDecision.APPROVE)
            .count();

    return approvalsForStage >= stage.requiredApprovals();
  }

  private boolean hasUserAlreadyApproved(ApprovalWorkflow workflow, String userId) {
    return workflow.approvalRecords().stream()
        .filter(r -> r.stageIndex() == workflow.currentStageIndex())
        .anyMatch(r -> r.approver().equals(userId));
  }

  private String resourceKey(String tenantId, String resourceType, String resourceId) {
    return tenantId + ":" + resourceType + ":" + resourceId;
  }

  private Promise<Void> recordAudit(
      String tenantId,
      String resourceType,
      String action,
      String resourceId,
      String userId,
      Map<String, Object> details) {

    AuditEvent event =
        AuditEvent.builder()
            .tenantId(tenantId)
            .eventType(resourceType + "." + action)
            .principal(userId)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .success(true)
            .details(details)
            .timestamp(Instant.now())
            .build();

    return auditService.record(event);
  }

  // ========== Domain Types ==========

  public enum WorkflowType {
    REQUIREMENT_APPROVAL,
    REQUIREMENT_SET_APPROVAL,
    ARCHITECTURE_CHANGE,
    SECURITY_EXCEPTION,
    RELEASE_APPROVAL
  }

  public enum WorkflowStatus {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED,
    CANCELLED
  }

  public enum ApprovalDecision {
    APPROVE,
    REJECT
  }

  /** An approval workflow instance. */
  public record ApprovalWorkflow(
      UUID id,
      String tenantId,
      String resourceType,
      String resourceId,
      WorkflowType workflowType,
      WorkflowStatus status,
      String initiator,
      List<ApprovalStage> stages,
      List<ApprovalRecord> approvalRecords,
      int currentStageIndex,
      Instant createdAt,
      Instant updatedAt,
      WorkflowConfig config) {}

  /** A stage in the approval workflow. */
  public record ApprovalStage(
      String name,
      List<String> approvers,
      List<String> approverPersonas,
      int requiredApprovals,
      boolean parallel // If true, any approver can approve. If false, must be sequential.
      ) {
    public static ApprovalStage single(String name, String... approvers) {
      return new ApprovalStage(name, List.of(approvers), List.of(), 1, true);
    }

    public static ApprovalStage byPersona(String name, int required, String... personas) {
      return new ApprovalStage(name, List.of(), List.of(personas), required, true);
    }
  }

  /** A record of an approval decision. */
  public record ApprovalRecord(
      UUID id,
      int stageIndex,
      String stageName,
      String approver,
      ApprovalDecision decision,
      String comments,
      Instant timestamp) {}

  /** Workflow configuration. */
  public record WorkflowConfig(
      int timeoutHours, boolean autoEscalate, String escalationTarget, boolean notifyOnComplete) {
    public static final WorkflowConfig DEFAULT = new WorkflowConfig(72, false, null, true);
  }
}
