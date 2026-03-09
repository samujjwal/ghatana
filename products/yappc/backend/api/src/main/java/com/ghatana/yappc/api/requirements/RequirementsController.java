/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.requirements;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.requirements.dto.*;
import com.ghatana.yappc.api.service.ConfigService;
import com.ghatana.yappc.api.service.RequirementService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified REST API Controller for Requirements operations.
 *
 * @doc.type class
 * @doc.purpose Requirements REST API (simplified)
 * @doc.layer api
 * @doc.pattern Controller
 */
public class RequirementsController {

  private static final Logger logger = LoggerFactory.getLogger(RequirementsController.class);

  private final RequirementService requirementService;
  private final ConfigService configService;

  public RequirementsController(
      RequirementService requirementService, ConfigService configService) {
    this.requirementService = requirementService;
    this.configService = configService;
  }

  /** Create a new requirement. POST /api/requirements */
  public Promise<HttpResponse> createRequirement(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Creating requirement for tenant: {}", ctx.tenantId());
              
              return request.loadBody().then(body -> {
                  CreateRequirementRequest req = JsonUtils.fromJson(body.getString(StandardCharsets.UTF_8), CreateRequirementRequest.class);

                  // Use config-driven data for requirement creation
                  return configService
                      .getDomains()
                      .then(
                          domains -> {
                            // Use first domain as default for demo, or parse from request
                            var defaultDomain = domains.isEmpty() ? "general" : domains.get(0).id();

                            Requirement.RequirementType domainType = req.type() != null ? 
                                Requirement.RequirementType.valueOf(req.type().name()) : null;
                                
                            Requirement.Priority domainPriority = req.priority() != null ? 
                                Requirement.Priority.valueOf(req.priority().name()) : null;

                            return requirementService.createRequirement(
                                ctx.tenantId(),
                                req.title() != null ? req.title() : "New Requirement", 
                                req.description() != null ? req.description() : ("Auto-generated requirement using domain: " + defaultDomain),
                                domainType,
                                domainPriority,
                                ctx.userId());
                          })
                      .<HttpResponse>map(
                          requirement -> {
                            logger.info(
                                "Successfully created requirement {} for tenant {}",
                                requirement.getId(),
                                ctx.tenantId());
                            return ApiResponse.created(requirement);
                          });
              });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Query requirements. GET /api/requirements */
  public Promise<HttpResponse> queryRequirements(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Querying requirements for tenant: {}", ctx.tenantId());

              // Get all requirements for the tenant
              return requirementService
                  .getAllRequirements(ctx.tenantId())
                  .map(
                      requirements -> {
                        logger.info(
                            "Found {} requirements for tenant {}",
                            requirements.size(),
                            ctx.tenantId());
                        return ApiResponse.ok(requirements);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get requirement by ID. GET /api/requirements/:id */
  public Promise<HttpResponse> getRequirement(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting requirement {} for tenant: {}", id, ctx.tenantId());

              return requirementService
                  .getRequirement(ctx.tenantId(), java.util.UUID.fromString(id))
                  .map(
                      optionalRequirement -> {
                        if (optionalRequirement.isPresent()) {
                          logger.info("Found requirement {} for tenant {}", id, ctx.tenantId());
                          return ApiResponse.ok(optionalRequirement.get());
                        } else {
                          logger.warn("Requirement {} not found for tenant {}", id, ctx.tenantId());
                          return ApiResponse.notFound("Requirement not found: " + id);
                        }
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Update requirement. PUT /api/requirements/:id */
  public Promise<HttpResponse> updateRequirement(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Updating requirement {} for tenant: {}", id, ctx.tenantId());

              return request.loadBody().then(body -> {
                  UpdateRequirementRequest req = JsonUtils.fromJson(body.getString(StandardCharsets.UTF_8), UpdateRequirementRequest.class);

                  Requirement.Priority domainPriority = req.priority() != null ? 
                        Requirement.Priority.valueOf(req.priority().name()) : null;

                  return requirementService
                      .updateRequirement(
                          ctx.tenantId(),
                          java.util.UUID.fromString(id),
                          req.title(),
                          req.description(),
                          domainPriority,
                          ctx.userId())
                      .map(
                          optionalRequirement -> {
                            if (optionalRequirement.isPresent()) {
                              logger.info(
                                  "Successfully updated requirement {} for tenant {}",
                                  id,
                                  ctx.tenantId());
                              return ApiResponse.ok(optionalRequirement.get());
                            } else {
                              logger.warn(
                                  "Requirement {} not found for update in tenant {}",
                                  id,
                                  ctx.tenantId());
                              return ApiResponse.notFound("Requirement not found: " + id);
                            }
                          });
              });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Delete requirement. DELETE /api/requirements/:id */
  public Promise<HttpResponse> deleteRequirement(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Deleting requirement {} for tenant: {}", id, ctx.tenantId());

              return requirementService
                  .deleteRequirement(ctx.tenantId(), java.util.UUID.fromString(id), ctx.userId())
                  .then(
                      voidResult -> {
                        logger.info(
                            "Successfully deleted requirement {} for tenant {}",
                            id,
                            ctx.tenantId());
                        return Promise.of(ApiResponse.noContent());
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Approve requirement. POST /api/requirements/:id/approve */
  public Promise<HttpResponse> approveRequirement(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Approving requirement {} for tenant: {}", id, ctx.tenantId());

              return requirementService
                  .approveRequirement(ctx.tenantId(), java.util.UUID.fromString(id), ctx.userId())
                  .then(
                      optionalRequirement -> {
                        if (optionalRequirement.isPresent()) {
                          logger.info(
                              "Successfully approved requirement {} for tenant {}",
                              id,
                              ctx.tenantId());
                          return Promise.of(ApiResponse.ok(optionalRequirement.get()));
                        } else {
                          logger.warn(
                              "Requirement {} not found for approval in tenant {}",
                              id,
                              ctx.tenantId());
                          return Promise.of(ApiResponse.notFound("Requirement not found: " + id));
                        }
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get funnel analytics. GET /api/requirements/funnel */
  public Promise<HttpResponse> getFunnelAnalytics(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting funnel analytics for tenant: {}", ctx.tenantId());

              return requirementService
                  .getFunnelMetrics(ctx.tenantId())
                  .then(
                      funnelMetrics -> {
                        logger.info("Retrieved funnel analytics for tenant {}", ctx.tenantId());
                        return Promise.of(ApiResponse.ok(funnelMetrics));
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get available requirement domains from config. GET /api/requirements/domains */
  public Promise<HttpResponse> getAvailableDomains(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting available requirement domains for tenant: {}", ctx.tenantId());

              return configService
                  .getDomains()
                  .then(
                      domains -> {
                        var domainInfo =
                            domains.stream()
                                .map(
                                    domain ->
                                        java.util.Map.of(
                                            "id", domain.id(),
                                            "name", domain.name(),
                                            "description", domain.description(),
                                            "taskCount", domain.taskCount(),
                                            "icon", domain.icon(),
                                            "color", domain.color()))
                                .toList();

                        var result =
                            java.util.Map.of(
                                "domains",
                                domainInfo,
                                "total",
                                domains.size(),
                                "source",
                                "config-driven");

                        logger.info(
                            "Retrieved {} domains from config for tenant {}",
                            domains.size(),
                            ctx.tenantId());
                        return Promise.of(ApiResponse.ok(result));
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Calculate quality score. POST /api/requirements/:id/quality */
  public Promise<HttpResponse> calculateQualityScore(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info(
                  "Calculating quality score for requirement {} in tenant: {}", id, ctx.tenantId());

              return requirementService
                  .getRequirement(ctx.tenantId(), java.util.UUID.fromString(id))
                  .then(
                      optionalRequirement -> {
                        if (optionalRequirement.isPresent()) {
                          // Simple quality score calculation based on basic completeness
                          var requirement = optionalRequirement.get();
                          double score = 50.0; // Base score

                          if (requirement.getTitle() != null
                              && requirement.getTitle().length() > 10) score += 15;
                          if (requirement.getDescription() != null
                              && requirement.getDescription().length() > 20) score += 20;
                          if (requirement.getType() != null) score += 10;
                          if (requirement.getPriority() != null) score += 5;

                          var result =
                              java.util.Map.of(
                                  "requirementId",
                                  requirement.getId(),
                                  "score",
                                  score,
                                  "feedback",
                                  score >= 75 ? "Good quality requirement" : "Needs improvement");

                          logger.info(
                              "Calculated quality score {} for requirement {} in tenant {}",
                              score,
                              id,
                              ctx.tenantId());
                          return Promise.of(ApiResponse.ok(result));
                        } else {
                          logger.warn(
                              "Requirement {} not found for quality calculation in tenant {}",
                              id,
                              ctx.tenantId());
                          return Promise.of(ApiResponse.notFound("Requirement not found: " + id));
                        }
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }
}
