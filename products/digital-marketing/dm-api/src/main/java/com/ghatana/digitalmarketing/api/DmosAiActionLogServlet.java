package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.transparency.AiActionLogService;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
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
 * HTTP servlet for transparency and AI action log APIs (F1-025).
 *
 * @doc.type class
 * @doc.purpose DMOS transparency action log API
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosAiActionLogServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAiActionLogServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final AiActionLogService service;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosAiActionLogServlet(AiActionLogService service, Eventloop eventloop, DmosMetricsCollector metrics, DmosHttpContextFactory httpContextFactory) {
        this.service            = Objects.requireNonNull(service,            "service must not be null");
        this.eventloop           = Objects.requireNonNull(eventloop,           "eventloop must not be null");
        this.metrics             = Objects.requireNonNull(metrics,             "metrics must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosAiActionLogServlet(AiActionLogService service, Eventloop eventloop) {
        this(service, eventloop, DmosMetricsCollector.disabled(), new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet routes() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/ai-actions", this::handleRecord)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/ai-actions", this::handleList)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/ai-actions/:actionId", this::handleGet)
                .build(),
            metrics,
            "aiactions"
        );
    }

    private Promise<HttpResponse> handleRecord(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                RecordRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    RecordRequest.class
                );
                AiActionLogService.RecordActionCommand command = new AiActionLogService.RecordActionCommand(
                    body.correlationId(),
                    body.actionType(),
                    body.status(),
                    body.actor(),
                    body.initiatedByAi(),
                    body.provider(),
                    body.modelVersion(),
                    body.humanEdited(),
                    body.confidence(),
                    body.evidenceLinks(),
                    body.policyChecks(),
                    body.summary(),
                    body.details(),
                    body.relatedEntityId(),
                    null,
                    null,
                    null
                );
                return service.recordAction(ctx, command)
                    .map(entry -> jsonResponse(201, EntryResponse.from(entry)))
                    .then(r -> Promise.of(r), e -> mapServiceError("record action", e, ctx));
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to record action", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private Promise<HttpResponse> handleList(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            String correlationId = request.getQueryParameter("correlationId");
            String relatedEntityId = request.getQueryParameter("relatedEntityId");
            int limit = parseLimit(request.getQueryParameter("limit"));
            return service.listActions(ctx, new AiActionLogService.ListActionsQuery(
                    correlationId,
                    relatedEntityId,
                    limit
                ))
                .map(entries -> new ListResponse(entries.stream().map(EntryResponse::from).toList()))
                .map(res -> jsonResponse(200, res))
                .then(r -> Promise.of(r), e -> mapServiceError("list actions", e, ctx));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to list actions", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
    }

    private Promise<HttpResponse> handleGet(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String actionId = request.getPathParameter("actionId");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.getAction(ctx, actionId)
                .map(entry -> jsonResponse(200, EntryResponse.from(entry)))
                .then(r -> Promise.of(r), e -> mapServiceError("get action", e, ctx));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get action", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) return 50;
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0) throw new IllegalArgumentException("limit must be > 0");
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("limit must be an integer");
        }
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error, DmOperationContext ctx) {
        if (error instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, error.getMessage(), ctx.getCorrelationId().getValue()));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(DmosApiErrorResponses.error(404, error.getMessage(), ctx.getCorrelationId().getValue()));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(DmosApiErrorResponses.error(400, error.getMessage(), ctx.getCorrelationId().getValue()));
        }
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(DmosApiErrorResponses.error(423, error.getMessage(), ctx.getCorrelationId().getValue()));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal error", ctx.getCorrelationId().getValue()));
    }

    // P1-001: Local buildContext method removed - using shared DmosHttpContextFactory

    // P1-001: Local helper methods removed - using shared DmosHttpContextFactory

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

    record RecordRequest(
            String correlationId,
            AiActionType actionType,
            AiActionStatus status,
            String actor,
            boolean initiatedByAi,
            String provider,
            String modelVersion,
            boolean humanEdited,
            Double confidence,
            List<String> evidenceLinks,
            List<String> policyChecks,
            String summary,
            String details,
            String relatedEntityId) {
    }

    record EntryResponse(
            String actionId,
            String workspaceId,
            String correlationId,
            String actionType,
            String status,
            String actor,
            boolean initiatedByAi,
            String provider,
            String modelVersion,
            boolean humanEdited,
            Double confidence,
            List<String> evidenceLinks,
            List<String> policyChecks,
            String summary,
            String details,
            String relatedEntityId,
            java.time.Instant occurredAt) {
        static EntryResponse from(AiActionLogEntry entry) {
            return new EntryResponse(
                entry.actionId(),
                entry.workspaceId(),
                entry.correlationId(),
                entry.actionType().name(),
                entry.status().name(),
                entry.actor(),
                entry.initiatedByAi(),
                entry.provider(),
                entry.modelVersion(),
                entry.humanEdited(),
                entry.confidence(),
                entry.evidenceLinks(),
                entry.policyChecks(),
                entry.summary(),
                entry.details(),
                entry.relatedEntityId(),
                entry.occurredAt()
            );
        }
    }

    record ListResponse(List<EntryResponse> entries) {
    }
}
