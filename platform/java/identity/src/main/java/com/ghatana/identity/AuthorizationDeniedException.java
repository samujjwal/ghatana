/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

/**
 * Exception thrown when a principal lacks authorization to perform an operation.
 *
 * @doc.type class
 * @doc.purpose Authorization failure exception (403 Forbidden class)
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class AuthorizationDeniedException extends RuntimeException {

    private final String principal;
    private final String resource;
    private final String tenantId;

    public AuthorizationDeniedException(String principal, String resource, String tenantId) {
        super(String.format("Authorization denied: principal '%s' cannot access '%s' in tenant '%s'",
            principal, resource, tenantId));
        this.principal = principal;
        this.resource = resource;
        this.tenantId = tenantId;
    }

    public String principal() {
        return principal;
    }

    public String resource() {
        return resource;
    }

    public String tenantId() {
        return tenantId;
    }
}
