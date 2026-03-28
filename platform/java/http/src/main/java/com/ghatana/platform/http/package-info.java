/**
 * Platform HTTP server and client utilities: request routing, response building,
 * health checks, security filters, and the shared OkHttp adapter.
 *
 * <h2>HTTP Response Building (HTTP-003 / HTTP-004)</h2>
 *
 * <p>Always use {@link com.ghatana.platform.http.server.response.ResponseBuilder} for
 * constructing HTTP responses in handlers. Never write raw
 * {@code HttpResponse.ofCode(n).withJson("{...}")} strings inline:</p>
 *
 * <pre>{@code
 * // Success responses:
 * return ResponseBuilder.ok().json(responseBody).build();
 * return ResponseBuilder.status(201).json(created).build();
 *
 * // Standard error responses — always pair with ErrorResponse:
 * return ResponseBuilder.badRequest()
 *     .json(ErrorResponse.of(400, "VALIDATION_ERROR", "Field 'name' is required"))
 *     .build();
 * return ResponseBuilder.status(401)
 *     .json(ErrorResponse.of(401, "UNAUTHORIZED", "Bearer token required"))
 *     .build();
 * return ResponseBuilder.status(403)
 *     .json(ErrorResponse.of(403, "FORBIDDEN", "Insufficient permissions"))
 *     .build();
 * return ResponseBuilder.status(404)
 *     .json(ErrorResponse.of(404, "NOT_FOUND", "Resource not found"))
 *     .build();
 * return ResponseBuilder.status(500)
 *     .json(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Unexpected error"))
 *     .build();
 * }</pre>
 *
 * <h2>HTTP Header Conventions (HTTP-003)</h2>
 *
 * <ul>
 * <li>Set {@code Content-Type: application/json} — {@code ResponseBuilder.json()} does
 *     this automatically.</li>
 * <li>Include {@code X-Request-Id} from the inbound request in all responses so clients
 *     can correlate logs.</li>
 * <li>Rate-limited responses must include {@code X-RateLimit-Limit},
 *     {@code X-RateLimit-Remaining}, {@code X-RateLimit-Reset}, and
 *     {@code Retry-After} headers.</li>
 * <li>Do not set {@code Access-Control-*} headers in service code — let the gateway
 *     or reverse proxy handle CORS.</li>
 * </ul>
 *
 * <h2>HTTP Client Usage</h2>
 *
 * <p>Use {@link com.ghatana.platform.http.client.HttpClientFactory} for all
 * outbound HTTP calls. Do not create bare {@code OkHttpClient} instances:</p>
 *
 * <pre>{@code
 * // Default client (shared, connection-pooled, 30s connect / 10s read):
 * OkHttpAdapter client = HttpClientFactory.createDefaultAdapter(metrics);
 *
 * // Tenant-scoped client with per-tenant rate limiting:
 * OkHttpAdapter tenantClient = HttpClientFactory.createTenantAdapter(tenantId, metrics, rps);
 * }</pre>
 *
 * <h2>Health Endpoints</h2>
 *
 * <p>Register {@code /health} and {@code /readiness} using
 * {@link com.ghatana.platform.http.server.servlet.HealthCheckServlet}:</p>
 *
 * <pre>{@code
 * HealthCheckServlet.addHealthEndpoints(routingServletBuilder, "service-name", "1.0.0");
 * }</pre>
 */
package com.ghatana.platform.http;
