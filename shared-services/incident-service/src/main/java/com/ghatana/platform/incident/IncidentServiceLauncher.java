/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.port.JwtTokenProviders;
import io.activej.eventloop.Eventloop;
import io.activej.http.*;
import io.activej.inject.annotation.Provides;
import io.activej.launcher.Launcher;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.activej.http.HttpMethod.*;

/**
 * Incident Service providing HTTP endpoints for kill switch and graceful degradation management.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/v1/incident/kill-switch/activate} — Activate kill switch for a tenant</li>
 *   <li>{@code POST /api/v1/incident/kill-switch/deactivate} — Deactivate kill switch for a tenant</li>
 *   <li>{@code GET  /api/v1/incident/kill-switch/status/{tenantId}} — Check kill switch status</li>
 *   <li>{@code POST /api/v1/incident/kill-switch/global/activate} — Activate global kill switch</li>
 *   <li>{@code POST /api/v1/incident/degradation/set} — Set degradation mode for a tenant</li>
 *   <li>{@code GET  /api/v1/incident/degradation/status/{tenantId}} — Get degradation mode</li>
 *   <li>{@code GET  /health} — Health probe</li>
 *   <li>{@code GET  /metrics} — Basic metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Incident management HTTP service for kill switch and graceful degradation
 * @doc.layer shared-service
 * @doc.pattern Service Launcher
 */
public class IncidentServiceLauncher extends HttpServerLauncher {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceLauncher.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /** HTTP header used to propagate correlation IDs across service boundaries. */
    static final HttpHeader CORRELATION_ID_HEADER = HttpHeaders.of("X-Correlation-ID");

    // ─── Providers ───────────────────────────────────────────────────────────

    @Provides
    KillSwitchService killSwitchService(Optional<KillSwitchService> postgresService) {
        // Prefer PostgreSQL implementation, fall back to in-memory
        return postgresService.orElse(new InMemoryKillSwitchService());
    }

    @Provides
    GracefulDegradationManager degradationManager(Optional<GracefulDegradationManager> redisManager) {
        // Prefer Redis implementation, fall back to in-memory
        return redisManager.orElse(new InMemoryGracefulDegradationManager());
    }

