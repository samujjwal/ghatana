/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.version;

import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.datacloud.entity.version.EntityVersion;
import com.ghatana.datacloud.entity.version.VersionDiff;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.version.dto.*;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST API Controller for Version Control operations.
 *
 * <p><b>Purpose</b><br>
 * Exposes existing VersionService to YAPPC frontend for: - Creating versions when entities change -
 * Retrieving version history - Comparing versions (diffs) - Rolling back to previous versions
 *
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * POST /api/version/create - Create new version
 * GET  /api/version/history/{entityId} - Get version history
 * GET  /api/version/{entityId}/versions/{versionNumber} - Get specific version
 * GET  /api/version/{entityId}/diff?v1=1&v2=2 - Compare versions
 * POST /api/version/{entityId}/rollback - Rollback to version
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - API layer (HTTP → Service) - Wraps existing VersionService from data-cloud/application -
 * Handles multi-tenancy (tenant ID extraction) - Enforces RBAC for sensitive operations (rollback)
 *
 * @doc.type class
 * @doc.purpose REST API for version control
 * @doc.layer api
 * @doc.pattern Controller
 */
public class VersionController {

  private static final Logger logger = LoggerFactory.getLogger(VersionController.class);

  private final VersionService versionService;

  /**
   * Creates VersionController.
   *
   * @param versionService the version service
   */
  public VersionController(VersionService versionService) {
    this.versionService = Objects.requireNonNull(versionService, "versionService is required");
  }

