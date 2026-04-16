package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.server.marketplace.AgentMarketplaceService;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose HTTP endpoints for marketplace discovery, publishing, and reviews
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class AgentMarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(AgentMarketplaceController.class);

    private final AgentMarketplaceService marketplaceService;

    public AgentMarketplaceController(@Nullable DataCloudClient dataCloudClient) {
        this.marketplaceService = new AgentMarketplaceService(dataCloudClient);
    }

    AgentMarketplaceController(AgentMarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    public Promise<HttpResponse> handleListAgents(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        String search = request.getQueryParameter("search");
        String capability = request.getQueryParameter("capability");
        int limit = parseInt(request.getQueryParameter("limit"), 100);
        return marketplaceService.listAgents(tenantId, search, capability, limit)
                .map(agents -> HttpHelper.jsonResponse(Map.of(
                        "agents", agents,
                        "count", agents.size(),
                        "timestamp", Instant.now().toString())));
    }

    public Promise<HttpResponse> handleGetAgent(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        return marketplaceService.getAgent(tenantId, agentId)
                .map(detail -> detail
                        .<HttpResponse>map(value -> HttpHelper.jsonResponse(Map.of(
                                "listing", value.listing(),
                                "reviews", value.reviews(),
                                "timestamp", Instant.now().toString())))
                        .orElseGet(() -> HttpHelper.errorResponse(404, "Marketplace agent not found: " + agentId)));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handlePublishAgent(HttpRequest request) {
        return request.loadBody().then(buffer -> {
            try {
                Map<String, Object> payload = HttpHelper.mapper().readValue(buffer.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, payload);
                AgentMarketplaceService.PublishAgentRequest publishRequest = new AgentMarketplaceService.PublishAgentRequest(
                        asString(payload.get("id")),
                        asString(payload.get("name")),
                        asNullableString(payload.get("description")),
                        asNullableString(payload.get("version")),
                        asNullableString(payload.get("domain")),
                        asNullableString(payload.get("level")),
                        asStringList(payload.get("capabilities")),
                        asStringList(payload.get("tags")),
                        asNullableString(payload.get("owner")));
                if (publishRequest.name() == null || publishRequest.name().isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "name is required"));
                }
                return marketplaceService.publishAgent(tenantId, publishRequest)
                        .map(agent -> HttpHelper.jsonResponse(Map.of(
                                "agent", agent,
                                "timestamp", Instant.now().toString())));
            } catch (Exception error) {
                log.error("[marketplace] failed to parse publish request", error);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid publish request: " + error.getMessage()));
            }
        }, error -> {
            log.error("[marketplace] failed to read publish request body", error);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    public Promise<HttpResponse> handleListReviews(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        return marketplaceService.listReviews(tenantId, agentId)
                .map(reviews -> HttpHelper.jsonResponse(Map.of(
                        "reviews", reviews,
                        "count", reviews.size(),
                        "timestamp", Instant.now().toString())));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateReview(HttpRequest request) {
        return request.loadBody().then(buffer -> {
            try {
                Map<String, Object> payload = HttpHelper.mapper().readValue(buffer.getString(StandardCharsets.UTF_8), Map.class);
                String tenantId = HttpHelper.resolveTenantId(request, payload);
                String agentId = request.getPathParameter("agentId");
                AgentMarketplaceService.CreateReviewRequest reviewRequest = new AgentMarketplaceService.CreateReviewRequest(
                        asNullableString(payload.get("reviewer")),
                        parseInt(String.valueOf(payload.getOrDefault("rating", 0)), 0),
                        asNullableString(payload.get("title")),
                        asNullableString(payload.get("comment")));
                if (reviewRequest.rating() < 1 || reviewRequest.rating() > 5) {
                    return Promise.of(HttpHelper.errorResponse(400, "rating must be between 1 and 5"));
                }
                return marketplaceService.addReview(tenantId, agentId, reviewRequest)
                        .map(review -> HttpHelper.jsonResponse(Map.of(
                                "review", review,
                                "timestamp", Instant.now().toString())));
            } catch (Exception error) {
                log.error("[marketplace] failed to parse review request", error);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid review request: " + error.getMessage()));
            }
        }, error -> {
            log.error("[marketplace] failed to read review request body", error);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    private static int parseInt(@Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Nullable
    private static String asNullableString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String rendered = value.toString();
        return rendered.isBlank() ? null : rendered;
    }

    private static String asString(@Nullable Object value) {
        return value != null ? value.toString() : "";
    }

    private static java.util.List<String> asStringList(@Nullable Object value) {
        if (value instanceof java.util.List<?> values) {
            return values.stream().map(Object::toString).toList();
        }
        return java.util.List.of();
    }
}