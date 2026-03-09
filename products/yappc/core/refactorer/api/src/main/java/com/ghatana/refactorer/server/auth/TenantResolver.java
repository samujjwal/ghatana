package com.ghatana.refactorer.server.auth;

import io.activej.http.HttpRequest;

/**
 * Utility methods for storing and retrieving {@link TenantContext} from request attachments.
 *
 * @doc.type class
 * @doc.purpose Inspect HTTP requests and build TenantContext instances consistently.
 * @doc.layer product
 * @doc.pattern Resolver
 */
public final class TenantResolver {
    private TenantResolver() {}

    public static void attach(HttpRequest request, TenantContext context) {
        request.attach(TenantContext.class, context);
    }

    public static TenantContext get(HttpRequest request) {
        return request.getAttachment(TenantContext.class);
    }

    public static TenantContext require(HttpRequest request) {
        TenantContext context = get(request);
        if (context == null) {
            throw new IllegalStateException("Tenant context not available on request");
        }
        return context;
    }
}
