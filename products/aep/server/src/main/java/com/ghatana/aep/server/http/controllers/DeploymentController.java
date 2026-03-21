/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.orchestrator.deployment.contract.DeploymentRequest;
import com.ghatana.orchestrator.deployment.contract.DeploymentResponse;
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

    public DeploymentController(DeploymentHttpAdapter deploymentAdapter) {
        this.deploymentAdapter = deploymentAdapter;
    }

    public Promise<HttpResponse> handleCreateDeployment(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest =
                    HttpHelper.mapper().readValue(body, DeploymentRequest.class);
                DeploymentResponse response =
                    deploymentAdapter.handleDeploymentRequest(deploymentRequest);
                return Promise.of(HttpHelper.jsonResponse(toMap(response)));
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
                String body = buf.getString(StandardCharsets.UTF_8);
                DeploymentRequest deploymentRequest =
                    HttpHelper.mapper().readValue(body, DeploymentRequest.class);
                DeploymentResponse response =
                    deploymentAdapter.handleUpdateRequest(deploymentId, deploymentRequest);
                return Promise.of(HttpHelper.jsonResponse(toMap(response)));
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
            String deploymentId = request.getPathParameter("deploymentId");
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null || tenantId.isBlank()) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "tenantId query parameter is required"));
            }
            DeploymentResponse response =
                deploymentAdapter.handleUndeployRequest(deploymentId, tenantId);
            return Promise.of(HttpHelper.jsonResponse(toMap(response)));
        } catch (Exception e) {
            log.error("Error deleting deployment", e);
            return Promise.of(HttpHelper.errorResponse(400,
                "Invalid delete deployment request: " + e.getMessage()));
        }
    }

    private Map<String, Object> toMap(Object value) {
        return HttpHelper.mapper().convertValue(value, new TypeReference<>() {});
    }
}
