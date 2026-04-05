/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;

/**
 * Service for enforcing role-based access control (RBAC) policies.
 *
 * <p>Checks whether an authenticated principal (agent, user, service) is
 * permitted to perform a specific operation on a resource. Integrates with
 * the governance module for policy evaluation.
 *
 * @doc.type interface
 * @doc.purpose Role-based access control enforcement
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AuthorizationService {

    /**
     * Check if a principal is authorized to perform an operation on a resource.
     *
     * @param tenantId   tenant scope
     * @param principal  the authenticated agent or user
     * @param resource   the resource being accessed (e.g., "collection:read", "job:execute")
     * @return true if permitted, false otherwise
     */
    Promise<Boolean> isAuthorized(String tenantId, String principal, String resource);

    /**
     * Check if a principal has all required scopes.
     *
     * @param tenantId   tenant scope
     * @param principal  the authenticated agent or user
     * @param requiredScope the scope to check (e.g., "read", "write", "admin")
     * @return true if principal has the scope, false otherwise
     */
    Promise<Boolean> hasScope(String tenantId, String principal, String requiredScope);

    /**
     * Enforce authorization: throw exception if not permitted.
     *
     * @param tenantId   tenant scope
     * @param principal  the authenticated agent or user
     * @param resource   the resource being accessed
     * @throws AuthorizationDeniedException if not permitted
     * @return void if authorized
     */
    Promise<Void> enforce(String tenantId, String principal, String resource);
}
