/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

/**
 * Thrown when a cross-tenant access violation is detected (AEP-007).
 *
 * <p>Thrown immediately without further processing when an operation attempts
 * to access resources belonging to a different tenant.
 *
 * @doc.type class
 * @doc.purpose Signals detected tenant isolation violations
 * @doc.layer product
 * @doc.pattern Exception
 * @since 1.2.0
 */
public final class AepTenantException extends AepException {

    public AepTenantException(String tenantId, String resource) {
        super(tenantId, "tenant.isolation", "Cross-tenant access denied for resource: " + resource);
    }

    public AepTenantException(String tenantId, String resource, String detail) {
        super(tenantId, "tenant.isolation",
              "Cross-tenant access denied for resource '" + resource + "': " + detail);
    }
}
