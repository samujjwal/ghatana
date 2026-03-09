/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.domain.Requirement.Priority;
import com.ghatana.yappc.api.domain.Requirement.RequirementStatus;
import com.ghatana.yappc.api.domain.Requirement.RequirementType;
import com.ghatana.yappc.api.repository.RequirementRepository;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service for requirement management operations.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates requirement lifecycle operations including create, update, status transitions,
 * versioning, and audit logging. Coordinates between repository, version service, and audit
 * service.
 *
 * <p><b>Responsibilities</b><br>
 * - CRUD operations with validation - Lifecycle state transitions (DRAFT → REVIEW → APPROVED) -
 * Integration with VersionService for change tracking - Integration with AuditService for audit
 * trail - Quality score calculations - Funnel metrics computation
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe if underlying services are thread-safe. Uses ActiveJ Promise for non-blocking async
 * operations.
 *
 * @doc.type class
 * @doc.purpose Requirement management service
 * @doc.layer application
 * @doc.pattern Service
 */
public class RequirementService {

  private static final Logger logger = LoggerFactory.getLogger(RequirementService.class);

  private final RequirementRepository repository;
  private final AuditService auditService;
  private final VersionService versionService;

  /**
   * Creates a RequirementService with dependencies.
   *
   * @param repository the requirement repository
   * @param auditService the audit service for logging changes
   * @param versionService the version service for change tracking (nullable)
   */
  public RequirementService(
      RequirementRepository repository, AuditService auditService, VersionService versionService) {
    this.repository = Objects.requireNonNull(repository, "Repository must not be null");
    this.auditService = Objects.requireNonNull(auditService, "AuditService must not be null");
    this.versionService = versionService; // Can be null for simple operations
  }

  // ========== CRUD Operations ==========

  /**
   * Create a new requirement.
   *
   * @param tenantId the tenant ID
   * @param title the requirement title
   * @param description the requirement description
   * @param type the requirement type
   * @param priority the priority level
   * @param createdBy the creating user ID
   * @return Promise of created requirement
   */
  public Promise<Requirement> createRequirement(
      String tenantId,
      String title,
      String description,
      RequirementType type,
      Priority priority,
      String createdBy) {

    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(title, "Title must not be null");
    Objects.requireNonNull(createdBy, "Created by must not be null");

    logger.info("Creating requirement '{}' for tenant {}", title, tenantId);

    Requirement requirement =
        Requirement.builder()
            .tenantId(tenantId)
            .title(title)
            .description(description)
            .type(type != null ? type : RequirementType.FUNCTIONAL)
            .status(RequirementStatus.DRAFT)
            .priority(priority != null ? priority : Priority.MEDIUM)
            .createdBy(createdBy)
            .build();

    return repository
        .save(requirement)
        .then(
            saved ->
                recordAudit(
                        tenantId,
                        "REQUIREMENT",
                        "CREATE",
                        saved.getId().toString(),
                        createdBy,
                        Map.of("title", title, "type", requirement.getType().name()))
                    .map(v -> saved));
  }

  /**
   * Get requirement by ID.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @return Promise of Optional requirement
   */
  public Promise<Optional<Requirement>> getRequirement(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");

    return repository.findById(tenantId, id);
  }

  /**
   * Get all requirements for a tenant.
   *
   * @param tenantId the tenant ID
   * @return Promise of list of requirements
   */
  public Promise<List<Requirement>> getAllRequirements(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    return repository.findAllByTenant(tenantId);
  }

  /**
   * Get all requirements for a project.
   *
   * @param tenantId the tenant ID
   * @param projectId the project ID
   * @return Promise of list of requirements
   */
  public Promise<List<Requirement>> getRequirementsByProject(String tenantId, String projectId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(projectId, "Project ID must not be null");

    return repository.findByProject(tenantId, projectId);
  }

  /**
   * Update a requirement.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param title new title (nullable to keep existing)
   * @param description new description (nullable to keep existing) /** Update a requirement.
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param title new title (nullable to keep existing)
   * @param description new description (nullable to keep existing)
   * @param priority new priority (nullable to keep existing)
   * @param updatedBy the updating user ID
   * @return Promise of Optional updated requirement
   */
  public Promise<Optional<Requirement>> updateRequirement(
      String tenantId,
      UUID id,
      String title,
      String description,
      Priority priority,
      String updatedBy) {

    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");
    Objects.requireNonNull(updatedBy, "Updated by must not be null");

    return repository
        .findById(tenantId, id)
        .then(
            optReq -> {
              if (optReq.isEmpty()) {
                return Promise.of(Optional.<Requirement>empty());
              }

              Requirement requirement = optReq.get();
              Map<String, Object> changes = new HashMap<>();

              if (title != null && !title.equals(requirement.getTitle())) {
                changes.put("title", Map.of("old", requirement.getTitle(), "new", title));
                requirement.setTitle(title);
              }
              if (description != null && !description.equals(requirement.getDescription())) {
                changes.put("description", "updated");
                requirement.setDescription(description);
              }
              if (priority != null && priority != requirement.getPriority()) {
                changes.put(
                    "priority",
                    Map.of(
                        "old", requirement.getPriority().name(),
                        "new", priority.name()));
                requirement.setPriority(priority);
              }

              requirement.setUpdatedAt(Instant.now());
              requirement.setVersionNumber(requirement.getVersionNumber() + 1);

              return repository
                  .save(requirement)
                  .then(
                      saved ->
                          recordAudit(
                                  tenantId,
                                  "REQUIREMENT",
                                  "UPDATE",
                                  id.toString(),
                                  updatedBy,
                                  changes)
                              .map(v -> Optional.of(saved)));
            });
  }

