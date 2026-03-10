package com.ghatana.yappc.ai.requirements.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.yappc.ai.requirements.api.error.ErrorResponse;
import com.ghatana.yappc.ai.requirements.api.rest.dto.CreateRequirementRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.GenerateSuggestionsRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.RecordFeedbackRequest;
import com.ghatana.yappc.ai.requirements.api.rest.dto.RequirementResponse;
import com.ghatana.yappc.ai.requirements.api.rest.dto.SimilarRequirementResponse;
import com.ghatana.yappc.ai.requirements.api.rest.dto.SuggestionResponse;
import com.ghatana.platform.security.model.User;
import com.ghatana.yappc.ai.requirements.api.validation.Validation;
import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackType;
import com.ghatana.yappc.ai.requirements.ai.feedback.SuggestionFeedback;
import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.yappc.ai.requirements.domain.requirement.RequirementStatus;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for requirement operations with AI suggestions and search.
 *
 * <p><b>Purpose:</b> Provides HTTP API endpoints for:
 * - Creating requirements (with automatic embedding)
 * - Retrieving requirements with suggestions
 * - Finding similar requirements
 * - Managing suggestion feedback
 *
 * <p><b>Thread Safety:</b> Thread-safe. Stateless controller suitable for
 * concurrent HTTP request handling.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li><b>POST /api/projects/{projectId}/requirements:</b> Create requirement</li>
 *   <li><b>GET /api/requirements/{id}:</b> Get requirement with suggestions</li>
 *   <li><b>GET /api/requirements/{id}/similar:</b> Find similar requirements</li>
 *   <li><b>POST /api/requirements/{id}/suggestions:</b> Generate suggestions</li>
 *   <li><b>POST /api/suggestions/{id}/feedback:</b> Record feedback</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   // Create requirement
 *   POST /api/projects/proj-123/requirements
 *   { "text": "Add OAuth2 authentication" }
 *
 *   // Get with suggestions
 *   GET /api/requirements/req-123?includesSuggestions=true
 *
 *   // Find similar
 *   GET /api/requirements/req-123/similar?limit=5&minSimilarity=0.8
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose REST controller for requirement management
 * @doc.layer product
 * @doc.pattern REST Controller
 * @since 1.0.0
 */
public final class RequirementController {
  private static final Logger logger = LoggerFactory.getLogger(RequirementController.class);

  private static final int DEFAULT_SUGGESTION_LIMIT = 5;
  private static final int MAX_SUGGESTION_LIMIT = 25;
  private static final int MIN_SUGGESTION_LIMIT = 1;
  private static final int DEFAULT_SIMILAR_LIMIT = 5;
  private static final int MIN_SIMILAR_LIMIT = 1;
  private static final int MAX_SIMILAR_LIMIT = 50;
  private static final float DEFAULT_MIN_SIMILARITY = 0.75f;
  private static final int MAX_SUGGESTION_CACHE_SIZE = 4096;

  private final RequirementEmbeddingService embeddingService;
  private final ObjectMapper objectMapper;
  private final Executor blockingExecutor;
  private final ConcurrentMap<UUID, RequirementSnapshot> requirements = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AISuggestion> suggestionCache = new ConcurrentHashMap<>();

  /**
   * Create a requirement controller.
   *
   * @param embeddingService the orchestrator service (non-null)
   */
  public RequirementController(RequirementEmbeddingService embeddingService) {
    this(embeddingService, JsonUtils.getDefaultMapper(), ForkJoinPool.commonPool());
  }

