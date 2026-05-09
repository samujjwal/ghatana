package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.intake.IntakeQuestionnaireService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP servlet for intake questionnaire draft, resume, and submit flows.
 *
 * @doc.type class
 * @doc.purpose DMOS intake questionnaire API servlet for F1-009 business profile capture
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosIntakeQuestionnaireServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosIntakeQuestionnaireServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final IntakeQuestionnaireService intakeService;
    private final Eventloop eventloop;

    public DmosIntakeQuestionnaireServlet(IntakeQuestionnaireService intakeService, Eventloop eventloop) {
        this.intakeService = Objects.requireNonNull(intakeService, "intakeService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.PUT, "/v1/workspaces/:workspaceId/intake/questionnaire/draft", this::handleSaveDraft)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/intake/questionnaire/draft", this::handleGetDraft)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/intake/questionnaire/submit", this::handleSubmit)
            .build()
        );
    }

    private Promise<HttpResponse> handleSaveDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                SaveDraftRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    SaveDraftRequest.class
                );

                IntakeQuestionnaireService.SaveDraftCommand command = new IntakeQuestionnaireService.SaveDraftCommand(
                    body.businessName(),
                    body.websiteUrl(),
                    body.offerSummary(),
                    body.targetAudience(),
                    body.primaryGeography(),
                    body.monthlyBudgetAmount(),
                    body.competitorDomains(),
                    body.constraints(),
                    body.growthGoal(),
                    body.riskTolerance()
                );

                return intakeService.saveDraft(ctx, command)
                    .map(saved -> jsonResponse(200, IntakeResponse.from(saved)))
                    .then(r -> Promise.of(r), e -> mapServiceError("save intake draft", e, request));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to save intake draft", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private Promise<HttpResponse> handleGetDraft(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return intakeService.getDraft(ctx)
                .map(saved -> jsonResponse(200, IntakeResponse.from(saved)))
                .then(r -> Promise.of(r), e -> mapServiceError("get intake draft", e, request));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get intake draft", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
    }

    private Promise<HttpResponse> handleSubmit(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                SubmitIntakeRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    SubmitIntakeRequest.class
                );

                IntakeQuestionnaireService.SubmitIntakeCommand command =
                    new IntakeQuestionnaireService.SubmitIntakeCommand(
                        body.aiSummary(),
                        body.aiConfidenceScore(),
                        body.aiUnknowns()
                    );

                return intakeService.submitIntake(ctx, command)
                    .map(saved -> jsonResponse(200, IntakeResponse.from(saved)))
                    .then(r -> Promise.of(r), e -> mapServiceError("submit intake", e, request));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to submit intake", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error, HttpRequest request) {
        if (error instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, error.getMessage(), request));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(DmosApiErrorResponses.error(404, error.getMessage(), request));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(DmosApiErrorResponses.error(400, error.getMessage(), request));
        }
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(DmosApiErrorResponses.error(423, error.getMessage(), request));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
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
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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

    record SaveDraftRequest(
        String businessName,
        String websiteUrl,
        String offerSummary,
        String targetAudience,
        String primaryGeography,
        BigDecimal monthlyBudgetAmount,
        java.util.List<String> competitorDomains,
        java.util.List<String> constraints,
        String growthGoal,
        String riskTolerance
    ) {
    }

    record SubmitIntakeRequest(String aiSummary, double aiConfidenceScore, java.util.List<String> aiUnknowns) {
    }

    record IntakeResponse(
        String intakeId,
        String workspaceId,
        String businessName,
        String websiteUrl,
        String offerSummary,
        String targetAudience,
        String primaryGeography,
        BigDecimal monthlyBudgetAmount,
        java.util.List<String> competitorDomains,
        java.util.List<String> constraints,
        String growthGoal,
        String riskTolerance,
        String aiSummary,
        double aiConfidenceScore,
        java.util.List<String> aiUnknowns,
        String status,
        String createdAt,
        String updatedAt,
        String submittedAt
    ) {
        static IntakeResponse from(BusinessIntakeProfile profile) {
            return new IntakeResponse(
                profile.getIntakeId(),
                profile.getWorkspaceId().getValue(),
                profile.getBusinessName(),
                profile.getWebsiteUrl(),
                profile.getOfferSummary(),
                profile.getTargetAudience(),
                profile.getPrimaryGeography(),
                profile.getMonthlyBudgetAmount(),
                profile.getCompetitorDomains(),
                profile.getConstraints(),
                profile.getGrowthGoal(),
                profile.getRiskTolerance(),
                profile.getAiSummary(),
                profile.getAiConfidenceScore(),
                profile.getAiUnknowns(),
                profile.getStatus().name(),
                profile.getCreatedAt().toString(),
                profile.getUpdatedAt().toString(),
                profile.getSubmittedAt() != null ? profile.getSubmittedAt().toString() : null
            );
        }
    }

}
