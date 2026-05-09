package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.research.CompetitorResearchService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.research.CompetitorFinding;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import com.ghatana.digitalmarketing.domain.research.KeywordFinding;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP servlet for competitor and keyword research run and retrieval.
 *
 * @doc.type class
 * @doc.purpose DMOS competitor and keyword research API servlet for F1-011
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosCompetitorResearchServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosCompetitorResearchServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final CompetitorResearchService researchService;
    private final Eventloop eventloop;

    public DmosCompetitorResearchServlet(CompetitorResearchService researchService, Eventloop eventloop) {
        this.researchService = Objects.requireNonNull(researchService, "researchService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/research/competitor", this::handleRunResearch)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/research/competitor", this::handleGetLatestResearch)
            .build()
        );
    }

    private Promise<HttpResponse> handleRunResearch(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                RunResearchRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    RunResearchRequest.class
                );

                CompetitorResearchService.RunCompetitorResearchCommand command =
                    new CompetitorResearchService.RunCompetitorResearchCommand(
                        body.competitorDomains(),
                        body.serviceArea(),
                        body.primaryOffer()
                    );

                return researchService.runResearch(ctx, command)
                    .map(snap -> jsonResponse(200, ResearchResponse.from(snap)))
                    .then(r -> Promise.of(r), e -> mapServiceError("run competitor research", e, request));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to run competitor research", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestResearch(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return researchService.getLatestResearch(ctx)
                .map(snap -> jsonResponse(200, ResearchResponse.from(snap)))
                .then(r -> Promise.of(r), e -> mapServiceError("get competitor research", e, request));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get competitor research", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
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

    record RunResearchRequest(
        List<String> competitorDomains,
        String serviceArea,
        String primaryOffer
    ) {
    }

    record CompetitorFindingDto(
        String competitorDomain,
        String observedFact,
        String interpretation,
        boolean isInferred,
        String source
    ) {
        static CompetitorFindingDto from(CompetitorFinding f) {
            return new CompetitorFindingDto(
                f.competitorDomain(), f.observedFact(), f.interpretation(), f.isInferred(), f.source()
            );
        }
    }

    record KeywordFindingDto(
        String keyword,
        String intent,
        double relevanceScore,
        String suggestedCampaignUse,
        String evidence,
        String source
    ) {
        static KeywordFindingDto from(KeywordFinding k) {
            return new KeywordFindingDto(
                k.keyword(), k.intent().name(), k.relevanceScore(),
                k.suggestedCampaignUse(), k.evidence(), k.source()
            );
        }
    }

    record ResearchResponse(
        String snapshotId,
        String workspaceId,
        List<CompetitorFindingDto> competitorFindings,
        List<KeywordFindingDto> keywordFindings,
        String opportunitySummary,
        String generatedAt,
        String generatedBy
    ) {
        static ResearchResponse from(CompetitorResearchSnapshot snap) {
            return new ResearchResponse(
                snap.getSnapshotId(),
                snap.getWorkspaceId().getValue(),
                snap.getCompetitorFindings().stream().map(CompetitorFindingDto::from).toList(),
                snap.getKeywordFindings().stream().map(KeywordFindingDto::from).toList(),
                snap.getOpportunitySummary(),
                snap.getGeneratedAt().toString(),
                snap.getGeneratedBy()
            );
        }
    }

}