  /**
   * Delete a requirement.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param deletedBy the deleting user ID
   * @return Promise completing when deleted
   */
  public Promise<Void> deleteRequirement(String tenantId, UUID id, String deletedBy) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");
    Objects.requireNonNull(deletedBy, "Deleted by must not be null");

    return repository
        .delete(tenantId, id)
        .then(
            v ->
                recordAudit(tenantId, "REQUIREMENT", "DELETE", id.toString(), deletedBy, Map.of()));
  }

  // ========== Lifecycle Transitions ==========

  /**
   * Submit requirement for review.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param submittedBy the submitting user ID
   * @return Promise of updated requirement
   */
  public Promise<Requirement> submitForReview(String tenantId, UUID id, String submittedBy) {
    return transitionStatus(
        tenantId,
        id,
        submittedBy,
        req -> {
          req.submitForReview();
          return "SUBMIT_FOR_REVIEW";
        });
  }

  /**
   * Approve a requirement.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param approvedBy the approving user ID
   * @return Promise of Optional updated requirement
   */
  public Promise<Optional<Requirement>> approveRequirement(
      String tenantId, UUID id, String approvedBy) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");
    Objects.requireNonNull(approvedBy, "Approved by must not be null");

    return repository
        .findById(tenantId, id)
        .then(
            optReq -> {
              if (optReq.isEmpty()) {
                return Promise.of(Optional.<Requirement>empty());
              }

              Requirement requirement = optReq.get();
              RequirementStatus oldStatus = requirement.getStatus();
              requirement.approve();

              return repository
                  .save(requirement)
                  .then(
                      saved ->
                          recordAudit(
                                  tenantId,
                                  "REQUIREMENT",
                                  "APPROVE",
                                  id.toString(),
                                  approvedBy,
                                  Map.of(
                                      "oldStatus", oldStatus.name(),
                                      "newStatus", saved.getStatus().name()))
                              .map(v -> Optional.of(saved)));
            });
  }

  /**
   * Approve a requirement (throws exception if not found).
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param approvedBy the approving user ID
   * @return Promise of updated requirement
   */
  public Promise<Requirement> approve(String tenantId, UUID id, String approvedBy) {
    return transitionStatus(
        tenantId,
        id,
        approvedBy,
        req -> {
          req.approve();
          return "APPROVE";
        });
  }

  /**
   * Reject a requirement.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @param rejectedBy the rejecting user ID
   * @return Promise of updated requirement
   */
  public Promise<Requirement> reject(String tenantId, UUID id, String rejectedBy) {
    return transitionStatus(
        tenantId,
        id,
        rejectedBy,
        req -> {
          req.reject();
          return "REJECT";
        });
  }

  private Promise<Requirement> transitionStatus(
      String tenantId,
      UUID id,
      String userId,
      java.util.function.Function<Requirement, String> transition) {

    return repository
        .findById(tenantId, id)
        .then(
            optReq -> {
              if (optReq.isEmpty()) {
                return Promise.ofException(
                    new NoSuchElementException("Requirement not found: " + id));
              }

              Requirement requirement = optReq.get();
              RequirementStatus oldStatus = requirement.getStatus();
              String action = transition.apply(requirement);

              return repository
                  .save(requirement)
                  .then(
                      saved ->
                          recordAudit(
                                  tenantId,
                                  "REQUIREMENT",
                                  action,
                                  id.toString(),
                                  userId,
                                  Map.of(
                                      "oldStatus", oldStatus.name(),
                                      "newStatus", saved.getStatus().name()))
                              .map(v -> saved));
            });
  }

  // ========== Query Operations ==========

  /** Get requirements by status. */
  public Promise<List<Requirement>> getByStatus(String tenantId, RequirementStatus status) {
    return repository.findByStatus(tenantId, status);
  }

  /** Get requirements by type. */
  public Promise<List<Requirement>> getByType(String tenantId, RequirementType type) {
    return repository.findByType(tenantId, type);
  }

  /** Get requirements assigned to user. */
  public Promise<List<Requirement>> getByAssignee(String tenantId, String userId) {
    return repository.findByAssignee(tenantId, userId);
  }

  /** Get requirements with quality issues. */
  public Promise<List<Requirement>> getQualityIssues(String tenantId, double threshold) {
    return repository.findBelowQualityThreshold(tenantId, threshold);
  }

  // ========== Funnel Metrics ==========

  /**
   * Get funnel metrics (counts by status).
   *
   * @param tenantId the tenant ID
   * @return Promise of map status -> count
   */
  public Promise<Map<RequirementStatus, Long>> getFunnelMetrics(String tenantId) {
    return repository.countByStatus(tenantId);
  }

  // ========== Helper Methods ==========

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
}
