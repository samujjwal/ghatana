package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.scoring.LeadScoringService;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import com.ghatana.digitalmarketing.domain.scoring.ScoreDimension;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * HTTP servlet for lead scoring generation and retrieval.
 *
 * P2-025: Uses DmosHttpContextFactory for server-side identity derivation to prevent
 * spoofed identity attacks (P0-015). Client-provided X-Roles/X-Permissions headers are
 * ignored in production mode.
 *
 * @doc.type class
 * @doc.purpose DMOS lead scoring API servlet for F1-012 prospect prioritization
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosLeadScoringServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosLeadScoringServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final LeadScoringService leadScoringService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    /**
     * Creates the DMOS lead scoring servlet.
     *
     * @param leadScoringService the lead scoring service; must not be null
     * @param eventloop the eventloop; must not be null
     * @param httpContextFactory the shared HTTP context factory for fail-closed security; must not be null
     */
    public DmosLeadScoringServlet(LeadScoringService leadScoringService, Eventloop eventloop, DmosHttpContextFactory httpContextFactory) {
        this.leadScoringService = Objects.requireNonNull(leadScoringService, "leadScoringService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    /**
     * Returns the routing servlet for lead scoring endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/lead-score", this::handleGenerateScore)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/lead-score", this::handleGetLatestScore)
            .build()
        );
    }

    private Promise<HttpResponse> handleGenerateScore(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                GenerateScoreRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateScoreRequest.class
                );

                LeadScoringService.GenerateLeadScoreCommand command =
                    new LeadScoringService.GenerateLeadScoreCommand(
                        body.intakeCompletionPct(),
                        body.auditFindingCount(),
                        body.trackingGapsDetected(),
                        body.keywordOpportunityCount(),
                        body.serviceArea(),
                        body.monthlyBudgetHint()
                    );

                return leadScoringService.generateScore(ctx, command)
                    .map(score -> jsonResponse(200, LeadScoreResponse.from(score)))
                    .then(r -> Promise.of(r), e -> mapServiceError("generate lead score", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to generate lead score", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestScore(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return leadScoringService.getLatestScore(ctx)
                .map(score -> jsonResponse(200, LeadScoreResponse.from(score)))
                .then(r -> Promise.of(r), e -> mapServiceError("get lead score", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get lead score", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error) {
        if (error instanceof SecurityException) {
            return Promise.of(errorResponse(403, error.getMessage()));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(errorResponse(404, error.getMessage()));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(errorResponse(400, error.getMessage()));
        }
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(errorResponse(423, error.getMessage()));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error"));
    }

    // P2-025: Using shared DmosHttpContextFactory for server-side identity derivation
    // instead of parsing headers directly. This prevents spoofed identity attacks (P0-015).

    private HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            LOG.error("[DMOS] Serialization failure", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        return jsonResponse(code, new ErrorBody(code, message));
    }

    record GenerateScoreRequest(
        int intakeCompletionPct,
        int auditFindingCount,
        boolean trackingGapsDetected,
        int keywordOpportunityCount,
        String serviceArea,
        int monthlyBudgetHint
    ) {
    }

    record ScoreDimensionDto(String dimension, int points, String rationale) {
        static ScoreDimensionDto from(ScoreDimension d) {
            return new ScoreDimensionDto(d.dimension(), d.points(), d.rationale());
        }
    }

    record LeadScoreResponse(
        String scoreId,
        String workspaceId,
        int score,
        String grade,
        List<ScoreDimensionDto> dimensions,
        double confidence,
        boolean requiresHumanReview,
        String recommendedNextAction,
        String modelVersion,
        Instant scoredAt,
        String scoredBy
    ) {
        static LeadScoreResponse from(LeadScore s) {
            return new LeadScoreResponse(
                s.getScoreId(),
                s.getWorkspaceId().getValue(),
                s.getScore(),
                s.getGrade().name(),
                s.getDimensions().stream().map(ScoreDimensionDto::from).collect(Collectors.toList()),
                s.getConfidence(),
                s.isRequiresHumanReview(),
                s.getRecommendedNextAction(),
                s.getModelVersion(),
                s.getScoredAt(),
                s.getScoredBy()
            );
        }
    }

    record ErrorBody(int status, String message) {
    }
}
