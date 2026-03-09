package com.ghatana.platform.observability.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.trace.TraceStorage;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP handler for health check endpoints.
 * <p>
 * Handles:
 * <ul>
 *   <li>GET /health - Liveness probe (always returns 200 OK if service is running)</li>
 *   <li>GET /health/ready - Readiness probe (checks TraceStorage health)</li>
 * </ul>
 * </p>
 * <p>
 * Used by Kubernetes for:
 * <ul>
 *   <li>Liveness - Restart container if unhealthy</li>
 *   <li>Readiness - Route traffic only if ready</li>
 * </ul>
 * </p>
 *
 * @author Ghatana Platform Team
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose HTTP handler for Kubernetes liveness and readiness health probes
 * @doc.layer observability
 * @doc.pattern Handler, ActiveJ HTTP Handler
 */
public class HealthHandler {

    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);

    private final TraceStorage storage;
    private final ObjectMapper objectMapper;
    private final Instant startTime;

    /**
     * Constructs a HealthHandler.
     *
     * @param storage       TraceStorage backend (not null)
     * @param objectMapper  Jackson ObjectMapper for JSON (not null)
     * @throws IllegalArgumentException if any parameter is null
     */
    public HealthHandler(TraceStorage storage, ObjectMapper objectMapper) {
        this.storage = Objects.requireNonNull(storage, "TraceStorage cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.startTime = Instant.now();
    }

    /**
     * Handles GET /health - liveness check.
     * <p>
     * Always returns 200 OK if the service is running.
     * This is a simple check to determine if the process is alive.
     * </p>
     * <p>
     * Response:
     * <pre>
     * {
     *   "status": "UP",
     *   "timestamp": "2025-10-23T10:00:00Z",
     *   "uptime": 3600000
     * }
     * </pre>
     * </p>
     *
     * @param request  HTTP request
     * @return Promise of HTTP response with 200 OK
     */
    public Promise<HttpResponse> handleLiveness(HttpRequest request) {
        logger.debug("Liveness check");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("uptime", System.currentTimeMillis() - startTime.toEpochMilli());

        return Promise.of(createJsonResponse(200, response));
    }

    /**
     * Handles GET /health/ready - readiness check.
     * <p>
     * Checks if the service is ready to accept traffic by:
     * <ul>
     *   <li>Verifying TraceStorage is healthy</li>
     *   <li>Checking connectivity to backend storage</li>
     * </ul>
     * </p>
     * <p>
     * Returns:
     * <ul>
     *   <li>200 OK if ready (storage is healthy)</li>
     *   <li>503 Service Unavailable if not ready (storage is unhealthy)</li>
     * </ul>
     * </p>
     * <p>
     * Response (ready):
     * <pre>
     * {
     *   "status": "UP",
     *   "timestamp": "2025-10-23T10:00:00Z",
     *   "checks": {
     *     "storage": "UP"
     *   }
     * }
     * </pre>
     * </p>
     * <p>
     * Response (not ready):
     * <pre>
     * {
     *   "status": "DOWN",
     *   "timestamp": "2025-10-23T10:00:00Z",
     *   "checks": {
     *     "storage": "DOWN"
     *   }
     * }
     * </pre>
     * </p>
     *
     * @param request  HTTP request
     * @return Promise of HTTP response (200 or 503)
     */
    public Promise<HttpResponse> handleReadiness(HttpRequest request) {
        logger.debug("Readiness check");

        return storage.isHealthy()
                .then(isHealthy -> {
                    String status = isHealthy ? "UP" : "DOWN";
                    int statusCode = isHealthy ? 200 : 503;

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", status);
                    response.put("timestamp", Instant.now().toString());

                    Map<String, String> checks = new HashMap<>();
                    checks.put("storage", status);
                    response.put("checks", checks);

                    if (isHealthy) {
                        logger.debug("Readiness check: READY");
                    } else {
                        logger.warn("Readiness check: NOT READY - Storage is unhealthy");
                    }

                    return Promise.of(createJsonResponse(statusCode, response));
                }, ex -> {
                    logger.error("Readiness check failed", ex);

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "DOWN");
                    response.put("timestamp", Instant.now().toString());

                    Map<String, String> checks = new HashMap<>();
                    checks.put("storage", "DOWN");
                    response.put("checks", checks);
                    response.put("error", ex.getMessage());

                    return Promise.of(createJsonResponse(503, response));
                });
    }

    /**
     * Creates a JSON HTTP response with the given status code and body.
     *
     * @param statusCode  HTTP status code
     * @param body        Response body object (will be serialized to JSON)
     * @return HTTP response
     */
    private HttpResponse createJsonResponse(int statusCode, Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            return HttpResponse.ofCode(statusCode)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build();
        } catch (Exception ex) {
            logger.error("Failed to serialize response to JSON", ex);
            return HttpResponse.ofCode(500)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody("{\"error\":\"Failed to serialize response\"}".getBytes())
                    .build();
        }
    }
}
