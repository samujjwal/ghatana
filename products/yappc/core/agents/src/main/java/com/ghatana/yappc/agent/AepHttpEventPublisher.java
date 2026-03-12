package com.ghatana.yappc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP-based {@link AepEventPublisher} that sends YAPPC workflow events to AEP's ingest
 * endpoint ({@code POST /api/v1/events}) over HTTP.
 *
 * <p>This implements the <em>connector pattern</em>: YAPPC does not interact with
 * {@code EventCloud} directly. Instead, it sends events to AEP via HTTP, and AEP
 * internally routes them through EventCloud and its operator pipelines.
 *
 * <p>Blocking HTTP calls are wrapped with {@link Promise#ofBlocking} so the ActiveJ
 * Eventloop is never blocked.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code AEP_BASE_URL} — AEP service base URL (default: {@code http://localhost:8081})</li>
 *   <li>{@code AEP_HTTP_TIMEOUT_MS} — per-request timeout in milliseconds (default: {@code 5000})</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP connector for publishing YAPPC workflow events to AEP via POST /api/v1/events
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle act
 */
public final class AepHttpEventPublisher implements AepEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(AepHttpEventPublisher.class);
    private static final String EVENTS_PATH = "/api/v1/events";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String eventsUrl;
    private final HttpClient httpClient;
    private final Executor executor;

    /**
     * Creates an HTTP event publisher targeting the given AEP base URL.
     *
     * @param aepBaseUrl base URL of the AEP service (e.g. {@code http://localhost:8081})
     * @param executor   executor for off-eventloop blocking HTTP calls
     */
    public AepHttpEventPublisher(String aepBaseUrl, Executor executor) {
        Objects.requireNonNull(aepBaseUrl, "aepBaseUrl");
        this.eventsUrl = aepBaseUrl.stripTrailing() + EVENTS_PATH;
        this.executor = Objects.requireNonNull(executor, "executor");
        long timeoutMs = Long.parseLong(
            System.getenv().getOrDefault("AEP_HTTP_TIMEOUT_MS", "5000"));
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
    }

    /**
     * Creates an {@link AepHttpEventPublisher} configured from environment variables.
     *
     * @return publisher targeting {@code AEP_BASE_URL} (default {@code http://localhost:8081})
     */
    public static AepHttpEventPublisher fromEnvironment() {
        String baseUrl = System.getenv().getOrDefault("AEP_BASE_URL", "http://localhost:8081");
        Executor executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "aep-http-publisher");
            t.setDaemon(true);
            return t;
        });
        return new AepHttpEventPublisher(baseUrl, executor);
    }

    /**
     * Publishes a workflow event to AEP by POSTing JSON to {@code /api/v1/events}.
     *
     * <p>The request body is: {@code {"tenantId": "...", "type": "...", "payload": {...}}}
     *
     * <p>HTTP failures are logged and silently absorbed — callers should use
     * {@link com.ghatana.yappc.services.lifecycle.AepEventBridge} for resilient
     * fire-and-forget semantics.
     *
     * @param eventType the event type (e.g. {@code lifecycle.phase.advanced})
     * @param tenantId  the tenant identifier
     * @param payload   the event payload map
     * @return a promise resolving to {@code null} (always completes, never fails)
     */
    @Override
    public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
        String safeTenantId = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        String safeType = (eventType == null || eventType.isBlank()) ? "unknown" : eventType;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", safeTenantId);
        body.put("type", safeType);
        body.put("payload", payload != null ? payload : Map.of());

        return Promise.ofBlocking(executor, () -> {
            try {
                String json = MAPPER.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(eventsUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOG.debug("AEP event published: type={} tenant={} status={}",
                        safeType, safeTenantId, response.statusCode());
                } else {
                    LOG.warn("AEP ingest returned non-2xx status={} for type={} tenant={}: {}",
                        response.statusCode(), safeType, safeTenantId, response.body());
                }
            } catch (Exception ex) {
                LOG.error("AEP HTTP publish failed for type={} tenant={}: {}",
                    safeType, safeTenantId, ex.getMessage(), ex);
            }
            return null;
        });
    }
}