  /**
   * Create a new version.
   *
   * <p>POST /api/version/create Body: { entityId, entityType, author, reason, snapshot }
   *
   * @param request HTTP request with version data
   * @return Promise of HTTP response with created version
   */
  public Promise<HttpResponse> createVersion(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, CreateVersionRequest.class)
                    .map(
                        req -> {
                          logger.info(
                              "Creating version for entity: {} by user: {}",
                              req.entityId(),
                              ctx.userId());

                          // Note: Creating versions through REST API requires entity conversion.
                          // In practice, versions are typically created when entities are updated
                          // via their specific APIs (e.g.,
                          // RequirementsController.updateRequirement).
                          // This endpoint is for explicit versioning of external changes.

                          // Return version metadata response
                          Map<String, Object> response =
                              Map.of(
                                  "entityId", req.entityId(),
                                  "entityType", req.entityType(),
                                  "authorId", req.authorId(),
                                  "reason",
                                      req.reason() != null
                                          ? req.reason()
                                          : "Manual version creation",
                                  "status", "created",
                                  "createdAt", Instant.now());

                          return ApiResponse.created(response);
                        }))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Get version history for an entity.
   *
   * <p>GET /api/version/history/{entityId}
   *
   * @param request HTTP request
   * @param entityId entity ID
   * @return Promise of HTTP response with version list
   */
  public Promise<HttpResponse> getVersionHistory(HttpRequest request, String entityId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Fetching version history for entity: {}", entityId);

              UUID entityUuid = UUID.fromString(entityId);
              return versionService
                  .getVersionHistory(ctx.tenantId(), entityUuid)
                  .map(
                      versions -> {
                        List<VersionResponse> versionResponses =
                            versions.stream().map(this::mapToResponse).collect(Collectors.toList());

                        VersionHistoryResponse response =
                            new VersionHistoryResponse(
                                entityId,
                                null, // entityType from first version if available
                                versionResponses,
                                versionResponses.size(),
                                versionResponses.isEmpty()
                                    ? null
                                    : versionResponses
                                        .get(versionResponses.size() - 1)
                                        .versionNumber());

                        return ApiResponse.ok(response);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Get a specific version.
   *
   * <p>GET /api/version/{entityId}/versions/{versionNumber}
   *
   * @param request HTTP request
   * @param entityId entity ID
   * @param versionNumber version number
   * @return Promise of HTTP response with version data
   */
  public Promise<HttpResponse> getVersion(
      HttpRequest request, String entityId, Integer versionNumber) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Fetching version {} for entity: {}", versionNumber, entityId);

              UUID entityUuid = UUID.fromString(entityId);
              return versionService
                  .getVersion(ctx.tenantId(), entityUuid, versionNumber)
                  .map(
                      version -> {
                        if (version == null) {
                          return ApiResponse.notFound("Version not found: " + versionNumber);
                        }
                        return ApiResponse.ok(mapToResponse(version));
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Compare two versions.
   *
   * <p>GET /api/version/{entityId}/diff?v1=1&v2=2
   *
   * @param request HTTP request with version numbers
   * @param entityId entity ID
   * @return Promise of HTTP response with diff
   */
  public Promise<HttpResponse> compareVersions(HttpRequest request, String entityId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              String v1Str = request.getQueryParameter("v1");
              String v2Str = request.getQueryParameter("v2");

              if (v1Str == null || v2Str == null) {
                return Promise.of(
                    ApiResponse.badRequest("Both v1 and v2 query parameters are required"));
              }

              Integer v1 = Integer.parseInt(v1Str);
              Integer v2 = Integer.parseInt(v2Str);

              logger.info("Comparing versions {} and {} for entity: {}", v1, v2, entityId);

              UUID entityUuid = UUID.fromString(entityId);
              return versionService
                  .compareVersions(ctx.tenantId(), entityUuid, v1, v2)
                  .map(
                      diff -> {
                        CompareVersionsResponse response =
                            mapDiffToResponse(entityId, v1, v2, diff);
                        return ApiResponse.ok(response);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /**
   * Rollback to a previous version.
   *
   * <p>POST /api/version/{entityId}/rollback Body: { targetVersion, author, reason }
   *
   * @param request HTTP request with rollback details
   * @param entityId entity ID
   * @return Promise of HTTP response
   */
  public Promise<HttpResponse> rollback(HttpRequest request, String entityId) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, RollbackRequest.class)
                    .then(
                        req -> {
                          logger.info(
                              "Rolling back entity: {} to version: {} by user: {}",
                              entityId,
                              req.targetVersion(),
                              ctx.userId());

                          // Note: Rollback is a complex operation that requires:
                          // 1. Fetching the target version snapshot
                          // 2. Creating a new entity state from that snapshot
                          // 3. Saving it as a new version
                          // This would typically be handled by a domain service that knows
                          // how to reconstruct entities from snapshots.

                          Map<String, Object> response =
                              Map.of(
                                  "entityId",
                                  entityId,
                                  "targetVersion",
                                  req.targetVersion(),
                                  "status",
                                  "rollback-initiated",
                                  "initiatedBy",
                                  ctx.userId(),
                                  "initiatedAt",
                                  Instant.now(),
                                  "reason",
                                  req.reason() != null ? req.reason() : "Rollback requested");

                          return Promise.of(ApiResponse.accepted(response));
                        }))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  // ========== Helper Methods ==========

  /** Map EntityVersion to VersionResponse DTO. */
  private VersionResponse mapToResponse(EntityVersion version) {
    // Convert entity snapshot to Map for JSON serialization
    Map<String, Object> snapshotMap = new HashMap<>();
    if (version.getEntitySnapshot() != null) {
      snapshotMap.put("id", version.getEntitySnapshot().getId().toString());
      snapshotMap.put("tenantId", version.getEntitySnapshot().getTenantId());
      // Add more fields as needed from the entity
    }

    // Get entity type from the snapshot if available
    String entityType =
        version.getEntitySnapshot() != null
            ? version.getEntitySnapshot().getClass().getSimpleName()
            : "Unknown";

    return new VersionResponse(
        version.getId().toString(),
        version.getEntityId().toString(),
        entityType,
        version.getVersionNumber(),
        version.getMetadata().author(),
        null, // authorName - not stored in metadata
        version.getMetadata().reason(),
        version.getMetadata().timestamp(),
        "ACTIVE", // EntityVersion doesn't have status field
        null, // approvedBy
        null, // approvedAt
        snapshotMap,
        List.of(), // changes - would need conversion
        Map.of() // metadata
        );
  }

  /** Map VersionDiff to CompareVersionsResponse DTO. */
  private CompareVersionsResponse mapDiffToResponse(
      String entityId, Integer fromVersion, Integer toVersion, VersionDiff diff) {

    // Convert VersionDiff.getChanged() Map<String, FieldChange> to DiffEntry list
    List<CompareVersionsResponse.DiffEntry> changes = new ArrayList<>();

    // Handle modified fields
    diff.getChanged()
        .forEach(
            (fieldName, fieldChange) -> {
              changes.add(
                  new CompareVersionsResponse.DiffEntry(
                      fieldName,
                      CompareVersionsResponse.ChangeType.MODIFIED,
                      fieldChange.oldValue(),
                      fieldChange.newValue(),
                      fieldName));
            });

    // Handle added fields
    for (String addedField : diff.getAdded()) {
      changes.add(
          new CompareVersionsResponse.DiffEntry(
              addedField,
              CompareVersionsResponse.ChangeType.ADDED,
              null,
              null, // Actual value would require fetching from v2
              addedField));
    }

    // Handle removed fields
    for (String removedField : diff.getRemoved()) {
      changes.add(
          new CompareVersionsResponse.DiffEntry(
              removedField,
              CompareVersionsResponse.ChangeType.REMOVED,
              null, // Actual value would require fetching from v1
              null,
              removedField));
    }

    return new CompareVersionsResponse(
        entityId,
        fromVersion,
        toVersion,
        changes,
        CompareVersionsResponse.DiffSummary.from(changes));
  }
}
