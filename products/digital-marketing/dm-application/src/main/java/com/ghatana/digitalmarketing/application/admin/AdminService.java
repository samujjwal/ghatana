package com.ghatana.digitalmarketing.application.admin;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Service interface for operator/admin flows.
 *
 * @doc.type class
 * @doc.purpose Defines operations for platform administration (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AdminService {

    /**
     * Create a tenant.
     *
     * @param ctx     operation context
     * @param request tenant creation request
     * @return the created tenant
     */
    Promise<Tenant> createTenant(DmOperationContext ctx, CreateTenantRequest request);

    /**
     * Fetch a tenant by ID.
     *
     * @param ctx      operation context
     * @param tenantId tenant ID
     * @return the tenant
     */
    Promise<Tenant> getTenant(DmOperationContext ctx, String tenantId);

    /**
     * Update a tenant.
     *
     * @param ctx      operation context
     * @param tenantId tenant ID
     * @param request  update request
     * @return updated tenant
     */
    Promise<Tenant> updateTenant(DmOperationContext ctx, String tenantId, UpdateTenantRequest request);

    /**
     * Create a user.
     *
     * @param ctx     operation context
     * @param request user creation request
     * @return the created user
     */
    Promise<User> createUser(DmOperationContext ctx, CreateUserRequest request);

    /**
     * Update user roles.
     *
     * @param ctx     operation context
     * @param userId  user ID
     * @param roles   new roles
     * @return updated user
     */
    Promise<User> updateUserRoles(DmOperationContext ctx, String userId, List<String> roles);

    /**
     * Fetch audit log.
     *
     * @param ctx   operation context
     * @param limit max results
     * @return audit log entries
     */
    Promise<List<AuditLogEntry>> fetchAuditLog(DmOperationContext ctx, int limit);

    /**
     * Get system health.
     *
     * @param ctx operation context
     * @return system health status
     */
    Promise<SystemHealth> getSystemHealth(DmOperationContext ctx);

    // ── Request types ─────────────────────────────────────────────────────────

    record CreateTenantRequest(
        String name,
        String domain,
        Map<String, String> metadata
    ) {}

    record UpdateTenantRequest(
        String name,
        String status,
        Map<String, String> metadata
    ) {}

    record CreateUserRequest(
        String tenantId,
        String email,
        String name,
        List<String> roles
    ) {}

    record Tenant(
        String tenantId,
        String name,
        String domain,
        String status,
        String createdAt,
        Map<String, String> metadata
    ) {}

    record User(
        String userId,
        String tenantId,
        String email,
        String name,
        List<String> roles,
        String createdAt
    ) {}

    record AuditLogEntry(
        String entryId,
        String timestamp,
        String userId,
        String action,
        String resource,
        String details
    ) {}

    record SystemHealth(
        boolean isHealthy,
        Map<String, String> components,
        String lastCheckedAt
    ) {}
}
