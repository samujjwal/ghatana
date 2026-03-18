package com.ghatana.refactorer.server.auth;

import com.ghatana.platform.governance.security.TenantContext;

/**
 * Thin façade that delegates to the canonical
 * {@link TenantContext} from {@code platform:java:governance}.
 *
 * <p><strong>Deprecated — use {@link TenantContext} directly.</strong>
 * This class exists only for backward compatibility during the migration from the
 * product-local ThreadLocal re-implementation to the canonical platform class.
 * All callers in this package should be migrated to import
 * {@code com.ghatana.platform.governance.security.TenantContext} instead.
 *
 * <h2>Migration</h2>
 * Replace every occurrence of:
 * <pre>
 *   TenantContextStorage.getCurrentTenantId()  →  TenantContext.getCurrentTenantId()
 *   TenantContextStorage.setCurrentTenantId(x) →  TenantContext.setCurrentTenantId(x)
 *   TenantContextStorage.clear()               →  TenantContext.clear()
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Deprecated delegate to canonical TenantContext; prevents divergence
 * @doc.layer product
 * @doc.pattern Context Holder, Facade (deprecated)
 * @deprecated Use {@link TenantContext} directly.
 */
@Deprecated(since = "2026-Q2", forRemoval = true)
public final class TenantContextStorage {

    private TenantContextStorage() {
        // Utility class — all methods are static
    }

    /**
     * @deprecated Use {@link TenantContext#getCurrentTenantId()} directly.
     */
    @Deprecated(since = "2026-Q2", forRemoval = true)
    public static String getCurrentTenantId() {
        return TenantContext.getCurrentTenantId();
    }

    /**
     * @deprecated Use {@link TenantContext#setCurrentTenantId(String)} directly.
     */
    @Deprecated(since = "2026-Q2", forRemoval = true)
    public static void setCurrentTenantId(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID must not be null");
        }
        TenantContext.setCurrentTenantId(tenantId);
    }

    /**
     * @deprecated Use {@link TenantContext#clear()} directly.
     */
    @Deprecated(since = "2026-Q2", forRemoval = true)
    public static void clear() {
        TenantContext.clear();
    }
}

