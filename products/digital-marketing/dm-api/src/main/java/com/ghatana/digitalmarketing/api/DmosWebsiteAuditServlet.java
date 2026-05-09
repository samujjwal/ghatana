package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditService;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HTTP servlet for website audit generation and retrieval.
 *
 * P2-025: Uses DmosHttpContextFactory for server-side identity derivation to prevent
 * spoofed identity attacks (P0-015). Client-provided X-Roles/X-Permissions headers are
 * ignored in production mode.
 *
 * @doc.type class
 * @doc.purpose DMOS website audit API servlet for F1-010 website diagnostics
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosWebsiteAuditServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosWebsiteAuditServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final WebsiteAuditService auditService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosWebsiteAuditServlet(WebsiteAuditService auditService, Eventloop eventloop, DmosHttpContextFactory httpContextFactory) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosWebsiteAuditServlet(WebsiteAuditService auditService, Eventloop eventloop) {
        this(auditService, eventloop, new DmosHttpContextFactory(false, null));
    }

    /**
     * Returns the routing servlet for website audit endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/audit/run", this::handleRunAudit)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/audit/latest", this::handleGetLatestAudit)
            .build()
        );
    }

    private Promise<HttpResponse> handleRunAudit(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                RunAuditRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    RunAuditRequest.class);

                return auditService.runAudit(
                    ctx,
                    new WebsiteAuditService.RunWebsiteAuditCommand(
                        body.websiteUrl(),
                        body.reachable(),
                        body.responseTimeMs(),
                        body.title(),
                        body.metaDescription(),
                        body.h1(),
                        body.trackingTagDetected(),
                        body.hasLeadForm()))
                    .map(report -> jsonResponse(200, AuditReportResponse.from(report)))
                    .then(r -> Promise.of(r), e -> mapServiceError("run audit", e, request));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to run audit", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestAudit(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return auditService.getLatestAudit(ctx)
                .map(report -> jsonResponse(200, AuditReportResponse.from(report)))
                .then(r -> Promise.of(r), e -> mapServiceError("get audit", e, request));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get audit", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error, HttpRequest request) {
        if (error instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, "Access denied", request));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(DmosApiErrorResponses.error(404, error.getMessage(), request));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(DmosApiErrorResponses.error(400, error.getMessage(), request));
        }
        if (error instanceof IllegalStateException) {
            return Promise.of(DmosApiErrorResponses.error(409, error.getMessage(), request));
        }
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(DmosApiErrorResponses.error(423, error.getMessage(), request));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
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

    record RunAuditRequest(
        String websiteUrl,
        boolean reachable,
        int responseTimeMs,
        String title,
        String metaDescription,
        String h1,
        boolean trackingTagDetected,
        boolean hasLeadForm
    ) {
    }

    record AuditFindingDto(
        String severity,
        String category,
        String evidence,
        String rationale,
        String recommendedAction,
        String sourceUrl
    ) {
        static AuditFindingDto from(WebsiteAuditFinding f) {
            return new AuditFindingDto(
                f.severity().name(),
                f.category(),
                f.evidence(),
                f.rationale(),
                f.recommendedAction(),
                f.sourceUrl()
            );
        }
    }

    record AuditReportResponse(
        String reportId,
        String workspaceId,
        String websiteUrl,
        List<AuditFindingDto> findings,
        Instant generatedAt,
        String generatedBy
    ) {
        static AuditReportResponse from(WebsiteAuditReport r) {
            return new AuditReportResponse(
                r.getReportId(),
                r.getWorkspaceId().getValue(),
                r.getWebsiteUrl(),
                r.getFindings().stream().map(AuditFindingDto::from).collect(Collectors.toList()),
                r.getGeneratedAt(),
                r.getGeneratedBy()
            );
        }
    }

}
