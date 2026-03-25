/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.http.server.servlet;

import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;

import java.time.Instant;

/**
 * Utility that mounts standardised health-check routes onto any
 * {@link RoutingServlet.Builder}.
 *
 * <p><b>Purpose</b><br>
 * Every Ghatana service must expose a {@code GET /health} and a
 * {@code GET /readiness} endpoint so that container orchestrators
 * (Kubernetes, Docker Compose, etc.) can perform liveness and readiness probes.
 * Before this helper existed each service inlined its own health-check snippet,
 * resulting in inconsistent JSON shapes and missing endpoints.
 *
 * <p><b>Design</b><br>
 * The utility is intentionally minimal — it does <em>not</em> check downstream
 * dependencies such as databases or caches.  If a service needs deep health
 * checks it should override the routes added here by registering a handler
 * <em>after</em> calling {@link #addHealthEndpoints}.
 *
 * <p><b>Response format</b><br>
 * Both endpoints return {@code 200 OK} with the JSON body:
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "service": "<serviceName>",
 *   "version": "<version>",
 *   "timestamp": "<ISO-8601>"
 * }
 * }</pre>
 *
 * <p><b>Usage Example</b><br>
 * <pre>{@code
 * RoutingServlet servlet = HealthCheckServlet
 *     .addHealthEndpoints(
 *         RoutingServlet.builder(eventloop),
 *         "auth-gateway",
 *         "1.0.0")
 *     .with(HttpMethod.POST, "/auth/login", loginHandler)
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Utility for mounting standardised /health and /readiness routes
 * @doc.layer platform
 * @doc.pattern Utility / Builder Extension
 */
public final class HealthCheckServlet {

    private HealthCheckServlet() {
        // Utility class — not instantiable
    }

    /**
     * Registers {@code GET /health} and {@code GET /readiness} on the given
     * builder and returns the same builder for further chaining.
     *
     * <p>Both routes respond immediately with {@code 200 OK} and a JSON body
     * describing service identity and current timestamp.
     *
     * @param builder     the {@link RoutingServlet.Builder} to augment
     * @param serviceName logical service name embedded in the JSON response
     *                    (e.g. {@code "auth-gateway"})
     * @param version     service version string embedded in the JSON response
     *                    (e.g. {@code "1.0.0"})
     * @return the same {@code builder} instance for fluent chaining
     * @throws NullPointerException if {@code builder}, {@code serviceName}, or
     *                              {@code version} is {@code null}
     */
    public static RoutingServlet.Builder addHealthEndpoints(
            RoutingServlet.Builder builder,
            String serviceName,
            String version) {

        return builder
                .with(HttpMethod.GET, "/health", request -> {
                    String body = buildResponseBody(serviceName, version, "UP");
                    return HttpResponse.ok200()
                            .withHeader(
                                    io.activej.http.HttpHeaders.CONTENT_TYPE,
                                    io.activej.http.HttpHeaderValue.of("application/json"))
                            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toPromise();
                })
                .with(HttpMethod.GET, "/readiness", request -> {
                    String body = buildResponseBody(serviceName, version, "READY");
                    return HttpResponse.ok200()
                            .withHeader(
                                    io.activej.http.HttpHeaders.CONTENT_TYPE,
                                    io.activej.http.HttpHeaderValue.of("application/json"))
                            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toPromise();
                });
    }

    private static String buildResponseBody(
            String serviceName, String version, String status) {
        return "{\"status\":\"" + status + "\""
                + ",\"service\":\"" + serviceName + "\""
                + ",\"version\":\"" + version + "\""
                + ",\"timestamp\":\"" + Instant.now() + "\"}";
    }
}