  /**
   * Create a requirement controller with explicit dependencies.
   *
   * @param embeddingService embedding orchestrator
   * @param objectMapper JSON mapper for request bodies
   * @param blockingExecutor executor for blocking JSON parsing
   */
  public RequirementController(
      RequirementEmbeddingService embeddingService,
      ObjectMapper objectMapper,
      Executor blockingExecutor) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
  }

  /**
   * Create servlet with all requirement routes.
   * 
   * @return RoutingServlet with all requirement routes
   */
  public RoutingServlet createServlet() {
    RoutingServlet servlet = new RoutingServlet();
    servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/projects/:projectId/requirements", this::createRequirement);
    servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/requirements/:id", this::getRequirement);
    servlet.addAsyncRoute(HttpMethod.GET, "/api/v1/requirements/:id/similar", this::findSimilar);
    servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/requirements/:id/suggestions", this::generateSuggestions);
    servlet.addAsyncRoute(HttpMethod.POST, "/api/v1/suggestions/:id/feedback", this::recordFeedback);
    return servlet;
  }

  /**
   * POST /api/v1/projects/{projectId}/requirements
   *
   * <p>Create a new requirement in a project.
   *
   * @param request HTTP request
   * @return Promise resolving to HTTP response
   */
  public Promise<HttpResponse> createRequirement(HttpRequest request) {
    final UUID projectId;
    try {
      String rawProjectId = Validation.requirePathParameter(request, "projectId");
      projectId = Validation.requireValidUuid(rawProjectId, "projectId");
    } catch (ErrorResponse.ValidationException ex) {
      return Promise.of(
          ErrorResponse.badRequest(ex.getMessage(), Map.of("field", "projectId", "error", "INVALID")));
    }

    logger.info("Creating requirement in project: {}", projectId);

    Promise<HttpResponse> pipeline =
        parseRequestBody(request, CreateRequirementRequest.class, null)
            .then(req -> {
              UUID requirementId = UUID.randomUUID();
              Instant now = Instant.now();
              RequirementSnapshot snapshot =
                  new RequirementSnapshot(
                      requirementId,
                      projectId,
                      req.text().trim(),
                      req.priority(),
                      RequirementStatus.DRAFT.name(),
                      now,
                      now);

              return embeddingService
                  .embedAndStore(
                      requirementId.toString(),
                      snapshot.text(),
                      projectId.toString())
                  .map(ignored -> {
                    requirements.put(requirementId, snapshot);
                    return ResponseBuilder.created()
                        .json(toRequirementResponse(snapshot, List.of(), List.of()))
                        .build();
                  });
            });

    return withErrorHandling(pipeline, "createRequirement");
  }

  /**
   * GET /api/v1/requirements/{id}
   *
   * <p>Get a requirement by ID.
   *
   * @param request HTTP request
   * @return Promise resolving to HTTP response
   */
  public Promise<HttpResponse> getRequirement(HttpRequest request) {
    final UUID requirementId;
    try {
      String rawRequirementId = Validation.requirePathParameter(request, "id");
      requirementId = Validation.requireValidUuid(rawRequirementId, "requirementId");
    } catch (ErrorResponse.ValidationException ex) {
      return Promise.of(
          ErrorResponse.badRequest(ex.getMessage(), Map.of("field", "id", "error", "INVALID")));
    }

    RequirementSnapshot snapshot = requirements.get(requirementId);
    if (snapshot == null) {
      logger.warn("Requirement not found: {}", requirementId);
      return Promise.of(
          ErrorResponse.notFound(
              "Requirement not found",
              "REQUIREMENT_NOT_FOUND",
              Map.of("requirementId", requirementId.toString())));
    }

    boolean includeSuggestions =
        isTruthy(request.getQueryParameter("includeSuggestions"))
            || isTruthy(request.getQueryParameter("includesSuggestions"));
    boolean includeSimilar =
        isTruthy(request.getQueryParameter("includeSimilar"))
            || isTruthy(request.getQueryParameter("includesSimilar"));

    Promise<List<SuggestionResponse>> suggestionsPromise =
        includeSuggestions
            ? generateSuggestionsInternal(
                snapshot.id(),
                snapshot.text(),
                resolveUserId(request),
                Collections.emptySet(),
                DEFAULT_SUGGESTION_LIMIT,
                null)
            : Promise.of(List.of());

    int similarLimit =
        includeSimilar
            ? parseLimit(
                request.getQueryParameter("limit"),
                DEFAULT_SIMILAR_LIMIT,
                MIN_SIMILAR_LIMIT,
                MAX_SIMILAR_LIMIT)
            : DEFAULT_SIMILAR_LIMIT;
    float minSimilarity =
        includeSimilar
            ? parseFloat(
                request.getQueryParameter("minSimilarity"),
                DEFAULT_MIN_SIMILARITY,
                -1f,
                1f)
            : DEFAULT_MIN_SIMILARITY;

    Promise<List<SimilarRequirementResponse>> similarPromise =
        includeSimilar
            ? findSimilarInternal(snapshot, similarLimit, minSimilarity)
            : Promise.of(List.of());

    Promise<HttpResponse> pipeline =
        suggestionsPromise.then(
            suggestions ->
                similarPromise.map(
                    similar ->
                        ResponseBuilder.ok()
                            .json(toRequirementResponse(snapshot, suggestions, similar))
                            .build()));

    return withErrorHandling(pipeline, "getRequirement");
  }

  /**
   * GET /api/v1/requirements/{id}/similar
   *
   * <p>Find semantically similar requirements.
   *
   * @param request HTTP request
   * @return Promise resolving to HTTP response
   */
  public Promise<HttpResponse> findSimilar(HttpRequest request) {
    final UUID requirementId;
    try {
      String rawRequirementId = Validation.requirePathParameter(request, "id");
      requirementId = Validation.requireValidUuid(rawRequirementId, "requirementId");
    } catch (ErrorResponse.ValidationException ex) {
      return Promise.of(
          ErrorResponse.badRequest(ex.getMessage(), Map.of("field", "id", "error", "INVALID")));
    }

    RequirementSnapshot snapshot = requirements.get(requirementId);
    if (snapshot == null) {
      logger.warn("Requirement not found for similarity search: {}", requirementId);
      return Promise.of(
          ErrorResponse.notFound(
              "Requirement not found",
              "REQUIREMENT_NOT_FOUND",
              Map.of("requirementId", requirementId.toString())));
    }

    int limit =
        parseLimit(
            request.getQueryParameter("limit"),
            DEFAULT_SIMILAR_LIMIT,
            MIN_SIMILAR_LIMIT,
            MAX_SIMILAR_LIMIT);
    float minSimilarity =
        parseFloat(
            request.getQueryParameter("minSimilarity"),
            DEFAULT_MIN_SIMILARITY,
            -1f,
            1f);

    logger.debug(
        "Finding similar requirements for {} with limit {} and minSimilarity {}",
        requirementId,
        limit,
        minSimilarity);

    Promise<HttpResponse> pipeline =
        findSimilarInternal(snapshot, limit, minSimilarity)
            .map(similar -> ResponseBuilder.ok().json(similar).build());

    return withErrorHandling(pipeline, "findSimilarRequirements");
  }

  /**
   * POST /api/v1/requirements/{id}/suggestions
   *
   * <p>Generate AI suggestions for a requirement.
   *
   * @param request HTTP request
   * @return Promise resolving to HTTP response
   */
  public Promise<HttpResponse> generateSuggestions(HttpRequest request) {
    final UUID requirementId;
    try {
      String rawRequirementId = Validation.requirePathParameter(request, "id");
      requirementId = Validation.requireValidUuid(rawRequirementId, "requirementId");
    } catch (ErrorResponse.ValidationException ex) {
      return Promise.of(
          ErrorResponse.badRequest(ex.getMessage(), Map.of("field", "id", "error", "INVALID")));
    }

    RequirementSnapshot snapshot = requirements.get(requirementId);

    Promise<HttpResponse> pipeline =
        parseRequestBody(
                request,
                GenerateSuggestionsRequest.class,
                () -> GenerateSuggestionsRequest.empty())
            .then(dto -> {
              String featureDescription =
                  dto.featureDescription() != null
                      ? dto.featureDescription().trim()
                      : snapshot != null
                          ? snapshot.text()
                          : null;
              if (featureDescription == null || featureDescription.isEmpty()) {
                return Promise.ofException(
                    new ErrorResponse.ValidationException(
                        "featureDescription is required when requirement text is unavailable",
                        Map.of("field", "featureDescription", "error", "REQUIRED")));
              }

              Set<Persona> personaFilter = parsePersonas(dto.personas());
              Float minRelevance =
                  dto.minRelevance() != null
                      ? clampFloat(dto.minRelevance(), 0f, 1f)
                      : null;
              int limit =
                  dto.limit() != null
                      ? parseLimit(
                          dto.limit().toString(),
                          DEFAULT_SUGGESTION_LIMIT,
                          MIN_SUGGESTION_LIMIT,
                          MAX_SUGGESTION_LIMIT)
                      : DEFAULT_SUGGESTION_LIMIT;

              logger.info(
                  "Generating suggestions for requirement {} feature='{}' personas={} limit={}",
                  requirementId,
                  featureDescription,
                  personaFilter,
                  limit);

              return generateSuggestionsInternal(
                      requirementId,
                      featureDescription,
                      resolveUserId(request),
                      personaFilter,
                      limit,
                      minRelevance)
                  .map(suggestions -> ResponseBuilder.ok().json(suggestions).build());
            });

    return withErrorHandling(pipeline, "generateSuggestions");
  }

  /**
   * POST /api/v1/suggestions/{id}/feedback
   *
   * <p>Record user feedback on a suggestion.
   *
   * @param request HTTP request
   * @return Promise resolving to HTTP response
   */
  public Promise<HttpResponse> recordFeedback(HttpRequest request) {
    final String suggestionId;
    try {
      suggestionId = Validation.requirePathParameter(request, "id");
    } catch (ErrorResponse.ValidationException ex) {
      return Promise.of(
          ErrorResponse.badRequest(ex.getMessage(), Map.of("field", "id", "error", "INVALID")));
    }

    AISuggestion suggestion = suggestionCache.get(suggestionId);
    if (suggestion == null) {
      logger.warn("Suggestion not found for feedback: {}", suggestionId);
      return Promise.of(
          ErrorResponse.notFound(
              "Suggestion not found",
              "SUGGESTION_NOT_FOUND",
              Map.of("suggestionId", suggestionId)));
    }

    Promise<HttpResponse> pipeline =
        parseRequestBody(request, RecordFeedbackRequest.class, null)
            .then(body -> {
              FeedbackType feedbackType = parseFeedbackType(body.getType());
              SuggestionFeedback feedback =
                  new SuggestionFeedback(
                      suggestionId,
                      feedbackType,
                      body.getFeedbackText(),
                      body.getRating(),
                      resolveUserId(request));

              logger.info(
                  "Recording feedback {} for suggestion {} by {}",
                  feedback.type(),
                  suggestionId,
                  feedback.userId());

              return embeddingService
                  .recordFeedback(suggestion, feedback)
                  .map(updated -> {
                    suggestionCache.put(suggestionId, updated);
                    return ResponseBuilder.noContent().build();
                  });
            });

    return withErrorHandling(pipeline, "recordFeedback");
  }

  private Promise<HttpResponse> withErrorHandling(
      Promise<HttpResponse> pipeline, String operation) {
    return pipeline.then(
        (response, exception) -> {
          if (exception == null) {
            return Promise.of(response);
          }
          logger.error("RequirementController {} failed", operation, exception);
          return Promise.of(handleException(exception, operation));
        });
  }

  private HttpResponse handleException(Throwable error, String operation) {
    if (error instanceof ErrorResponse.ValidationException validationException) {
      return ErrorResponse.badRequest(
          validationException.getMessage(), "VALIDATION_ERROR", validationException.getDetails());
    }

    if (error instanceof IllegalArgumentException illegalArgumentException) {
      return ErrorResponse.badRequest(
          illegalArgumentException.getMessage(),
          "INVALID_ARGUMENT",
          Map.of("operation", operation));
    }

    return ErrorResponse.internalServerError(
        "Unexpected error while processing request",
        "INTERNAL_ERROR",
        Map.of("operation", operation));
  }

  private <T> Promise<T> parseRequestBody(
      HttpRequest request, Class<T> type, Supplier<T> emptySupplier) {
    return request
        .loadBody()
        .then(
            byteBuf -> {
              byte[] body = byteBuf.asArray();
              if ((body == null || body.length == 0)) {
                if (emptySupplier != null) {
                  return Promise.of(emptySupplier.get());
                }
                return Promise.ofException(
                    new ErrorResponse.ValidationException("Request body is required"));
              }
              return Promise.ofBlocking(
                  blockingExecutor,
                  () -> {
                    try {
                      return objectMapper.readValue(body, type);
                    } catch (IOException ex) {
                      throw new ErrorResponse.ValidationException(
                          "Invalid JSON body",
                          Map.of("error", ex.getMessage()));
                    }
                  });
            });
  }

  private Promise<List<SuggestionResponse>> generateSuggestionsInternal(
      UUID requirementId,
      String featureDescription,
      String userId,
      Set<Persona> personaFilter,
      int limit,
      Float minRelevance) {
    return embeddingService
        .generateSuggestions(requirementId.toString(), featureDescription, userId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(
                        suggestion ->
                            personaFilter.isEmpty()
                                || personaFilter.contains(suggestion.persona()))
                    .filter(
                        suggestion ->
                            minRelevance == null
                                || suggestion.relevanceScore() >= minRelevance)
                    .limit(limit)
                    .map(this::toSuggestionResponse)
                    .collect(Collectors.toList()));
  }

  private Promise<List<SimilarRequirementResponse>> findSimilarInternal(
      RequirementSnapshot snapshot, int limit, float minSimilarity) {
    return embeddingService
        .findSimilarRequirements(snapshot.text(), snapshot.projectId().toString(), limit, minSimilarity)
        .map(this::toSimilarResponses);
  }

  private RequirementResponse toRequirementResponse(
      RequirementSnapshot snapshot,
      List<SuggestionResponse> suggestions,
      List<SimilarRequirementResponse> similarRequirements) {
    return new RequirementResponse(
        snapshot.id(),
        snapshot.projectId(),
        snapshot.text(),
        snapshot.status(),
        suggestions,
        similarRequirements,
        snapshot.createdAt(),
        snapshot.updatedAt());
  }

  private SuggestionResponse toSuggestionResponse(AISuggestion suggestion) {
    String suggestionId = encodeSuggestionId(suggestion);
    cacheSuggestion(suggestionId, suggestion);
    return new SuggestionResponse(
        suggestionId,
        suggestion.suggestionText(),
        suggestion.persona().name(),
        suggestion.relevanceScore(),
        suggestion.priorityScore(),
        suggestion.status().name(),
        suggestion.createdAt());
  }

  private List<SimilarRequirementResponse> toSimilarResponses(List<VectorSearchResult> results) {
    return results.stream()
        .map(
            result ->
                new SimilarRequirementResponse(
                    safeUuid(result.getId()),
                    result.getContent(),
                    (float) result.getSimilarity()))
        .collect(Collectors.toList());
  }

  private void cacheSuggestion(String id, AISuggestion suggestion) {
    suggestionCache.put(id, suggestion);
    if (suggestionCache.size() > MAX_SUGGESTION_CACHE_SIZE) {
      suggestionCache.keySet().stream().findFirst().ifPresent(suggestionCache::remove);
    }
  }

  private String encodeSuggestionId(AISuggestion suggestion) {
    String payload =
        suggestion.requirementId()
            + "|"
            + suggestion.persona().name()
            + "|"
            + suggestion.createdAt().toEpochMilli()
            + "|"
            + suggestion.suggestionText();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private String resolveUserId(HttpRequest request) {
    User principal = extractUser(request);
    if (principal != null && principal.getUserId() != null) {
      return principal.getUserId();
    }

    String header = request.getHeader(HttpHeaders.of("X-User-Id"));
    if (header != null && !header.isBlank()) {
      return header;
    }

    return "system";
  }

  private User extractUser(HttpRequest request) {
    Object attachment = request.getAttachment("userPrincipal");
    if (attachment instanceof User user) {
      return user;
    }
    return null;
  }

  private boolean isTruthy(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("1")
        || normalized.equals("true")
        || normalized.equals("yes")
        || normalized.equals("on");
  }

  private int parseLimit(String raw, int defaultValue, int min, int max) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(raw);
      if (parsed < min || parsed > max) {
        throw new IllegalArgumentException(
            String.format("limit must be between %d and %d", min, max));
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("limit must be a number");
    }
  }

  private float parseFloat(String raw, float defaultValue, float min, float max) {
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      float parsed = Float.parseFloat(raw);
      if (parsed < min || parsed > max) {
        throw new IllegalArgumentException(
            String.format("Value must be between %.2f and %.2f", min, max));
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Value must be a decimal");
    }
  }

  private Float clampFloat(float value, float min, float max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(
          String.format("Value must be between %.2f and %.2f", min, max));
    }
    return value;
  }

  private FeedbackType parseFeedbackType(String rawType) throws ErrorResponse.ValidationException {
    if (rawType == null || rawType.isBlank()) {
      throw new ErrorResponse.ValidationException(
          "Feedback type is required", Map.of("field", "type", "error", "REQUIRED"));
    }
    try {
      return FeedbackType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ErrorResponse.ValidationException(
          "Unsupported feedback type",
          Map.of("field", "type", "value", rawType, "error", "INVALID"));
    }
  }

  private Set<Persona> parsePersonas(List<String> personas) {
    if (personas == null || personas.isEmpty()) {
      return Collections.emptySet();
    }

    return personas.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(value -> {
          try {
            return Persona.valueOf(value.toUpperCase(Locale.ROOT));
          } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported persona: " + value);
          }
        })
        .collect(Collectors.toSet());
  }

  private UUID safeUuid(String candidate) {
    try {
      return UUID.fromString(candidate);
    } catch (IllegalArgumentException ex) {
      return UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8));
    }
  }

  private record RequirementSnapshot(
      UUID id,
      UUID projectId,
      String text,
      String priority,
      String status,
      Instant createdAt,
      Instant updatedAt) {}
}
