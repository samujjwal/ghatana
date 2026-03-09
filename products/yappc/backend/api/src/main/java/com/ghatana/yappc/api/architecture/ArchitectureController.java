/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.architecture;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.ArchitectureAnalysisService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified REST API Controller for Architecture Analysis operations.
 *
 * @doc.type class
 * @doc.purpose Architecture analysis REST API (simplified)
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ArchitectureController {

  private static final Logger logger = LoggerFactory.getLogger(ArchitectureController.class);

  private final ArchitectureAnalysisService architectureAnalysisService;

  public ArchitectureController(ArchitectureAnalysisService architectureAnalysisService) {
    this.architectureAnalysisService = architectureAnalysisService;
  }

  /** Analyze architecture impact. POST /api/architecture/impact */
  public Promise<HttpResponse> analyzeImpact(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Analyzing architecture impact for tenant: {}", ctx.tenantId());
              return architectureAnalysisService.analyzeImpact(ctx.tenantId())
                  .map(response -> ApiResponse.ok(response));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get dependency graph. GET /api/architecture/dependencies */
  public Promise<HttpResponse> getDependencies(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting dependency graph for tenant: {}", ctx.tenantId());
              return architectureAnalysisService.getDependencies(ctx.tenantId())
                  .map(response -> ApiResponse.ok(response));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get tech debt analysis. GET /api/architecture/tech-debt */
  public Promise<HttpResponse> getTechDebt(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting tech debt analysis for tenant: {}", ctx.tenantId());
              return architectureAnalysisService.getTechDebt(ctx.tenantId())
                  .map(response -> ApiResponse.ok(response));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get pattern warnings. GET /api/architecture/patterns */
  public Promise<HttpResponse> getPatternWarnings(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting pattern warnings for tenant: {}", ctx.tenantId());
              return architectureAnalysisService.getPatternWarnings(ctx.tenantId())
                  .map(response -> ApiResponse.ok(response));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Simulate change impact. POST /api/architecture/simulate */
  public Promise<HttpResponse> simulateChange(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Simulating change impact for tenant: {}", ctx.tenantId());
              return architectureAnalysisService.simulateChange(ctx.tenantId())
                  .map(ApiResponse::ok);
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }}
