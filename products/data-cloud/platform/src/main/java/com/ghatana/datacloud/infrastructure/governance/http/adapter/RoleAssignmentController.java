package com.ghatana.datacloud.infrastructure.governance.http.adapter;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.datacloud.infrastructure.governance.http.dto.*;
import com.ghatana.datacloud.infrastructure.governance.service.RoleAssignmentService;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * HTTP adapter for role assignment endpoints.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides REST API for managing role assignments and revocations. Implements 3
 * core endpoints with batch support.
 *
 * <p>
 * <b>Endpoints Provided</b><br>
 * - POST /assignments (assign role to principals) - DELETE /assignments (revoke
 * role from principals) - GET /assignments/{principalId} (list roles assigned
 * to principal)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RoleAssignmentController controller = new RoleAssignmentController(assignmentService, meterRegistry);
 * Promise<HttpResponse> response = controller.assignRole(tenantId, assignRequest);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP controller for role assignment REST endpoints
 * @doc.layer infrastructure
 * @doc.pattern Adapter (HTTP)
 */
public final class RoleAssignmentController {

    private final RoleAssignmentService assignmentService;
    private final MetricsCollector metrics;

    /**
     * Constructs the role assignment controller.
     *
     * @param assignmentService the role assignment service
     * @param meterRegistry the metrics registry
     * @throws NullPointerException if parameters are null
     */
    public RoleAssignmentController(RoleAssignmentService assignmentService, MeterRegistry meterRegistry) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService cannot be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.metrics = MetricsCollectorFactory.create(meterRegistry);
    }

    /**
     * Assigns a role to multiple principals.
     *
     * HTTP: POST /assignments Request: AssignRoleRequest Response:
     * RoleAssignmentResponse (HTTP 200)
     *
     * GIVEN: Valid role and principal IDs WHEN: POST /assignments is called
     * THEN: Role is assigned to all principals
     *
     * @param tenantId the tenant ID from context
     * @param request the role assignment request
     * @return Promise of HTTP response with assignment results
     */
    public Promise<HttpResponse> assignRole(String tenantId, AssignRoleRequest request) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        long startTime = System.currentTimeMillis();
        return assignmentService.assignRoleToPrincipals(
                tenantId,
                request.getRoleId(),
                request.getPrincipalIds())
                .map(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.assign.duration", duration);
                    metrics.incrementCounter("assignment.assign.success",
                            "count", String.valueOf(result.successCount()));

                    RoleAssignmentResponse response = RoleAssignmentResponse.builder()
                            .roleId(request.getRoleId())
                            .successCount(result.successCount())
                            .failureCount(result.failureCount())
                            .message(String.format("Assigned role to %d principals", result.successCount()))
                            .timestamp(System.currentTimeMillis())
                            .build();

                    return ResponseBuilder.ok().json(response).build();
                })
                .then(Promise::of, ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.assign.duration", duration);
                    metrics.incrementCounter("assignment.assign.error", "type", ex.getClass().getSimpleName());
                    return Promise.of(handleException(tenantId, "/assignments", ex));
                });
    }

    /**
     * Revokes a role from multiple principals.
     *
     * HTTP: DELETE /assignments Request: AssignRoleRequest (roleId +
     * principalIds) Response: RoleAssignmentResponse (HTTP 200)
     *
     * GIVEN: Valid role and principal IDs WHEN: DELETE /assignments is called
     * THEN: Role is revoked from all principals
     *
     * @param tenantId the tenant ID from context
     * @param request the revocation request
     * @return Promise of HTTP response with revocation results
     */
    public Promise<HttpResponse> revokeRole(String tenantId, AssignRoleRequest request) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        long startTime = System.currentTimeMillis();
        return assignmentService.revokeRoleFromPrincipals(
                tenantId,
                request.getRoleId(),
                request.getPrincipalIds())
                .map(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.revoke.duration", duration);
                    metrics.incrementCounter("assignment.revoke.success",
                            "count", String.valueOf(result.successCount()));

                    RoleAssignmentResponse response = RoleAssignmentResponse.builder()
                            .roleId(request.getRoleId())
                            .successCount(result.successCount())
                            .failureCount(result.failureCount())
                            .message(String.format("Revoked role from %d principals", result.successCount()))
                            .timestamp(System.currentTimeMillis())
                            .build();

                    return ResponseBuilder.ok().json(response).build();
                })
                .then(Promise::of, ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.revoke.duration", duration);
                    metrics.incrementCounter("assignment.revoke.error", "type", ex.getClass().getSimpleName());
                    return Promise.of(handleException(tenantId, "/assignments", ex));
                });
    }

    /**
     * Gets all roles assigned to a principal.
     *
     * HTTP: GET /assignments/{principalId} Response: RoleListResponse (HTTP
     * 200)
     *
     * GIVEN: Valid principal ID WHEN: GET /assignments/{principalId} is called
     * THEN: List of assigned roles is returned
     *
     * @param tenantId the tenant ID from context
     * @param principalId the principal ID to query
     * @return Promise of HTTP response with assigned roles
     */
    public Promise<HttpResponse> getRolesForPrincipal(String tenantId, String principalId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(principalId, "principalId cannot be null");

        long startTime = System.currentTimeMillis();
        return assignmentService.getRolesForPrincipal(tenantId, principalId)
                .map(roles -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.getRoles.duration", duration);
                    metrics.incrementCounter("assignment.getRoles.success",
                            "count", String.valueOf(roles.size()));

                    var roleResponses = roles.stream()
                            .map(role -> RoleResponse.builder()
                            .tenantId(tenantId)
                            .roleId(role.getRoleId())
                            .roleName(role.getName())
                            .description(role.getDescription())
                            .permissions(role.getPermissions())
                            .isActive(!role.isSystemRole())
                            .createdAt(System.currentTimeMillis())
                            .updatedAt(System.currentTimeMillis())
                            .build())
                            .toList();

                    RoleListResponse response = RoleListResponse.builder()
                            .roles(roleResponses)
                            .totalCount(roleResponses.size())
                            .pageNumber(0)
                            .pageSize(roleResponses.size())
                            .build();

                    return ResponseBuilder.ok().json(response).build();
                })
                .then(Promise::of, ex -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("assignment.getRoles.duration", duration);
                    metrics.incrementCounter("assignment.getRoles.error", "type", ex.getClass().getSimpleName());
                    return Promise.of(handleException(tenantId, "/assignments/" + principalId, ex));
                });
    }

    /**
     * Handles exceptions and returns appropriate error response.
     */
    private HttpResponse handleException(String tenantId, String path, Throwable ex) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(500)
                .error("INTERNAL_SERVER_ERROR")
                .message(ex.getMessage() != null ? ex.getMessage() : "Internal server error")
                .timestamp(System.currentTimeMillis())
                .path(path)
                .build();

        return ResponseBuilder.internalServerError().json(errorResponse).build();
    }
}
