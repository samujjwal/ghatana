/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.controller;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.service.ConfigService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose HTTP endpoints for configuration data
 * @doc.layer platform
 * @doc.pattern Controller
 */
public class ConfigController {
  private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);
  private final ConfigService configService;

  public ConfigController(ConfigService configService) {
    this.configService = configService;
  }

  // GET /api/config/domains
  public Promise<HttpResponse> getDomains(HttpRequest request) {
    logger.debug("GET /api/config/domains");

    return configService
        .getDomains()
        .then(domains -> Promise.of(ApiResponse.ok(domains)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/domains/:id
  public Promise<HttpResponse> getDomainById(HttpRequest request, String domainId) {
    logger.debug("GET /api/config/domains/{}", domainId);

    return configService
        .getDomainById(domainId)
        .then(domain -> Promise.of(ApiResponse.ok(domain)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/workflows
  public Promise<HttpResponse> getWorkflows(HttpRequest request) {
    logger.debug("GET /api/config/workflows");

    return configService
        .getWorkflows()
        .then(workflows -> Promise.of(ApiResponse.ok(workflows)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/workflows/:id
  public Promise<HttpResponse> getWorkflowById(HttpRequest request, String workflowId) {
    logger.debug("GET /api/config/workflows/{}", workflowId);

    return configService
        .getWorkflowById(workflowId)
        .then(workflow -> Promise.of(ApiResponse.ok(workflow)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/lifecycle
  public Promise<HttpResponse> getLifecycleConfig(HttpRequest request) {
    logger.debug("GET /api/config/lifecycle");

    return configService
        .getLifecycleConfig()
        .then(config -> Promise.of(ApiResponse.ok(config)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/agents
  public Promise<HttpResponse> getAgentCapabilities(HttpRequest request) {
    logger.debug("GET /api/config/agents");

    return configService
        .getAgentCapabilities()
        .then(caps -> Promise.of(ApiResponse.ok(caps)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/tasks
  public Promise<HttpResponse> getAllTasks(HttpRequest request) {
    logger.debug("GET /api/config/tasks");

    return configService
        .getAllTasks()
        .then(tasks -> Promise.of(ApiResponse.ok(tasks)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/personas
  public Promise<HttpResponse> getPersonas(HttpRequest request) {
    logger.debug("GET /api/config/personas");

    return configService
        .getPersonas()
        .then(personas -> Promise.of(ApiResponse.ok(personas)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // GET /api/config/personas/:id
  public Promise<HttpResponse> getPersonaById(HttpRequest request, String personaId) {
    logger.debug("GET /api/config/personas/{}", personaId);

    return configService
        .getPersonaById(personaId)
        .then(persona -> Promise.of(ApiResponse.ok(persona)))
        .then(success -> Promise.of(success), error -> Promise.of(toErrorResponse(error)));
  }

  // Helper Method
  private HttpResponse toErrorResponse(Exception error) {
    logger.error("Request failed", error);
    return ApiResponse.error(500, "CONFIG_ERROR", error.getMessage(), "/api/config");
  }
}
