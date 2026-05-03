package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.scoring.LeadScoringService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import com.ghatana.digitalmarketing.domain.scoring.ScoreDimension;
import com.ghatana.kernel.security.TenantSecurityContext;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP servlet for lead scoring generation and retrieval.
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

    public DmosLeadScoringServlet(LeadScoringService leadScoringService, Eventloop eventloop) {
        this.leadScoringService = Objects.requireNonNull(leadScoringService, "leadScoringService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
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
                DmOperationContext ctx = buildContext(request, workspaceId, true);
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
            DmOperationContext ctx = buildContext(request, workspaceId, false);

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
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error"));
    }

    private DmOperationContext buildContext(HttpRequest request, String workspaceId, boolean requireIdempotencyKey) {
        String tenantId = getRequiredHeader(request, "X-Tenant-ID");
        String principal = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID", DmCorrelationId.generate().getValue());
        String idempotencyKeyValue = getHeader(request, "X-Idempotency-Key", null);
        String sessionId = getHeader(request, "X-Session-ID", null);
        Set<String> roles = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> permissions = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        if (requireIdempotencyKey && (idempotencyKeyValue == null || idempotencyKeyValue.isBlank())) {
            throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
        }

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);
        DmIdempotencyKey idempotencyKey =
            (idempotencyKeyValue != null && !idempotencyKeyValue.isBlank())
                ? DmIdempotencyKey.of(idempotencyKeyValue)
                : null;

        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlationId))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext,
            sessionId,
            roles,
            permissions,
            null
        );

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            workspace,
            DmCorrelationId.of(correlationId),
            idempotencyKey
        );
    }

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getRequiredHeader(HttpRequest request, String name) {
        String value = request.getHeader(HttpHeaders.of(name));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required header missing: " + name);
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

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
