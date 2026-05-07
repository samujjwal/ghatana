/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.http.DeploymentHttpAdapter;
import com.fasterxml.jackson.core.type.TypeReference;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller for deployment orchestration endpoints.
 *
 * @doc.type class
 * @doc.purpose Deployment CRUD operations
 * @doc.layer product
 * @doc.pattern Service
 */
public class DeploymentController {

    private static final Logger log = LoggerFactory.getLogger(DeploymentController.class);

    private final DeploymentHttpAdapter deploymentAdapter;
    private final boolean authDisabled;

    public DeploymentController(DeploymentHttpAdapter deploymentAdapter) {
        this(deploymentAdapter, "true".equalsIgnoreCase(System.getenv("AEP_AUTH_DISABLED")));
    }

    DeploymentController(DeploymentHttpAdapter deploymentAdapter, boolean authDisabled) {
        this.deploymentAdapter = deploymentAdapter;
        this.authDisabled = authDisabled;
    }

    public Promise<HttpResponse> handleCreateDeployment(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                HttpResponse authorizationFailure = authorizeDeploymentRequest(request);
                if (authorizationFailure != null) {
                    return Promise.of(authorizationFailure);
                }

                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest = parseDeploymentRequest(body);
                validateDeploymentRequest(deploymentRequest);
                return deploymentAdapter.handleDeploymentRequest(deploymentRequest)
                    .map(response -> HttpHelper.jsonResponse(toMap(response)));
            } catch (Exception e) {
                log.error("Error creating deployment", e);
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid deployment request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read deployment body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    public Promise<HttpResponse> handleUpdateDeployment(HttpRequest request) {
        String deploymentId = request.getPathParameter("deploymentId");
        return request.loadBody().then(buf -> {
            try {
                HttpResponse authorizationFailure = authorizeDeploymentRequest(request);
                if (authorizationFailure != null) {
                    return Promise.of(authorizationFailure);
                }

                AepInputValidator.validateResourceId(deploymentId, "deploymentId");
                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest = parseDeploymentRequest(body);
                validateDeploymentRequest(deploymentRequest);
                return deploymentAdapter.handleUpdateRequest(deploymentId, deploymentRequest)
                    .map(response -> HttpHelper.jsonResponse(toMap(response)));
            } catch (Exception e) {
                log.error("Error updating deployment", e);
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid update deployment request: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read deployment update body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    public Promise<HttpResponse> handleDeleteDeployment(HttpRequest request) {
        try {
            HttpResponse authorizationFailure = authorizeDeploymentRequest(request);
            if (authorizationFailure != null) {
                return Promise.of(authorizationFailure);
            }

            String deploymentId = request.getPathParameter("deploymentId");
            String tenantId = request.getQueryParameter("tenantId");
            AepInputValidator.validateResourceId(deploymentId, "deploymentId");
            if (tenantId == null || tenantId.isBlank()) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "tenantId query parameter is required"));
            }
            AepInputValidator.validateTenantId(tenantId);
            return deploymentAdapter.handleUndeployRequest(deploymentId, tenantId)
                .map(response -> HttpHelper.jsonResponse(toMap(response)));
        } catch (Exception e) {
            log.error("Error deleting deployment", e);
            return Promise.of(HttpHelper.errorResponse(400,
                "Invalid delete deployment request: " + e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Object value) {
        return HttpHelper.mapper().convertValue(value, new TypeReference<>() {});
    }

    private HttpResponse authorizeDeploymentRequest(HttpRequest request) {
        if (authDisabled) {
            return null;
        }
        AepAuthFilter.JwtPayload jwtPayload = request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT);
        if (jwtPayload == null) {
            return HttpHelper.errorResponse(403, "Deployment operations require an authenticated principal");
        }
        if (!jwtPayload.canManageDeployments()) {
            return HttpHelper.errorResponse(403, "Deployment operations require deployment management permission");
        }
        return null;
    }

    private void validateDeploymentRequest(DeploymentRequest deploymentRequest) {
        if (deploymentRequest == null || !deploymentRequest.isValid()) {
            throw new IllegalArgumentException("pipelineId, tenantId, and environment are required");
        }
        AepInputValidator.validateResourceId(deploymentRequest.getPipelineId(), "pipelineId");
        AepInputValidator.validateTenantId(deploymentRequest.getTenantId());
        AepInputValidator.requireNonBlank(deploymentRequest.getEnvironment(), "environment");
    }

    private DeploymentRequest parseDeploymentRequest(String body) throws Exception {
        Map<String, Object> payload = HttpHelper.mapper().readValue(body, new TypeReference<>() {});
        Map<String, String> deploymentOptions = null;
        Object optionsValue = payload.get("deploymentOptions");
        if (optionsValue instanceof Map<?, ?> optionsMap) {
            deploymentOptions = optionsMap.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()),
                entry -> String.valueOf(entry.getValue())
            ));
        }

        return DeploymentRequest.builder()
            .pipelineId(asString(payload.get("pipelineId")))
            .tenantId(asString(payload.get("tenantId")))
            .environment(asString(payload.get("environment")))
            .deploymentOptions(deploymentOptions)
            .build();
    }

    private String asString(Object value) {
        return value instanceof String stringValue ? stringValue : null;
    }
}
