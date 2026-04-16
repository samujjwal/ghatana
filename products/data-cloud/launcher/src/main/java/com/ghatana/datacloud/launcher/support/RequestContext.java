/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.support;

import org.slf4j.MDC;

import java.io.Closeable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Thread-local request context holder for structured logging via SLF4J MDC.
 *
 * <p>Sets standard MDC keys ({@code requestId}, {@code tenantId}) on the
 * current thread for the duration of an HTTP handler invocation so that every
 * log statement emitted within that scope automatically carries the correlation
 * ID without explicit parameter threading.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * String correlationId = http.resolveCorrelationId(request);
 * String tenantId      = http.requireTenantIdOrFail(request);
 *
 * try (RequestContext ctx = RequestContext.bind(correlationId, tenantId)) {
 *     return someService.handle(request)
 *         .map(result -> http.jsonResponse(result, correlationId));
 * }
 * }</pre>
 *
 * <p>The returned {@link Closeable} removes the MDC context when closed.
 * Always use in a try-with-resources block. ActiveJ runs handlers on the
 * eventloop thread; a straightforward try block covers the synchronous
 * dispatch phase — asynchronous continuations do not require MDC because
 * those callbacks resume on the same eventloop thread where the MDC is still
 * active until the scope is exited.
 *
 * @doc.type class
 * @doc.purpose Thread-scoped MDC correlation ID and tenant binding for handlers
 * @doc.layer product
 * @doc.pattern Context Holder, Decorator
 */
public final class RequestContext implements Closeable {

    static final String KEY_REQUEST_ID = "requestId";
    static final String KEY_TENANT_ID  = "tenantId";
    static final String KEY_TRACE_ID   = "traceId";
    static final String KEY_HTTP_METHOD = "httpMethod";
    static final String KEY_HTTP_PATH = "httpPath";
    static final String KEY_PRINCIPAL = "principal";

    private final Set<String> keysToClear;

    private RequestContext(Set<String> keysToClear) {
        this.keysToClear = keysToClear;
    }

    /**
     * Binds {@code requestId} and {@code tenantId} to the current thread's MDC
     * and returns a {@link Closeable} that clears them on close.
     *
     * @param requestId  correlation / request ID (must not be null or blank)
     * @param tenantId   tenant identifier (must not be null or blank)
     * @return closeable scope that clears MDC keys on close
     */
    public static RequestContext bind(String requestId, String tenantId) {
        MDC.put(KEY_REQUEST_ID, requestId);
        MDC.put(KEY_TENANT_ID, tenantId);
        return new RequestContext(new LinkedHashSet<>(Set.of(KEY_REQUEST_ID, KEY_TENANT_ID)));
    }

    public static RequestContext bind(String requestId, String tenantId, String traceId, String method, String path) {
        MDC.put(KEY_REQUEST_ID, requestId);
        MDC.put(KEY_TENANT_ID, tenantId);
        MDC.put(KEY_TRACE_ID, traceId);
        MDC.put(KEY_HTTP_METHOD, method);
        MDC.put(KEY_HTTP_PATH, path);
        return new RequestContext(new LinkedHashSet<>(Set.of(
                KEY_REQUEST_ID,
                KEY_TENANT_ID,
                KEY_TRACE_ID,
                KEY_HTTP_METHOD,
                KEY_HTTP_PATH)));
    }

    /**
     * Binds only the {@code requestId} to MDC (for contexts where the tenant
     * is not yet resolved).
     *
     * @param requestId  correlation / request ID (must not be null or blank)
     * @return closeable scope
     */
    public static RequestContext bindRequestId(String requestId) {
        MDC.put(KEY_REQUEST_ID, requestId);
        return new RequestContext(new LinkedHashSet<>(Set.of(KEY_REQUEST_ID)));
    }

    public static RequestContext bindRequest(String requestId, String traceId, String method, String path) {
        MDC.put(KEY_REQUEST_ID, requestId);
        MDC.put(KEY_TRACE_ID, traceId);
        MDC.put(KEY_HTTP_METHOD, method);
        MDC.put(KEY_HTTP_PATH, path);
        return new RequestContext(new LinkedHashSet<>(Set.of(
                KEY_REQUEST_ID,
                KEY_TRACE_ID,
                KEY_HTTP_METHOD,
                KEY_HTTP_PATH)));
    }

    public static RequestContext bindPrincipal(String principal) {
        MDC.put(KEY_PRINCIPAL, principal);
        return new RequestContext(new LinkedHashSet<>(Set.of(KEY_PRINCIPAL)));
    }

    @Override
    public void close() {
        for (String key : keysToClear) {
            MDC.remove(key);
        }
    }
}
