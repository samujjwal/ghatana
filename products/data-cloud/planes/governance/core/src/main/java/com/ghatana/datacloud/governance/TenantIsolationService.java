/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.util.Objects;

/**
 * Centralized tenant isolation enforcement service.
 *
 * <p>Ensures that all data operations respect tenant boundaries and prevents
 * cross-tenant data access. This service validates that operations are scoped
 * to a single tenant and that tenant context is properly propagated.
 *
 * @doc.type class
 * @doc.purpose Centralized tenant isolation enforcement for all data operations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TenantIsolationService {

    /**
     * Validates that a tenant context is valid for operations.
     *
     * @param tenantId the tenant identifier to validate
     * @throws IllegalArgumentException if tenant ID is null, blank, or invalid
     */
    public void validateTenantContext(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    /**
     * Validates that a source tenant can access a target tenant's data.
     *
     * <p>By default, cross-tenant access is denied. This method can be
     * extended to support controlled cross-tenant access patterns.
     *
     * @param sourceTenantId the tenant requesting access
     * @param targetTenantId the tenant whose data is being accessed
     * @throws SecurityException if cross-tenant access is attempted
     */
    public void validateCrossTenantAccess(String sourceTenantId, String targetTenantId) {
        validateTenantContext(sourceTenantId);
        validateTenantContext(targetTenantId);

        if (!sourceTenantId.equals(targetTenantId)) {
            throw new SecurityException(
                "Cross-tenant access denied: tenant '" + sourceTenantId +
                "' cannot access data from tenant '" + targetTenantId + "'");
        }
    }

    /**
     * Validates that a resource belongs to the specified tenant.
     *
     * @param resourceTenantId the tenant that owns the resource
     * @param requestingTenantId the tenant requesting access
     * @throws SecurityException if resource belongs to a different tenant
     */
    public void validateResourceOwnership(String resourceTenantId, String requestingTenantId) {
        validateTenantContext(resourceTenantId);
        validateTenantContext(requestingTenantId);

        if (!resourceTenantId.equals(requestingTenantId)) {
            throw new SecurityException(
                "Resource ownership violation: resource belongs to tenant '" +
                resourceTenantId + "' but requested by tenant '" + requestingTenantId + "'");
        }
    }

    /**
     * Checks if a tenant is allowed to perform an operation on a resource type.
     *
     * <p>This is a placeholder for more sophisticated permission checks.
     * Currently, all tenants are allowed all operations on their own data.
     *
     * @param tenantId the tenant requesting the operation
     * @param resourceType the type of resource being accessed
     * @param operation the operation being performed (READ, WRITE, DELETE, etc.)
     * @return true if the operation is allowed
     */
    public boolean isOperationAllowed(String tenantId, String resourceType, String operation) {
        validateTenantContext(tenantId);
        // Placeholder: all operations allowed on own data
        return true;
    }
}
