package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.GenerationReviewAction;
import com.ghatana.yappc.domain.generate.GenerationReviewRequest;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.ghatana.yappc.api.HttpResponses.*;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Generation phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class GenerationApiController {

    private static final Logger log = LoggerFactory.getLogger(GenerationApiController.class);
    private static final int GENERATION_RATE_LIMIT = 30;

    private final GenerationService generationService;
    private final YappcArtifactRepository artifactRepository;
    private final RateLimiter generationRateLimiter;

    public GenerationApiController(GenerationService generationService, YappcArtifactRepository artifactRepository) {
        this.generationService = generationService;
        this.artifactRepository = artifactRepository;
        this.generationRateLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(Integer.parseInt(System.getenv().getOrDefault("YAPPC_GENERATE_RATE_LIMIT_MAX", String.valueOf(GENERATION_RATE_LIMIT))))
                .burstSize(Integer.parseInt(System.getenv().getOrDefault("YAPPC_GENERATE_RATE_LIMIT_BURST", String.valueOf(GENERATION_RATE_LIMIT))))
                .windowDuration(Duration.ofMinutes(1))
                .build()
        );
    }

    /**
     * POST /api/v1/yappc/generate
     * Generates artifacts from validated specification.
     *
     * @param request HTTP request with ValidatedSpec JSON body
     * @return Promise of HTTP response with GeneratedArtifacts
     */
    public Promise<HttpResponse> generateArtifacts(HttpRequest request) {
        log.info("Generating artifacts from validated spec");

        HttpResponse throttled = rateLimitResponseIfNeeded(request, "generate");
        if (throttled != null) {
            return Promise.of(throttled);
        }

        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        ValidatedSpec spec = parseValidatedSpec(json);

                        return generationService.generate(spec)
                                .map(artifacts -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(artifacts));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException | IllegalArgumentException e) {
                        log.error("Error parsing request", e);
                        return Promise.of(badRequest400(e.getMessage() == null ? "Invalid generation request" : e.getMessage()));
                    }
                })
                .whenException(e -> log.error("Error generating artifacts", e));
    }

    /**
     * POST /api/v1/yappc/generate/diff
     * Regenerates artifacts and computes diff.
     *
     * @param request HTTP request with ValidatedSpec and existing artifacts
     * @return Promise of HTTP response with DiffResult
     */
    public Promise<HttpResponse> regenerateWithDiff(HttpRequest request) {
        HttpResponse throttled = rateLimitResponseIfNeeded(request, "generate-diff");
        if (throttled != null) {
            return Promise.of(throttled);
        }

        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    try {
                        RegenerateDiffRequest diffRequest = JsonMapper.fromJson(json, RegenerateDiffRequest.class);
                        if (diffRequest == null || diffRequest.validatedSpec() == null || diffRequest.existingArtifacts() == null) {
                            throw new IllegalArgumentException("validatedSpec and existingArtifacts are required");
                        }
                        ValidatedSpec spec = validateSpec(diffRequest.validatedSpec());
                        GeneratedArtifacts existing = validateExistingArtifacts(diffRequest.existingArtifacts());

                        return generationService.regenerateWithDiff(spec, existing)
                                .map(diff -> {
                                    try {
                                        return ok200Json(JsonMapper.toJson(diff));
                                    } catch (JsonProcessingException e) {
                                        log.error("Error serializing response", e);
                                        return error500("Internal server error");
                                    }
                                });
                    } catch (JsonProcessingException | IllegalArgumentException e) {
                        log.error("Error parsing request", e);
                        return Promise.of(badRequest400(e.getMessage() == null ? "Invalid diff generation request" : e.getMessage()));
                    }
                })
                .whenException(e -> log.error("Diff generation failed", e));
    }

    /**
     * GET /api/v1/yappc/generate/artifacts/{id}
     * Retrieves generated artifacts by ID.
     *
     * @param request HTTP request with artifacts ID in path
     * @return Promise of HTTP response with GeneratedArtifacts
     */
    public Promise<HttpResponse> getArtifacts(HttpRequest request) {
        String artifactsId = request.getPathParameter("id");

        log.info("Retrieving artifacts: {}", artifactsId);

        String[] parts = artifactsId.split(":");
        if (parts.length != 2) {
            return Promise.of(badRequest400("{\"error\": \"Invalid artifacts ID format. Expected: productId:version\"}"));
        }

        String productId = parts[0];
        String version = parts[1];

        return artifactRepository.getArtifact(productId, PhaseType.GENERATE, version)
                .map(content -> {
                    try {
                        GeneratedArtifacts artifacts = JsonMapper.fromJson(new String(content), GeneratedArtifacts.class);
                        return ok200Json(JsonMapper.toJson(artifacts));
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing artifacts", e);
                        return error500("Internal server error");
                    }
                })
                .whenException(e -> log.error("Error retrieving artifacts: {}", artifactsId, e));
    }

    /**
     * POST /api/v1/yappc/generate/runs/{runId}/apply
     * Applies an approved generated artifact run.
     */
    public Promise<HttpResponse> applyReviewDecision(HttpRequest request) {
        return handleReviewDecision(request, GenerationReviewAction.APPLY);
    }

    /**
     * POST /api/v1/yappc/generate/runs/{runId}/reject
     * Rejects a generated artifact run without applying changes.
     */
    public Promise<HttpResponse> rejectReviewDecision(HttpRequest request) {
        return handleReviewDecision(request, GenerationReviewAction.REJECT);
    }

    /**
     * POST /api/v1/yappc/generate/runs/{runId}/rollback
     * Rolls back a previously applied generated artifact run.
     */
    public Promise<HttpResponse> rollbackReviewDecision(HttpRequest request) {
        return handleReviewDecision(request, GenerationReviewAction.ROLLBACK);
    }

    private HttpResponse rateLimitResponseIfNeeded(HttpRequest request, String routeName) {
        String key = routeName + ":" + resolveRequesterKey(request);
        RateLimiter.AcquireResult acquireResult = generationRateLimiter.tryAcquire(key);
        if (acquireResult.allowed()) {
            return null;
        }
        return HttpResponse.ofCode(429)
            .withHeader(HttpHeaders.of("Retry-After"), HttpHeaderValue.of(String.valueOf(acquireResult.retryAfterSeconds())))
            .withJson("{\"error\":\"Generation rate limit exceeded\"}")
            .build();
    }

    private String resolveRequesterKey(HttpRequest request) {
        String apiKey = request.getHeader(HttpHeaders.of("X-API-Key"));
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String authorization = request.getHeader(HttpHeaders.of("Authorization"));
        if (authorization != null && !authorization.isBlank()) {
            return authorization.trim();
        }
        String forwardedFor = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return (commaIndex > 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor).trim();
        }
        return "anonymous";
    }

    private Promise<HttpResponse> handleReviewDecision(HttpRequest request, GenerationReviewAction action) {
        HttpResponse throttled = rateLimitResponseIfNeeded(request, "generate-review-" + action.wireValue());
        if (throttled != null) {
            return Promise.of(throttled);
        }

        String runId = request.getPathParameter("runId");
        if (runId == null || runId.isBlank()) {
            return Promise.of(badRequest400("runId is required"));
        }

        return request.loadBody()
            .then(body -> {
                try {
                    ReviewDecisionBody decisionBody = parseReviewDecisionBody(body.asString(UTF_8));
                    if (decisionBody.projectId() == null || decisionBody.projectId().isBlank()) {
                        throw new IllegalArgumentException("projectId is required");
                    }
                    if (decisionBody.actorId() == null || decisionBody.actorId().isBlank()) {
                        throw new IllegalArgumentException("actorId is required");
                    }

                    GenerationReviewRequest reviewRequest = new GenerationReviewRequest(
                        runId,
                        decisionBody.projectId(),
                        decisionBody.actorId(),
                        decisionBody.reason(),
                        action
                    );

                    return generationService.reviewDecision(reviewRequest)
                        .map(result -> {
                            try {
                                return ok200Json(JsonMapper.toJson(result));
                            } catch (JsonProcessingException e) {
                                log.error("Error serializing review decision response", e);
                                return error500("Internal server error");
                            }
                        });
                } catch (JsonProcessingException | IllegalArgumentException e) {
                    log.error("Error parsing review decision request", e);
                    return Promise.of(badRequest400(e.getMessage() == null ? "Invalid review decision request" : e.getMessage()));
                }
            })
            .whenException(e -> log.error("Generation review decision failed", e));
    }

    private ReviewDecisionBody parseReviewDecisionBody(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new ReviewDecisionBody(null, null, null);
        }
        return JsonMapper.fromJson(json, ReviewDecisionBody.class);
    }

    private ValidatedSpec parseValidatedSpec(String json) throws JsonProcessingException {
        return validateSpec(JsonMapper.fromJson(json, ValidatedSpec.class));
    }

    private ValidatedSpec validateSpec(ValidatedSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("validatedSpec is required");
        }
        if (spec.shapeSpec() == null) {
            throw new IllegalArgumentException("validatedSpec.shapeSpec is required");
        }
        if (spec.shapeSpec().id() == null || spec.shapeSpec().id().isBlank()) {
            throw new IllegalArgumentException("validatedSpec.shapeSpec.id is required");
        }
        if (spec.validationResult() == null) {
            throw new IllegalArgumentException("validatedSpec.validationResult is required");
        }
        if (!spec.validationResult().passed() || spec.validationResult().hasBlockingIssues()) {
            throw new IllegalArgumentException("validatedSpec must pass validation before generation");
        }
        return spec;
    }

    private GeneratedArtifacts validateExistingArtifacts(GeneratedArtifacts existingArtifacts) {
        if (existingArtifacts.id() == null || existingArtifacts.id().isBlank()) {
            throw new IllegalArgumentException("existingArtifacts.id is required");
        }
        if (existingArtifacts.specRef() == null || existingArtifacts.specRef().isBlank()) {
            throw new IllegalArgumentException("existingArtifacts.specRef is required");
        }
        if (existingArtifacts.artifacts() == null) {
            throw new IllegalArgumentException("existingArtifacts.artifacts is required");
        }
        return existingArtifacts;
    }

    private record RegenerateDiffRequest(ValidatedSpec validatedSpec, GeneratedArtifacts existingArtifacts) {
    }

    private record ReviewDecisionBody(String projectId, String actorId, String reason) {
    }
}
