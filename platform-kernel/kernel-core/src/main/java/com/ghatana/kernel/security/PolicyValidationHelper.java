/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

/**
 * Shared validation helper for policy-related identifiers.
 *
 * <p>Provides reusable validation logic for tenant, principal, and facility IDs
 * across all products using the Kernel. This ensures consistent validation rules
 * and prevents product-specific divergence in identifier formats.</p>
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Tenant IDs: 2-50 alphanumeric characters with hyphens</li>
 *   <li>Principal IDs: 2-100 alphanumeric characters with hyphens/underscores</li>
 *   <li>Facility IDs: 2-50 alphanumeric characters with hyphens (optional)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Shared validation helper for policy-related identifiers
 * @doc.layer core
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class PolicyValidationHelper {

    private PolicyValidationHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a tenant ID is properly formatted.
     *
     * <p>Tenant IDs must be 2-50 alphanumeric characters with hyphens allowed.
     * This format is used across all products for tenant isolation.</p>
     *
     * @param tenantId the tenant ID to validate
     * @throws IllegalArgumentException if the tenant ID is null, blank, or invalid
     */
    public static void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be blank");
        }
        // Tenant IDs should be alphanumeric with hyphens, 2-50 characters
        if (!tenantId.matches("^[a-zA-Z0-9-]{2,50}$")) {
            throw new IllegalArgumentException(
                "Tenant ID must be 2-50 alphanumeric characters with hyphens, but was: " + tenantId
            );
        }
    }

    /**
     * Validates that a principal ID is properly formatted.
     *
     * <p>Principal IDs must be 2-100 alphanumeric characters with hyphens
     * and underscores allowed. This format accommodates various user ID schemes
     * across products.</p>
     *
     * @param principalId the principal ID to validate
     * @throws IllegalArgumentException if the principal ID is null, blank, or invalid
     */
    public static void validatePrincipalId(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("Principal ID cannot be blank");
        }
        // Principal IDs should be alphanumeric with hyphens/underscores, 2-100 characters
        if (!principalId.matches("^[a-zA-Z0-9_-]{2,100}$")) {
            throw new IllegalArgumentException(
                "Principal ID must be 2-100 alphanumeric characters with hyphens/underscores, but was: " + principalId
            );
        }
    }

    /**
     * Validates that a facility ID is properly formatted (if provided).
     *
     * <p>Facility IDs are optional for non-facility users. When provided,
     * they must be 2-50 alphanumeric characters with hyphens allowed.</p>
     *
     * @param facilityId the facility ID to validate (may be null/blank for non-facility users)
     * @throws IllegalArgumentException if the facility ID is provided but invalid
     */
    public static void validateFacilityId(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return; // Optional for non-facility users
        }
        // Facility IDs should be alphanumeric with hyphens, 2-50 characters
        if (!facilityId.matches("^[a-zA-Z0-9-]{2,50}$")) {
            throw new IllegalArgumentException(
                "Facility ID must be 2-50 alphanumeric characters with hyphens, but was: " + facilityId
            );
        }
    }

    /**
     * Validates tenant scope for a request context.
     *
     * <p>Ensures the tenant in the request matches the tenant in the session context.
     * This prevents cross-tenant data access attempts.</p>
     *
     * @param requestTenantId the tenant ID from the request
     * @param sessionTenantId the tenant ID from the session context
     * @throws IllegalArgumentException if tenant IDs do not match
     */
    public static void validateTenantScope(String requestTenantId, String sessionTenantId) {
        if (requestTenantId == null || requestTenantId.isBlank()) {
            throw new IllegalArgumentException("Request tenant ID cannot be blank");
        }
        if (sessionTenantId == null || sessionTenantId.isBlank()) {
            throw new IllegalArgumentException("Session tenant ID cannot be blank");
        }
        
        validateTenantId(requestTenantId);
        validateTenantId(sessionTenantId);
        
        if (!requestTenantId.equals(sessionTenantId)) {
            throw new IllegalArgumentException(
                "Tenant ID mismatch: request tenant '" + requestTenantId + 
                "' does not match session tenant '" + sessionTenantId + "'"
            );
        }
    }

    /**
     * Validates facility scope for a request context.
     *
     * <p>Ensures the principal has access to the specified facility.
     * Admin and certain elevated roles may have broader facility access.</p>
     *
     * @param facilityId the facility ID to validate (may be null/blank)
     * @param role the user's role for access determination
     * @param elevatedRoles roles that have broader facility access (e.g., admin, fchv)
     * @throws IllegalArgumentException if facility access is not permitted
     */
    public static void validateFacilityScope(String facilityId, String role, String... elevatedRoles) {
        if (facilityId == null || facilityId.isBlank()) {
            return; // No facility scope required
        }
        
        validateFacilityId(facilityId);
        
        // Check if role has elevated facility access
        if (elevatedRoles != null) {
            for (String elevatedRole : elevatedRoles) {
                if (elevatedRole.equals(role)) {
                    return; // Skip facility scope check for elevated roles
                }
            }
        }
        
        // For non-elevated roles, the specific facility access check
        // is deferred to product-specific policy evaluators
    }

    /**
     * Checks if a tenant ID is valid without throwing an exception.
     *
     * @param tenantId the tenant ID to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidTenantId(String tenantId) {
        try {
            validateTenantId(tenantId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if a principal ID is valid without throwing an exception.
     *
     * @param principalId the principal ID to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidPrincipalId(String principalId) {
        try {
            validatePrincipalId(principalId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks if a facility ID is valid without throwing an exception.
     *
     * @param facilityId the facility ID to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidFacilityId(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return true; // Optional, so null/blank is valid
        }
        try {
            validateFacilityId(facilityId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
