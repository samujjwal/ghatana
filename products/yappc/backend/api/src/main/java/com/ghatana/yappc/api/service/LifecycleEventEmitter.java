/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes strongly-typed YAPPC lifecycle events to AEP.
 *
 * <p><b>Purpose</b><br>
 * Provides a typed API for emitting domain events from YAPPC domain services.
 * Each method corresponds to a specific lifecycle transition (e.g., requirement
 * created, sprint started, build completed). Events are published to AEP in a
 * fire-and-forget manner — emission failures are logged but never propagated to
 * callers so that the main domain flow is never blocked.
 *
 * <p><b>Event Schema</b><br>
 * All events share a common envelope:
 * <pre>
 * {
 *   "eventType":   "yappc.requirement.created",
 *   "tenantId":    "...",
 *   "occurredAt":  "2025-01-01T00:00:00Z",
 *   ...event-specific fields...
 * }
 * </pre>
 *
 * <p><b>Architecture</b><br>
 * This class is a thin adapter (not a domain object) and lives in the application
 * service layer. It depends on {@link AepService} (infrastructure) to publish.
 *
 * @doc.type class
 * @doc.purpose Typed lifecycle event emitter for YAPPC domain events via AEP
 * @doc.layer product
 * @doc.pattern Adapter, Application Service
 */
public class LifecycleEventEmitter {

  private static final Logger logger = LoggerFactory.getLogger(LifecycleEventEmitter.class);

  private final AepService aepService;
  private final ObjectMapper objectMapper;

  /**
   * Creates a LifecycleEventEmitter.
   *
   * @param aepService   AEP service for publishing events
   * @param objectMapper Jackson mapper for event payload serialization
   */
  public LifecycleEventEmitter(AepService aepService, ObjectMapper objectMapper) {
    this.aepService = Objects.requireNonNull(aepService, "AepService must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Requirement lifecycle events
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Emits a {@code yappc.requirement.created} event.
   *
   * @param tenantId       tenant scope
   * @param requirementId  newly created requirement ID
   * @param title          requirement title
   * @param createdBy      user who created it
   */
  public void emitRequirementCreated(
      String tenantId, String requirementId, String title, String createdBy) {
    emit("yappc.requirement.created", tenantId, Map.of(
        "requirementId", requirementId,
        "title", title,
        "createdBy", createdBy));
  }

  /**
   * Emits a {@code yappc.requirement.approved} event.
   *
   * @param tenantId       tenant scope
   * @param requirementId  approved requirement ID
   * @param approvedBy     user who approved it
   */
  public void emitRequirementApproved(String tenantId, String requirementId, String approvedBy) {
    emit("yappc.requirement.approved", tenantId, Map.of(
        "requirementId", requirementId,
        "approvedBy", approvedBy));
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Sprint lifecycle events
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Emits a {@code yappc.sprint.started} event.
   *
   * @param tenantId   tenant scope
   * @param sprintId   started sprint ID
   * @param sprintName sprint name
   * @param startedBy  user who started it
   */
  public void emitSprintStarted(
      String tenantId, String sprintId, String sprintName, String startedBy) {
    emit("yappc.sprint.started", tenantId, Map.of(
        "sprintId", sprintId,
        "sprintName", sprintName,
        "startedBy", startedBy));
  }

  /**
   * Emits a {@code yappc.sprint.completed} event.
   *
   * @param tenantId   tenant scope
   * @param sprintId   completed sprint ID
   * @param velocity   sprint velocity (story points completed)
   */
  public void emitSprintCompleted(String tenantId, String sprintId, int velocity) {
    emit("yappc.sprint.completed", tenantId, Map.of(
        "sprintId", sprintId,
        "velocity", velocity));
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Build lifecycle events
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Emits a {@code yappc.build.started} event.
   *
   * @param tenantId  tenant scope
   * @param jobId     build job ID
   * @param projectId project being built
   */
  public void emitBuildStarted(String tenantId, String jobId, String projectId) {
    emit("yappc.build.started", tenantId, Map.of(
        "jobId", jobId,
        "projectId", projectId));
  }

  /**
   * Emits a {@code yappc.build.completed} event.
   *
   * @param tenantId   tenant scope
   * @param jobId      build job ID
   * @param projectId  project that was built
   * @param success    whether the build succeeded
   * @param durationMs build duration in milliseconds
   */
  public void emitBuildCompleted(
      String tenantId, String jobId, String projectId, boolean success, long durationMs) {
    emit("yappc.build.completed", tenantId, Map.of(
        "jobId", jobId,
        "projectId", projectId,
        "success", success,
        "durationMs", durationMs));
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Approval workflow events
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Emits a {@code yappc.approval.requested} event.
   *
   * @param tenantId    tenant scope
   * @param workflowId  approval workflow ID
   * @param resourceId  resource under approval
   * @param requestedBy user who initiated the approval
   */
  public void emitApprovalRequested(
      String tenantId, String workflowId, String resourceId, String requestedBy) {
    emit("yappc.approval.requested", tenantId, Map.of(
        "workflowId", workflowId,
        "resourceId", resourceId,
        "requestedBy", requestedBy));
  }

  /**
   * Emits a {@code yappc.approval.decided} event.
   *
   * @param tenantId    tenant scope
   * @param workflowId  approval workflow ID
   * @param approved    true if approved, false if rejected
   * @param decidedBy   approver/rejector user ID
   */
  public void emitApprovalDecided(
      String tenantId, String workflowId, boolean approved, String decidedBy) {
    emit("yappc.approval.decided", tenantId, Map.of(
        "workflowId", workflowId,
        "approved", approved,
        "decidedBy", decidedBy));
  }

  // ═══════════════════════════════════════════════════════════════════════
  // No-op variant for testing / dev-mode
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Creates a no-op {@link LifecycleEventEmitter} that logs events without publishing.
   * Suitable for development and unit testing.
   *
   * @param objectMapper Jackson mapper (can be shared)
   * @return no-op emitter
   */
  public static LifecycleEventEmitter noop(ObjectMapper objectMapper) {
    return new LifecycleEventEmitter(null, objectMapper) {
      @Override
      protected void emit(String eventType, String tenantId, Map<String, Object> payload) {
        logger.debug("[NOOP] Would emit event type={} tenantId={} payload={}",
            eventType, tenantId, payload);
      }
    };
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Internal helpers
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Builds the event envelope and publishes it fire-and-forget via AepService.
   * Errors are caught and logged — never propagated to callers.
   *
   * @param eventType event type string (e.g. {@code "yappc.requirement.created"})
   * @param tenantId  tenant scope
   * @param payload   event-specific data
   */
  protected void emit(String eventType, String tenantId, Map<String, Object> payload) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("eventType", eventType);
      envelope.put("tenantId", tenantId);
      envelope.put("occurredAt", Instant.now().toString());
      envelope.putAll(payload);

      String json = objectMapper.writeValueAsString(envelope);
      aepService.publishEvent(eventType, json);

      logger.debug("Emitted lifecycle event type={} tenantId={}", eventType, tenantId);

    } catch (AepException e) {
      logger.warn("Failed to publish lifecycle event type={} tenantId={}: {}",
          eventType, tenantId, e.getMessage());
    } catch (Exception e) {
      logger.warn("Unexpected error emitting lifecycle event type={} tenantId={}: {}",
          eventType, tenantId, e.getMessage());
    }
  }
}
