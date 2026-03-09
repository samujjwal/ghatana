package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.security.TenantExtractor;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.application.webhook.WebhookService;
import com.ghatana.datacloud.entity.webhook.Webhook;
import com.ghatana.datacloud.entity.webhook.WebhookEventType;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST controller for webhook subscription management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides HTTP API endpoints for webhook CRUD operations. Supports creating,
 * listing, updating, and deleting webhook subscriptions with full multi-tenant
 * isolation and event type filtering.
 *
 * <p>
 * <b>Endpoints</b><br>
 * <ul>
 * <li><b>POST /api/webhooks:</b> Register webhook subscription
 * <li><b>GET /api/webhooks:</b> List webhooks for tenant
 * <li><b>GET /api/webhooks/{id}:</b> Get webhook by ID
 * <li><b>PUT /api/webhooks/{id}:</b> Update webhook (enable/disable or URL)
 * <li><b>DELETE /api/webhooks/{id}:</b> Delete webhook subscription
 * </ul>
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations extract tenantId from X-Tenant-Id header and enforce tenant
 * isolation.
 *
 * <p>
 * <b>Error Handling</b><br>
 * - 400: Invalid request or validation error
 * - 401: Missing tenant context
 * - 404: Webhook not found
 * - 500: Internal server error
 *
 * <p>
 * <b>Performance</b><br>
 * All operations async (Promise-based) for non-blocking execution.
 *
 * @see WebhookService
 * @see Webhook
 * @doc.type class
 * @doc.purpose REST API controller for webhook management
 * @doc.layer product
 * @doc.pattern Controller (API Layer)
 */
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-Id");

    private final WebhookService webhookService;
    private final MetricsCollector metrics;
    private final ObjectMapper mapper;

    /**
     * Create webhook controller.
     *
     * @param webhookService service for webhook operations
     * @param metrics        metrics collector for observability
     * @param mapper         JSON object mapper
     */
    public WebhookController(
            WebhookService webhookService,
            MetricsCollector metrics,
            ObjectMapper mapper) {
        this.webhookService = Objects.requireNonNull(webhookService, "WebhookService cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper cannot be null");
        logger.info("WebhookController initialized");
    }

    /**
     * Handle incoming HTTP requests for webhook operations.
     *
     * @param request HTTP request
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> handle(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            metrics.incrementCounter("controller.webhook.error",
                    "error_type", "MISSING_TENANT_ID");
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "X-Tenant-Id header is required"))
                    .build());
        }

        String path = request.getPath();
        HttpMethod method = request.getMethod();

        try {
            if (method == HttpMethod.POST && path.equals("/api/webhooks")) {
                return createWebhook(request, tenantId);
            } else if (method == HttpMethod.GET && path.equals("/api/webhooks")) {
                return listWebhooks(tenantId);
            } else if (method == HttpMethod.GET && path.startsWith("/api/webhooks/")) {
                String id = extractIdFromPath(path, "/api/webhooks/");
                return getWebhook(id, tenantId);
            } else if (method == HttpMethod.PUT && path.startsWith("/api/webhooks/")) {
                String id = extractIdFromPath(path, "/api/webhooks/");
                return updateWebhook(request, id, tenantId);
            } else if (method == HttpMethod.DELETE && path.startsWith("/api/webhooks/")) {
                String id = extractIdFromPath(path, "/api/webhooks/");
                return deleteWebhook(id, tenantId);
            } else {
                return Promise.of(ResponseBuilder.notFound()
                        .json(Collections.singletonMap("error", "Endpoint not found"))
                        .build());
            }
        } catch (Exception e) {
            logger.error("Error handling webhook request", e);
            metrics.incrementCounter("controller.webhook.error",
                    "error_type", "INTERNAL");
            return Promise.of(ResponseBuilder.internalServerError()
                    .json(Collections.singletonMap("error", "Internal server error"))
                    .build());
        }
    }

    /**
     * Create webhook subscription.
     *
     * @param request  HTTP request with webhook config
     * @param tenantId tenant identifier
     * @return Promise of HTTP response
     */
    private Promise<HttpResponse> createWebhook(HttpRequest request, String tenantId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(body, Map.class);

            String eventTypeStr = (String) payload.get("eventType");
            String url = (String) payload.get("url");
            String environment = (String) payload.get("environment");
            Object maxRetriesObj = payload.get("maxRetries");
            Object retryDelayObj = payload.get("retryDelayMs");
            Object timeoutObj = payload.get("deliveryTimeoutSeconds");

            if (eventTypeStr == null || url == null || environment == null) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Collections.singletonMap("error",
                                "Missing required fields: eventType, url, environment"))
                        .build());
            }

            WebhookEventType eventType;
            try {
                // Prefer event-name mapping (e.g., "collection.created")
                eventType = WebhookEventType.fromEventName(eventTypeStr);
            } catch (IllegalArgumentException ex) {
                // Fallback: support enum constant name (e.g., "COLLECTION_CREATED")
                eventType = WebhookEventType.valueOf(eventTypeStr);
            }
            int maxRetries = maxRetriesObj != null ? ((Number) maxRetriesObj).intValue() : 3;
            int retryDelayMs = retryDelayObj != null ? ((Number) retryDelayObj).intValue() : 5000;
            int deliveryTimeoutSeconds = timeoutObj != null ? ((Number) timeoutObj).intValue() : 60;

            return webhookService.registerWebhook(
                    tenantId,
                    eventType,
                    url,
                    environment,
                    maxRetries,
                    retryDelayMs,
                    deliveryTimeoutSeconds)
                    .then(webhook -> {
                        try {
                            HttpResponse response = ResponseBuilder.created()
                                    .json(mapper.writeValueAsString(webhook))
                                    .build();
                            return Promise.of(response);
                        } catch (Exception e) {
                            logger.error("Error serializing created webhook", e);
                            return Promise.of(ResponseBuilder.internalServerError()
                                    .json(Collections.singletonMap("error", "Failed to serialize response"))
                                    .build());
                        }
                    }, error -> {
                        metrics.incrementCounter("controller.webhook.create.error",
                                "error_type", error.getClass().getSimpleName());
                        if (error instanceof IllegalArgumentException) {
                            return Promise.of(ResponseBuilder.badRequest()
                                    .json(Collections.singletonMap("error", error.getMessage()))
                                    .build());
                        }
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(Collections.singletonMap("error", "Failed to create webhook"))
                                .build());
                    });
        } catch (Exception e) {
            logger.error("Error parsing webhook request", e);
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "Invalid request format"))
                    .build());
        }
    }

    /**
     * List webhooks for tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise of HTTP response
     */
    private Promise<HttpResponse> listWebhooks(String tenantId) {
        return webhookService.listWebhooks(tenantId)
                .then(webhooks -> {
                    try {
                        Map<String, Object> response = new HashMap<>();
                        response.put("webhooks", webhooks);
                        response.put("count", webhooks.size());
                        HttpResponse httpResponse = ResponseBuilder.ok()
                                .json(mapper.writeValueAsString(response))
                                .build();
                        return Promise.of(httpResponse);
                    } catch (Exception e) {
                        logger.error("Error serializing webhook list", e);
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(Collections.singletonMap("error", "Failed to serialize response"))
                                .build());
                    }
                }, error -> {
                    logger.error("Error listing webhooks", error);
                    metrics.incrementCounter("controller.webhook.list.error");
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(Collections.singletonMap("error", "Failed to list webhooks"))
                            .build());
                });
    }

    /**
     * Get webhook by ID.
     *
     * @param id       webhook ID
     * @param tenantId tenant identifier
     * @return Promise of HTTP response
     */
    private Promise<HttpResponse> getWebhook(String id, String tenantId) {
        try {
            UUID webhookId = UUID.fromString(id);
            return webhookService.getWebhook(webhookId, tenantId)
                    .then(optional -> {
                        if (optional.isEmpty()) {
                            HttpResponse notFound = ResponseBuilder.notFound()
                                    .json(Collections.singletonMap("error", "Webhook not found"))
                                    .build();
                            return Promise.of(notFound);
                        }
                        try {
                            HttpResponse ok = ResponseBuilder.ok()
                                    .json(mapper.writeValueAsString(optional.get()))
                                    .build();
                            return Promise.of(ok);
                        } catch (Exception e) {
                            HttpResponse error = ResponseBuilder.internalServerError()
                                    .json(Collections.singletonMap("error", "Serialization error"))
                                    .build();
                            return Promise.of(error);
                        }
                    });
        } catch (IllegalArgumentException e) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "Invalid webhook ID"))
                    .build());
        }
    }

    /**
     * Update webhook (enable/disable or URL).
     *
     * @param request  HTTP request with update payload
     * @param id       webhook ID
     * @param tenantId tenant identifier
     * @return Promise of HTTP response
     */
    private Promise<HttpResponse> updateWebhook(HttpRequest request, String id, String tenantId) {
        try {
            UUID webhookId = UUID.fromString(id);
            return Promise.of(request.getBody().asString(StandardCharsets.UTF_8))
                    .then(body -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> payload = mapper.readValue(body, Map.class);

                            Promise<Webhook> updatePromise;
                            if (payload.containsKey("enabled")) {
                                Boolean enabled = (Boolean) payload.get("enabled");
                                updatePromise = webhookService.updateEnabled(webhookId, tenantId, enabled);
                            } else if (payload.containsKey("url")) {
                                String newUrl = (String) payload.get("url");
                                updatePromise = webhookService.updateUrl(webhookId, tenantId, newUrl);
                            } else {
                                return Promise.of(ResponseBuilder.badRequest()
                                        .json(Collections.singletonMap("error", "No update fields provided"))
                                        .build());
                            }

                            return updatePromise
                                    .then(webhook -> {
                                        try {
                                            HttpResponse ok = ResponseBuilder.ok()
                                                    .json(mapper.writeValueAsString(webhook))
                                                    .build();
                                            return Promise.of(ok);
                                        } catch (Exception e) {
                                            HttpResponse error = ResponseBuilder.internalServerError()
                                                    .json(Collections.singletonMap("error", "Serialization error"))
                                                    .build();
                                            return Promise.of(error);
                                        }
                                    }, error -> {
                                        if (error instanceof IllegalArgumentException) {
                                            HttpResponse notFound = ResponseBuilder.notFound()
                                                    .json(Collections.singletonMap("error", "Webhook not found"))
                                                    .build();
                                            return Promise.of(notFound);
                                        }
                                        logger.error("Error updating webhook", error);
                                        metrics.incrementCounter("controller.webhook.update.error");
                                        return Promise.of(ResponseBuilder.internalServerError()
                                                .json(Collections.singletonMap("error", "Failed to update webhook"))
                                                .build());
                                    });
                        } catch (Exception e) {
                            logger.error("Error parsing update request", e);
                            return Promise.of(ResponseBuilder.badRequest()
                                    .json(Collections.singletonMap("error", "Invalid request format"))
                                    .build());
                        }
                    });
        } catch (IllegalArgumentException e) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "Invalid webhook ID"))
                    .build());
        }
    }

    /**
     * Delete webhook subscription.
     *
     * @param id       webhook ID
     * @param tenantId tenant identifier
     * @return Promise of HTTP response
     */
    private Promise<HttpResponse> deleteWebhook(String id, String tenantId) {
        try {
            UUID webhookId = UUID.fromString(id);
            return webhookService.deleteWebhook(webhookId, tenantId)
                    .then(ignored -> {
                        HttpResponse ok = ResponseBuilder.ok()
                                .json(Collections.singletonMap("message", "Webhook deleted"))
                                .build();
                        return Promise.of(ok);
                    }, error -> {
                        logger.error("Error deleting webhook", error);
                        metrics.incrementCounter("controller.webhook.delete.error");
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(Collections.singletonMap("error", "Failed to delete webhook"))
                                .build());
                    });
        } catch (IllegalArgumentException e) {
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Collections.singletonMap("error", "Invalid webhook ID"))
                    .build());
        }
    }

    /**
     * Extract tenant ID from X-Tenant-Id header.
     *
     * @param request HTTP request
     * @return tenant ID or null if missing
     */
    private String extractTenantId(HttpRequest request) {
        return TenantExtractor.fromHttp(request).orElse(null);
    }

    /**
     * Extract ID from URL path.
     *
     * @param path   URL path
     * @param prefix path prefix
     * @return extracted ID
     */
    private String extractIdFromPath(String path, String prefix) {
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length()).split("\\?")[0]; // Remove query params
        }
        return "";
    }
}
