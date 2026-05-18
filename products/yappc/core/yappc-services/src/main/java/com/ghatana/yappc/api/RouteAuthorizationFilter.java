/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.security.rbac.AccessDeniedException;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Route-level authorization filter that enforces the canonical route/action authorization registry.
 *
 * <p>This filter wraps each route to ensure that:
 * <ul>
 *   <li>The route is registered in the authorization registry</li>
 *   <li>The authenticated principal has the required permissions</li>
 *   <li>Resource scope (tenant/workspace/project/artifact) is validated</li>
 *   <li>All authorization decisions are logged for audit</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Route-level authorization filter enforcing canonical authorization registry
 * @doc.layer api
 * @doc.pattern Filter
 */
public final class RouteAuthorizationFilter {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteAuthorizationRegistry registry;

    public RouteAuthorizationFilter(@NotNull RouteAuthorizationRegistry registry) {
        this.registry = registry;
    }

    /**
     * Applies route-level authorization to an HTTP request.
     *
     * @param request the HTTP request
     * @param delegate the servlet to delegate to if authorization succeeds
     * @return HTTP response or delegated result
     */
    public Promise<HttpResponse> apply(@NotNull HttpRequest request, @NotNull io.activej.http.AsyncServlet delegate) {
        try {
            registry.authorize(request);
            return delegate.serve(request);
        } catch (AccessDeniedException e) {
            log.warn("Route authorization denied: {} {} - {}",
                request.getMethod(), request.getRelativePath(), e.getMessage());
            String errorJson = toJsonError("Forbidden", e.getMessage());
            return Promise.of(HttpResponse.ofCode(403)
                .withJson(errorJson)
                .build());
        } catch (Exception e) {
            log.error("Unexpected error during route authorization: {} {} - {}",
                request.getMethod(), request.getRelativePath(), e.getMessage(), e);
            String errorJson = toJsonError("Internal authorization error", "Internal authorization error");
            return Promise.of(HttpResponse.ofCode(500)
                .withJson(errorJson)
                .build());
        }
    }

    /**
     * P0: Safe JSON error serialization to prevent injection attacks and malformed JSON.
     * Properly escapes special characters in error messages.
     */
    private String toJsonError(String errorType, String message) {
        StringBuilder json = new StringBuilder();
        json.append("{\"error\":\"");
        json.append(escapeJsonString(errorType));
        if (message != null && !message.isEmpty()) {
            json.append(": ");
            json.append(escapeJsonString(message));
        }
        json.append("\"}");
        return json.toString();
    }

    /**
     * P0: Escape special characters for JSON string literals.
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
