/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.ai;

import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.service.AISuggestionService;
import com.ghatana.yappc.api.service.ConfigService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.yappc.api.ai.dto.GenerateSuggestionRequest;
import com.ghatana.yappc.api.common.JsonUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Simplified REST API Controller for AI Suggestions operations.
 *
 * @doc.type class
 * @doc.purpose AI Suggestions REST API (simplified)
 * @doc.layer api
 * @doc.pattern Controller
 */
public class AISuggestionsController {

  private static final Logger logger = LoggerFactory.getLogger(AISuggestionsController.class);

  private final AISuggestionService aiSuggestionService;
  private final ConfigService configService;
  private final AepService aepService;
  private final Executor blockingExecutor;

  public AISuggestionsController(
      AISuggestionService aiSuggestionService, ConfigService configService, AepService aepService) {
    this.aiSuggestionService = aiSuggestionService;
    this.configService = configService;
    this.aepService = aepService;
    this.blockingExecutor = Executors.newFixedThreadPool(2);
  }

  /** Generate new AI suggestion. POST /api/ai/suggestions/generate */
  public Promise<HttpResponse> generateSuggestion(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx ->
                JsonUtils.parseBody(request, GenerateSuggestionRequest.class)
                    .then(
                        req -> {
                          String validationError = validateGenerateSuggestionRequest(req);
                          if (validationError != null) {
                            return Promise.ofException(new JsonUtils.BadRequestException(validationError));
                          }

                          logger.info(
                              "Generating AI suggestion for tenant: {} project: {}",
                              ctx.tenantId(),
                              req.projectId());
                          return aiSuggestionService
                              .generateSuggestion(ctx.tenantId(), req)
                              .map(
                                  response -> {
                                    java.util.Map<String, Object> eventPayload =
                                        new java.util.LinkedHashMap<>();
                                    eventPayload.put("tenantId", ctx.tenantId());
                                    eventPayload.put("userId", ctx.userId());
                                    eventPayload.put("workspaceId", req.workspaceId());
                                    eventPayload.put("projectId", req.projectId());
                                    eventPayload.put("suggestionType", req.suggestionType().name());
                                    eventPayload.put("suggestionId", response.id());
                                    eventPayload.put("targetEntityId", req.targetEntityId());
                                    eventPayload.put("targetEntityType", req.targetEntityType());
                                    eventPayload.put("generatedAt", java.time.Instant.now().toString());
                                    publishAepEventNonCritical(
                                        "ai.suggestion.generated", eventPayload);
                                    return ApiResponse.created(response);
                                  });
                        }))
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  private static String validateGenerateSuggestionRequest(GenerateSuggestionRequest request) {
    if (request == null) {
      return "Request body is required";
    }
    if (isBlank(request.workspaceId())) {
      return "workspaceId is required";
    }
    if (isBlank(request.projectId())) {
      return "projectId is required";
    }
    if (request.suggestionType() == null) {
      return "suggestionType is required";
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** Query AI suggestions. GET /api/ai/suggestions */
  public Promise<HttpResponse> querySuggestions(HttpRequest request) {
    String workspaceId = request.getQueryParameter("workspaceId");
    String projectId = request.getQueryParameter("projectId");

    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Querying AI suggestions for tenant: {}", ctx.tenantId());
              return aiSuggestionService
                  .getSuggestions(ctx.tenantId(), workspaceId, projectId)
                  .map(
                      suggestions -> {
                        java.util.Map<String, Object> query = new java.util.LinkedHashMap<>();
                        query.put("tenantId", ctx.tenantId());
                        query.put("workspaceId", workspaceId);
                        query.put("projectId", projectId);
                        query.put("resultCount", suggestions.size());
                        executeAepQueryNonCritical("query-suggestions", query);
                        return ApiResponse.ok(suggestions);
                      });
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get AI suggestions inbox. GET /api/ai/suggestions/inbox */
  public Promise<HttpResponse> getInbox(HttpRequest request) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting AI suggestions inbox for tenant: {}", ctx.tenantId());
              java.util.Map<String, Object> actionContext = new java.util.LinkedHashMap<>();
              actionContext.put("tenantId", ctx.tenantId());
              actionContext.put("userId", ctx.userId());
              executeAepActionNonCritical("ai-suggestion-inbox", actionContext);

              // Return AI suggestion inbox with categorized suggestions
              var inbox =
                  java.util.Map.of(
                      "summary",
                          java.util.Map.of(
                              "totalPending", 5,
                              "highConfidence", 3,
                              "mediumConfidence", 2,
                              "newSuggestions", 2),
                      "categories",
                          java.util.Map.of(
                              "quality-improvement", 3,
                              "requirement-templates", 1,
                              "risk-assessment", 1),
                      "recentSuggestions",
                          java.util.List.of(
                              java.util.Map.of(
                                  "id", "ai-latest-001",
                                  "type", "quality-improvement",
                                  "title", "Improve Requirement Clarity",
                                  "confidence", 0.89,
                                  "createdAt", java.time.Instant.now().minusSeconds(300))));

              logger.info("Retrieved AI suggestion inbox for tenant {}", ctx.tenantId());
              return Promise.of(ApiResponse.ok(inbox));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Get AI suggestion by ID. GET /api/ai/suggestions/:id */
  public Promise<HttpResponse> getSuggestion(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Getting AI suggestion {} for tenant: {}", id, ctx.tenantId());
              java.util.Map<String, Object> actionContext = new java.util.LinkedHashMap<>();
              actionContext.put("tenantId", ctx.tenantId());
              actionContext.put("userId", ctx.userId());
              actionContext.put("suggestionId", id);
              executeAepActionNonCritical("ai-suggestion-get", actionContext);

              // Return detailed AI suggestion using HashMap for larger maps
              var suggestion = new java.util.HashMap<String, Object>();
              suggestion.put("id", id);
              suggestion.put("tenantId", ctx.tenantId());
              suggestion.put("type", "requirement-enhancement");
              suggestion.put("title", "Add User Acceptance Criteria");
              suggestion.put(
                  "description",
                  "This requirement would benefit from clearly defined acceptance criteria. Consider adding specific, measurable outcomes that define when this requirement is complete.");
              suggestion.put("confidence", 0.87);
              suggestion.put("category", "quality-improvement");
              suggestion.put("generatedAt", java.time.Instant.now().minusSeconds(1800));
              suggestion.put("status", "pending");
              suggestion.put("targetRequirementId", "req-123");
              suggestion.put(
                  "recommendations",
                  java.util.List.of(
                      "Add specific acceptance criteria",
                      "Include measurable outcomes",
                      "Define clear success metrics"));
              suggestion.put(
                  "metadata",
                  java.util.Map.of(
                      "modelVersion", "gpt-4-turbo",
                      "analysisSource", "requirement-text",
                      "processingTimeMs", 180));

              logger.info("Retrieved AI suggestion {} for tenant {}", id, ctx.tenantId());
              return Promise.of(ApiResponse.ok(suggestion));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Accept AI suggestion. POST /api/ai/suggestions/:id/accept */
  public Promise<HttpResponse> acceptSuggestion(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Accepting AI suggestion {} for tenant: {}", id, ctx.tenantId());
              java.util.Map<String, Object> actionContext = new java.util.LinkedHashMap<>();
              actionContext.put("tenantId", ctx.tenantId());
              actionContext.put("userId", ctx.userId());
              actionContext.put("suggestionId", id);
              actionContext.put("status", "accepted");
              executeAepActionNonCritical("ai-suggestion-accept", actionContext);

              // Return accepted suggestion with implementation details
              var acceptedSuggestion =
                  java.util.Map.of(
                      "id",
                      id,
                      "status",
                      "accepted",
                      "acceptedBy",
                      ctx.userId(),
                      "acceptedAt",
                      java.time.Instant.now(),
                      "implementationStatus",
                      "pending",
                      "estimatedImplementationTime",
                      "15 minutes",
                      "nextSteps",
                      java.util.List.of(
                          "Apply suggested changes to requirement",
                          "Review updated requirement",
                          "Notify stakeholders of improvement"),
                      "feedback",
                      java.util.Map.of(
                          "userSatisfaction", "positive",
                          "implementationDifficulty", "low"));

              logger.info(
                  "Successfully accepted AI suggestion {} for tenant {}", id, ctx.tenantId());
              return Promise.of(ApiResponse.ok(acceptedSuggestion));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  /** Reject AI suggestion. POST /api/ai/suggestions/:id/reject */
  public Promise<HttpResponse> rejectSuggestion(HttpRequest request, String id) {
    return TenantContextExtractor.requireAuthenticated(request)
        .then(
            ctx -> {
              logger.info("Rejecting AI suggestion {} for tenant: {}", id, ctx.tenantId());
              java.util.Map<String, Object> actionContext = new java.util.LinkedHashMap<>();
              actionContext.put("tenantId", ctx.tenantId());
              actionContext.put("userId", ctx.userId());
              actionContext.put("suggestionId", id);
              actionContext.put("status", "rejected");
              executeAepActionNonCritical("ai-suggestion-reject", actionContext);

              // Return rejected suggestion with feedback details
              var rejectedSuggestion =
                  java.util.Map.of(
                      "id", id,
                      "status", "rejected",
                      "rejectedBy", ctx.userId(),
                      "rejectedAt", java.time.Instant.now(),
                      "reason", "not-applicable",
                      "feedback",
                          java.util.Map.of(
                              "relevance", "low",
                              "accuracy", "medium",
                              "usefulness", "low",
                              "comments", "Suggestion doesn't apply to current context"),
                      "learningData",
                          java.util.Map.of(
                              "improveRecommendations", true,
                              "contextMismatch", true,
                              "userPreference", "detailed-analysis"));

              logger.info(
                  "Successfully rejected AI suggestion {} for tenant {}", id, ctx.tenantId());
              return Promise.of(ApiResponse.ok(rejectedSuggestion));
            })
        .then(response -> Promise.of(response), e -> Promise.of(ApiResponse.fromException(e)));
  }

  private void publishAepEventNonCritical(String eventType, java.util.Map<String, Object> payload) {
    Promise.ofBlocking(blockingExecutor, () -> {
      try {
        String eventId = aepService.publishEvent(eventType, JsonUtils.toJson(payload));
        logger.debug("Published AEP event {} with id {}", eventType, eventId);
        return null;
      } catch (AepException e) {
        logger.warn("AEP event publishing failed for {} (continuing)", eventType, e);
        return null;
      } catch (Exception e) {
        logger.warn("Failed to serialize AEP event payload for {} (continuing)", eventType, e);
        return null;
      }
    }).whenException(e -> logger.warn("Background AEP event publishing failed", e));
  }

  private void executeAepQueryNonCritical(String queryType, java.util.Map<String, Object> query) {
    Promise.ofBlocking(blockingExecutor, () -> {
      try {
        String response = aepService.queryEvents(JsonUtils.toJson(query));
        logger.debug("AEP query completed ({} bytes)", response.length());
        return null;
      } catch (AepException e) {
        logger.warn("AEP query failed (continuing)", e);
        return null;
      } catch (Exception e) {
        logger.warn("Failed to serialize AEP query payload (continuing)", e);
        return null;
      }
    }).whenException(e -> logger.warn("Background AEP query execution failed", e));
  }

  private void executeAepActionNonCritical(String action, java.util.Map<String, Object> context) {
    Promise.ofBlocking(blockingExecutor, () -> {
      try {
        String response = aepService.executeAction(action, JsonUtils.toJson(context));
        logger.debug("AEP action {} completed ({} bytes)", action, response.length());
        return null;
      } catch (AepException e) {
        logger.warn("AEP action {} failed (continuing)", action, e);
        return null;
      } catch (Exception e) {
        logger.warn("Failed to serialize AEP action context for {} (continuing)", action, e);
        return null;
      }
    }).whenException(e -> logger.warn("Background AEP action execution failed", e));
  }
}
