package com.ghatana.refactorer.server.auth;

/**
 * Thread-local storage for tenant context. This provides a way to access the current tenant ID

 * without passing it explicitly through all layers of the application.

 *

 * <p>The tenant ID is set by the TenantContextFilter early in the request pipeline and cleared

 * after the request is processed.

 *

 * @doc.type class

 * @doc.purpose Provide thread-local access to the current tenant identity for downstream services.

 * @doc.layer product

 * @doc.pattern Context Holder

 */

public final class TenantContextStorage {
    private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContextStorage() {
        // Utility class - all methods are static
    }

    /**
     * Gets the current tenant ID for this thread.
     *
     * @return The current tenant ID, or "default-tenant" if not set
     */
    public static String getCurrentTenantId() {
        String tenantId = CURRENT_TENANT_ID.get();
        return tenantId != null ? tenantId : "default-tenant";
    }

    /**
     * Sets the current tenant ID for this thread.
     *
     * @param tenantId The tenant ID to set (must not be null)
     */
    public static void setCurrentTenantId(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID must not be null");
        }
        CURRENT_TENANT_ID.set(tenantId);
    }

    /**
     * Clears the current tenant context. Should be called at the end of request processing.
     */
    public static void clear() {
        CURRENT_TENANT_ID.remove();
    }
}
