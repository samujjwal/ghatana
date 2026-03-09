/*
 * @doc.purpose HTTP endpoints for Left Rail
 * @doc.layer platform
 * @doc.pattern Controller
 */
package com.ghatana.yappc.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.api.service.RailService;
import io.activej.inject.annotation.Inject;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for Unified Left Rail.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for Left Rail
 * @doc.layer platform
 * @doc.pattern Controller
 */
public class RailController {
    private static final Logger logger = LoggerFactory.getLogger(RailController.class);
    private final RailService railService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    @Inject
    public RailController(RailService railService) {
        this.railService = railService;
    }

    public Promise<HttpResponse> getComponents(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String category = request.getQueryParameter("category");
        String query = request.getQueryParameter("query");
        String mode = request.getQueryParameter("mode");
        return railService.getComponents(tenantId, category, query, mode)
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getInfrastructure(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String mode = request.getQueryParameter("mode");
        return railService.getInfrastructure(tenantId, mode)
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getFiles(HttpRequest request) {
        String path = request.getQueryParameter("path");
        return railService.getFiles(path != null ? path : "/")
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getDataSources(HttpRequest request) {
        return railService.getDataSources()
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getHistory(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return railService.getHistory(tenantId)
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getFavorites(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return railService.getFavorites(tenantId)
                .map(ApiResponse::ok);
    }

    public Promise<HttpResponse> getSuggestions(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                Map<String, Object> requestBody = Map.of();
                try {
                    String jsonBody = request.getBody().asString(StandardCharsets.UTF_8);
                    if (jsonBody != null && !jsonBody.isBlank()) {
                        requestBody = objectMapper.readValue(jsonBody, new TypeReference<>() {});
                    }
                } catch (Exception e) {
                    logger.warn("Invalid rail suggestion request body, using empty context: {}", e.getMessage());
                }

                TenantContextExtractor.RequestContext context = TenantContextExtractor.extract(request);
                Map<String, Object> suggestionContext = new java.util.HashMap<>(requestBody);
                suggestionContext.putIfAbsent("tenantId", context.tenantId() != null ? context.tenantId() : "default");
                suggestionContext.putIfAbsent("userId", context.userId() != null ? context.userId() : "anonymous");
                suggestionContext.putIfAbsent("mode", request.getQueryParameter("mode"));

                return railService.getSuggestions(suggestionContext).map(ApiResponse::ok);
            });
    }

    private String resolveTenantId(HttpRequest request) {
        String queryTenant = request.getQueryParameter("tenantId");
        if (queryTenant != null && !queryTenant.isBlank()) {
            return queryTenant;
        }
        TenantContextExtractor.RequestContext context = TenantContextExtractor.extract(request);
        if (context.tenantId() != null && !context.tenantId().isBlank()) {
            return context.tenantId();
        }
        return "default";
    }
}
