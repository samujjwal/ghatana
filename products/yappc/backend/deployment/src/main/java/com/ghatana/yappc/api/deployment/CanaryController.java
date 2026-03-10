/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

import com.ghatana.yappc.api.deployment.dto.CanaryAbortRequest;
import com.ghatana.yappc.api.deployment.dto.CanaryDeploymentRequest;
import com.ghatana.yappc.api.deployment.dto.CanaryPromotionRequest;
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
 * Canary Deployment Controller - Progressive canary rollouts with automated analysis.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/deployment/canary/deploy - Start canary deployment</li>
 *   <li>POST /api/deployment/canary/promote - Promote canary to production</li>
 *   <li>POST /api/deployment/canary/abort - Abort and rollback canary</li>
 *   <li>GET /api/deployment/canary/metrics/{canaryId} - Get canary metrics</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Progressive canary deployment with metrics analysis
 * @doc.layer product
 * @doc.pattern Controller
 */
public class CanaryController extends AbstractModule {

  private static final Logger log = LoggerFactory.getLogger(CanaryController.class);

  private static final String BASE_PATH = "/api/deployment/canary";
  private static final String BASE_PATH_V1 = "/api/v1/deployment/canary";

  private final CanaryService canaryService;

  @Inject
  public CanaryController(CanaryService canaryService) {
    this.canaryService = canaryService;
  }

  @Provides
  RoutingServlet canaryServlet(Reactor reactor) {
    return RoutingServlet.builder(reactor)
        .with(POST, BASE_PATH + "/deploy", this::deployCanary)
        .with(POST, BASE_PATH + "/promote", this::promoteCanary)
        .with(POST, BASE_PATH + "/abort", this::abortCanary)
        .with(
            GET,
            BASE_PATH + "/metrics/:canaryId",
            request -> getCanaryMetrics(request.getPathParameter("canaryId")))
        // Back-compat / v1 alias
        .with(POST, BASE_PATH_V1 + "/deploy", this::deployCanary)
        .with(POST, BASE_PATH_V1 + "/promote", this::promoteCanary)
        .with(POST, BASE_PATH_V1 + "/abort", this::abortCanary)
        .with(
            GET,
            BASE_PATH_V1 + "/metrics/:canaryId",
          request -> getCanaryMetrics(request.getPathParameter("canaryId")))
        .build();
  }

  private Promise<HttpResponse> deployCanary(HttpRequest httpRequest) {
    return DeploymentHttpUtils.parseBody(httpRequest, CanaryDeploymentRequest.class)
        .then(
            request -> {
              int stageCount = request.stages() != null ? request.stages().size() : 0;
              log.info(
                  "Deploying canary: app={}, version={}, stages={}",
                  request.applicationName(),
                  request.version(),
                  stageCount);

              return canaryService.deployCanary(request).map(DeploymentHttpUtils::ok);
            });
  }

  private Promise<HttpResponse> promoteCanary(HttpRequest httpRequest) {
    return DeploymentHttpUtils.parseBody(httpRequest, CanaryPromotionRequest.class)
        .then(
            request -> {
              log.info("Promoting canary: {}", request.canaryId());
              return canaryService.promoteCanary(request).map(DeploymentHttpUtils::ok);
            });
  }

  private Promise<HttpResponse> abortCanary(HttpRequest httpRequest) {
    return DeploymentHttpUtils.parseBody(httpRequest, CanaryAbortRequest.class)
        .then(
            request -> {
              log.info("Aborting canary: {}, reason={}", request.canaryId(), request.reason());
              return canaryService.abortCanary(request).map(DeploymentHttpUtils::ok);
            });
  }

  private Promise<HttpResponse> getCanaryMetrics(String canaryId) {
    log.info("Getting canary metrics: {}", canaryId);
    return canaryService.getCanaryMetrics(canaryId).map(DeploymentHttpUtils::ok);
  }
}
