/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.JsonUtils;
import com.ghatana.yappc.api.deployment.dto.DeploymentRequest;
import com.ghatana.yappc.api.deployment.dto.HelmDeploymentRequest;
import com.ghatana.yappc.api.deployment.dto.RollbackRequest;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deployment Controller - Kubernetes and cloud deployment management.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/deployment/deploy - Deploy application</li>
 *   <li>GET /api/deployment/status/{deploymentId} - Get deployment status</li>
 *   <li>POST /api/deployment/rollback - Rollback deployment</li>
 *   <li>GET /api/deployment/environments - List environments</li>
 *   <li>POST /api/deployment/helm - Deploy Helm chart</li>
 *   <li>GET /api/deployment/logs/{deploymentId} - Get deployment logs</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Deployment orchestration API
 * @doc.layer product
 * @doc.pattern Controller
 */
public class DeploymentController extends AbstractModule {

        private static final Logger log = LoggerFactory.getLogger(DeploymentController.class);

        private static final String BASE_PATH = "/api/deployment";
        private static final String BASE_PATH_V1 = "/api/v1/deployment";

        private final DeploymentService deploymentService;

        @Inject
        public DeploymentController(DeploymentService deploymentService) {
                this.deploymentService = deploymentService;
        }

        @Provides
        RoutingServlet deploymentServlet(Reactor reactor) {
                return RoutingServlet.builder(reactor)
                                .with(POST, BASE_PATH + "/deploy", this::deploy)
                                .with(
                                                GET,
                                                BASE_PATH + "/status/:deploymentId",
                                                request -> getStatus(request.getPathParameter("deploymentId")))
                                .with(POST, BASE_PATH + "/rollback", this::rollback)
                                .with(GET, BASE_PATH + "/environments", this::listEnvironments)
                                .with(POST, BASE_PATH + "/helm", this::deployHelm)
                                .with(
                                                GET,
                                                BASE_PATH + "/logs/:deploymentId",
                                                request -> getLogs(request.getPathParameter("deploymentId")))
                                // Back-compat / v1 alias
                                .with(POST, BASE_PATH_V1 + "/deploy", this::deploy)
                                .with(
                                                GET,
                                                BASE_PATH_V1 + "/status/:deploymentId",
                                                request -> getStatus(request.getPathParameter("deploymentId")))
                                .with(POST, BASE_PATH_V1 + "/rollback", this::rollback)
                                .with(GET, BASE_PATH_V1 + "/environments", this::listEnvironments)
                                .with(POST, BASE_PATH_V1 + "/helm", this::deployHelm)
                                .with(
                                                GET,
                                                BASE_PATH_V1 + "/logs/:deploymentId",
                                                request -> getLogs(request.getPathParameter("deploymentId")))
                                .build();
        }

        private Promise<HttpResponse> deploy(HttpRequest httpRequest) {
                return JsonUtils.parseBody(httpRequest, DeploymentRequest.class)
                                .then(
                                                request -> {
                                                        log.info(
                                                                        "Deploying: app={}, env={}, strategy={}",
                                                                        request.applicationName(),
                                                                        request.environment(),
                                                                        request.strategy());

                                                        return deploymentService.deploy(request).map(ApiResponse::ok);
                                                });
        }

        private Promise<HttpResponse> getStatus(String deploymentId) {
                log.info("Getting deployment status: {}", deploymentId);
                return deploymentService.getDeploymentStatus(deploymentId).map(ApiResponse::ok);
        }

        private Promise<HttpResponse> rollback(HttpRequest httpRequest) {
                return JsonUtils.parseBody(httpRequest, RollbackRequest.class)
                                .then(
                                                request -> {
                                                        log.info("Rolling back deployment: {}", request.deploymentId());
                                                        return deploymentService.rollback(request).map(ApiResponse::ok);
                                                });
        }

        private Promise<HttpResponse> listEnvironments(HttpRequest httpRequest) {
                log.info("Listing deployment environments");
                return deploymentService.listEnvironments().map(ApiResponse::ok);
        }

        private Promise<HttpResponse> deployHelm(HttpRequest httpRequest) {
                return JsonUtils.parseBody(httpRequest, HelmDeploymentRequest.class)
                                .then(
                                                request -> {
                                                        log.info(
                                                                        "Deploying Helm chart: {}, release={}",
                                                                        request.chartName(),
                                                                        request.releaseName());

                                                        return deploymentService.deployHelm(request).map(ApiResponse::ok);
                                                });
        }

        private Promise<HttpResponse> getLogs(String deploymentId) {
                log.info("Getting deployment logs: {}", deploymentId);
                return deploymentService.getDeploymentLogs(deploymentId).map(ApiResponse::ok);
        }
}