    @Provides
    JwtTokenProvider jwtTokenProvider() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            String env = System.getenv().getOrDefault("ENVIRONMENT", "development");
            if ("production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env)) {
                throw new IllegalStateException(
                    "JWT_SECRET must be set to at least 32 characters in production. " +
                    "Set the JWT_SECRET environment variable before deploying.");
            }
            log.warn("JWT_SECRET not set or too short (< 32 chars) — " +
                     "using INSECURE development default. NEVER deploy this to production.");
            secret = "dev-incident-service-jwt-secret-change-me-in-prod!";
        }
        return JwtTokenProviders.fromSharedSecret(secret, 15 * 60 * 1000L); // 15 minutes
    }

    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withThreadName("incident-service")
                .build();
    }

    // ─── Servlet ─────────────────────────────────────────────────────────────

    @Provides
    AsyncServlet servlet(
            Eventloop eventloop,
            KillSwitchService killSwitchService,
            GracefulDegradationManager degradationManager,
            JwtTokenProvider jwtTokenProvider
    ) {
        return RoutingServlet.builder(eventloop)

            // ── Kill Switch: Activate ───────────────────────────────────────────
            .with(POST, "/api/v1/incident/kill-switch/activate", request ->
                request.loadBody().then(body -> {
                    String correlationId = extractCorrelationId(request);
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (!isAuthorized(authHeader, jwtTokenProvider)) {
                        return Promise.of(HttpResponse.ofCode(401)
                                .withJson(errorJson(401, "UNAUTHORIZED", "Invalid or missing JWT token"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }

                    String bodyStr = body.getString(StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> payload = objectMapper.readValue(bodyStr, Map.class);
                        String tenantId = payload.get("tenantId");
                        String reason = payload.get("reason");
                        String incidentId = payload.get("incidentId");

                        if (tenantId == null || tenantId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                                    .withJson(errorJson(400, "MISSING_TENANT_ID", "tenantId is required"))
                                    .withHeader(CORRELATION_ID_HEADER, correlationId)
                                    .build());
                        }

                        return killSwitchService.activate(tenantId, reason, incidentId)
                                .map($ -> {
                                    log.info("Kill switch activated correlationId={} tenantId={} incidentId={}", 
                                            correlationId, tenantId, incidentId);
                                    return HttpResponse.ok200()
                                            .withJson("{\"status\":\"activated\"}")
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build();
                                })
                                .then(Promise::of, error -> {
                                    log.error("Failed to activate kill switch correlationId={} tenantId={}", 
                                            correlationId, tenantId, error);
                                    return Promise.of(HttpResponse.ofCode(500)
                                            .withJson(errorJson(500, "INTERNAL_ERROR", "Failed to activate kill switch"))
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build());
                                });
                    } catch (Exception e) {
                        log.error("Invalid request payload correlationId={}", correlationId, e);
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "INVALID_PAYLOAD", "Invalid JSON payload"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }
                })
            )

            // ── Kill Switch: Deactivate ─────────────────────────────────────────
            .with(POST, "/api/v1/incident/kill-switch/deactivate", request ->
                request.loadBody().then(body -> {
                    String correlationId = extractCorrelationId(request);
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (!isAuthorized(authHeader, jwtTokenProvider)) {
                        return Promise.of(HttpResponse.ofCode(401)
                                .withJson(errorJson(401, "UNAUTHORIZED", "Invalid or missing JWT token"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }

                    String bodyStr = body.getString(StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> payload = objectMapper.readValue(bodyStr, Map.class);
                        String tenantId = payload.get("tenantId");
                        String reason = payload.get("reason");

                        if (tenantId == null || tenantId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                                    .withJson(errorJson(400, "MISSING_TENANT_ID", "tenantId is required"))
                                    .withHeader(CORRELATION_ID_HEADER, correlationId)
                                    .build());
                        }

                        return killSwitchService.deactivate(tenantId, reason)
                                .map($ -> {
                                    log.info("Kill switch deactivated correlationId={} tenantId={}", correlationId, tenantId);
                                    return HttpResponse.ok200()
                                            .withJson("{\"status\":\"deactivated\"}")
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build();
                                })
                                .then(Promise::of, error -> {
                                    log.error("Failed to deactivate kill switch correlationId={} tenantId={}", 
                                            correlationId, tenantId, error);
                                    return Promise.of(HttpResponse.ofCode(500)
                                            .withJson(errorJson(500, "INTERNAL_ERROR", "Failed to deactivate kill switch"))
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build());
                                });
                    } catch (Exception e) {
                        log.error("Invalid request payload correlationId={}", correlationId, e);
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "INVALID_PAYLOAD", "Invalid JSON payload"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }
                })
            )

            // ── Kill Switch: Status ───────────────────────────────────────────────
            .with(GET, "/api/v1/incident/kill-switch/status/:tenantId", request -> {
                String correlationId = extractCorrelationId(request);
                String tenantId = request.getPathParameter("tenantId");

                if (tenantId == null || tenantId.isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson(errorJson(400, "MISSING_TENANT_ID", "tenantId is required"))
                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                            .build());
                }

                return killSwitchService.isActive(tenantId)
                        .map(isActive -> {
                            String json = String.format("{\"tenantId\":\"%s\",\"isActive\":%s}", 
                                    tenantId, isActive);
                            return HttpResponse.ok200()
                                    .withJson(json)
                                    .withHeader(CORRELATION_ID_HEADER, correlationId)
                                    .build();
                        });
            })

            // ── Kill Switch: Global Activate ────────────────────────────────────
            .with(POST, "/api/v1/incident/kill-switch/global/activate", request ->
                request.loadBody().then(body -> {
                    String correlationId = extractCorrelationId(request);
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (!isAuthorized(authHeader, jwtTokenProvider)) {
                        return Promise.of(HttpResponse.ofCode(401)
                                .withJson(errorJson(401, "UNAUTHORIZED", "Invalid or missing JWT token"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }

                    String bodyStr = body.getString(StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> payload = objectMapper.readValue(bodyStr, Map.class);
                        String reason = payload.get("reason");
                        String incidentId = payload.get("incidentId");

                        return killSwitchService.activateGlobal(reason, incidentId)
                                .map($ -> {
                                    log.warn("GLOBAL KILL SWITCH ACTIVATED correlationId={} incidentId={}", 
                                            correlationId, incidentId);
                                    return HttpResponse.ok200()
                                            .withJson("{\"status\":\"global_activated\"}")
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build();
                                })
                                .then(Promise::of, error -> {
                                    log.error("Failed to activate global kill switch correlationId={}", correlationId, error);
                                    return Promise.of(HttpResponse.ofCode(500)
                                            .withJson(errorJson(500, "INTERNAL_ERROR", "Failed to activate global kill switch"))
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build());
                                });
                    } catch (Exception e) {
                        log.error("Invalid request payload correlationId={}", correlationId, e);
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "INVALID_PAYLOAD", "Invalid JSON payload"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }
                })
            )

            // ── Degradation: Set Mode ────────────────────────────────────────────
            .with(POST, "/api/v1/incident/degradation/set", request ->
                request.loadBody().then(body -> {
                    String correlationId = extractCorrelationId(request);
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    if (!isAuthorized(authHeader, jwtTokenProvider)) {
                        return Promise.of(HttpResponse.ofCode(401)
                                .withJson(errorJson(401, "UNAUTHORIZED", "Invalid or missing JWT token"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }

                    String bodyStr = body.getString(StandardCharsets.UTF_8);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> payload = objectMapper.readValue(bodyStr, Map.class);
                        String tenantId = payload.get("tenantId");
                        String mode = payload.get("mode");

                        if (tenantId == null || tenantId.isBlank()) {
                            return Promise.of(HttpResponse.ofCode(400)
                                    .withJson(errorJson(400, "MISSING_TENANT_ID", "tenantId is required"))
                                    .withHeader(CORRELATION_ID_HEADER, correlationId)
                                    .build());
                        }

                        DegradationMode degradationMode = DegradationMode.valueOf(mode.toUpperCase());
                        return degradationManager.setMode(tenantId, degradationMode)
                                .map($ -> {
                                    log.info("Degradation mode set correlationId={} tenantId={} mode={}", 
                                            correlationId, tenantId, mode);
                                    return HttpResponse.ok200()
                                            .withJson(String.format("{\"status\":\"mode_set\",\"mode\":\"%s\"}", mode))
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build();
                                })
                                .then(Promise::of, error -> {
                                    log.error("Failed to set degradation mode correlationId={} tenantId={}", 
                                            correlationId, tenantId, error);
                                    return Promise.of(HttpResponse.ofCode(500)
                                            .withJson(errorJson(500, "INTERNAL_ERROR", "Failed to set degradation mode"))
                                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                                            .build());
                                });
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid degradation mode correlationId={}", correlationId, e);
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "INVALID_MODE", "Invalid degradation mode"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    } catch (Exception e) {
                        log.error("Invalid request payload correlationId={}", correlationId, e);
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson(errorJson(400, "INVALID_PAYLOAD", "Invalid JSON payload"))
                                .withHeader(CORRELATION_ID_HEADER, correlationId)
                                .build());
                    }
                })
            )

            // ── Degradation: Get Status ───────────────────────────────────────────
            .with(GET, "/api/v1/incident/degradation/status/:tenantId", request -> {
                String correlationId = extractCorrelationId(request);
                String tenantId = request.getPathParameter("tenantId");

                if (tenantId == null || tenantId.isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson(errorJson(400, "MISSING_TENANT_ID", "tenantId is required"))
                            .withHeader(CORRELATION_ID_HEADER, correlationId)
                            .build());
                }

                return degradationManager.getMode(tenantId)
                        .map(mode -> {
                            String json = String.format("{\"tenantId\":\"%s\",\"mode\":\"%s\"", tenantId, mode);
                            return HttpResponse.ok200()
                                    .withJson(json)
                                    .withHeader(CORRELATION_ID_HEADER, correlationId)
                                    .build();
                        });
            })

            // ── Health ──────────────────────────────────────────────────────────────
            .with(GET, "/health", request ->
                HttpResponse.ok200()
                    .withJson("{\"status\":\"UP\",\"service\":\"incident-service\",\"version\":\"1.0.0\"}")
                    .build()
                    .toPromise()
            )

            // ── Metrics ─────────────────────────────────────────────────────────────
            .with(GET, "/metrics", request ->
                HttpResponse.ok200()
                    .withJson("{\"active_kill_switches\":0,\"active_degradations\":0}")
                    .build()
                    .toPromise()
            )
            .build();
    }

    // ─── Entry point ─────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        log.info("Starting Incident Service v1...");
        IncidentServiceLauncher launcher = new IncidentServiceLauncher();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping Incident Service gracefully...");
            try {
                launcher.shutdown();
                log.info("Incident Service stopped successfully");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }, "incident-service-shutdown"));
        
        launcher.launch(args);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extracts the {@code X-Correlation-ID} header from an incoming request, or generates
     * a new UUID if absent. The returned value is safe to log and return in response headers.
     */
    static String extractCorrelationId(HttpRequest request) {
        String id = request.getHeader(CORRELATION_ID_HEADER);
        return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }

    /** Validates JWT token from Authorization header. */
    private boolean isAuthorized(String authHeader, JwtTokenProvider jwtTokenProvider) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String token = authHeader.substring(7);
        return jwtTokenProvider.validateToken(token);
    }

    static String errorJson(int status, String code, String message) {
        return writeJson(standardError(status, code, message));
    }

    static String errorJson(int status, String code, String message, String details) {
        return writeJson(standardError(status, code, message, details));
    }

    static ErrorResponse standardError(int status, String code, String message) {
        return ErrorResponse.of(status, code, message);
    }

    static ErrorResponse standardError(int status, String code, String message, String details) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .details(details)
                .build();
    }

    private static String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize error response", exception);
        }
    }
}
