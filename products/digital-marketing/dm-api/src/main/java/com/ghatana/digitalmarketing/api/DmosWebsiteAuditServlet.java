package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.audit.WebsiteAuditService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
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
 * HTTP servlet for website audit generation and retrieval.
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

    public DmosWebsiteAuditServlet(WebsiteAuditService auditService, Eventloop eventloop) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for website audit endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/audit/run", this::handleRunAudit)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/audit/latest", this::handleGetLatestAudit)
            .build();
    }

    private Promise<HttpResponse> handleRunAudit(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                RunAuditRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    RunAuditRequest.class
                );

                WebsiteAuditService.RunWebsiteAuditCommand command =
                    new WebsiteAuditService.RunWebsiteAuditCommand(
                        body.websiteUrl(),
                        body.reachable(),
                        body.responseTimeMs(),
                        body.title(),
                        body.metaDescription(),
                        body.h1(),
                        body.trackingTagDetected(),
                        body.hasLeadForm()
                    );

                return auditService.runAudit(ctx, command)
                    .map(report -> jsonResponse(200, AuditReportResponse.from(report)))
                    .then(r -> Promise.of(r), e -> mapServiceError("run audit", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to run website audit", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestAudit(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return auditService.getLatestAudit(ctx)
                .map(report -> jsonResponse(200, AuditReportResponse.from(report)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest audit", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get latest audit", e);
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
        if (error instanceof IllegalStateException) {
            return Promise.of(errorResponse(409, error.getMessage()));
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

    record ErrorBody(int status, String message) {
    }
}
