package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.plugin.consent.ConsentPlugin;
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
import java.util.Map;
import java.util.Objects;

/**
 * Canonical consent and suppression API servlet.
 *
 * @doc.type class
 * @doc.purpose HTTP API for consent record/check/revoke/proof and do-not-contact enforcement
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosConsentServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosConsentServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ConsentPlugin consentPlugin;
    private final SuppressionService suppressionService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosConsentServlet(
            ConsentPlugin consentPlugin,
            SuppressionService suppressionService,
            Eventloop eventloop,
            DmosHttpContextFactory httpContextFactory) {
        this.consentPlugin = Objects.requireNonNull(consentPlugin, "consentPlugin must not be null");
        this.suppressionService = Objects.requireNonNull(suppressionService, "suppressionService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/consent", this::handleRecordConsent)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/consent/check", this::handleCheckConsent)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/consent/revoke", this::handleRevokeConsent)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/consent/proof/:subjectId", this::handleConsentProof)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/suppression", this::handleAddSuppression)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/suppression/check", this::handleCheckSuppression)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/unsubscribe", this::handleUnsubscribe)
                .build()
        );
    }

    private Promise<HttpResponse> handleRecordConsent(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                DmOperationContext ctx = context(request, true);
                ConsentRecordRequest body = readBody(request, ConsentRecordRequest.class);
                ConsentPlugin.ConsentAction action = Boolean.TRUE.equals(body.granted())
                    ? ConsentPlugin.ConsentAction.GRANT
                    : ConsentPlugin.ConsentAction.DENY;
                return consentPlugin.recordConsent(body.subjectId(), body.purpose(), action)
                    .map(record -> jsonResponse(201, new ConsentRecordResponse(record, ctx.getWorkspaceId().getValue())))
                    .then(r -> Promise.of(r), e -> handleError(request, e));
            } catch (Exception e) {
                return handleError(request, e);
            }
        });
    }

    private Promise<HttpResponse> handleCheckConsent(HttpRequest request) {
        try {
            context(request, false);
            String subjectId = requiredQuery(request, "subjectId");
            String purpose = requiredQuery(request, "purpose");
            return consentPlugin.verifyConsent(subjectId, purpose)
                .map(valid -> jsonResponse(200, Map.of(
                    "subjectId", subjectId,
                    "purpose", purpose,
                    "valid", valid
                )))
                .then(r -> Promise.of(r), e -> handleError(request, e));
        } catch (Exception e) {
            return handleError(request, e);
        }
    }

    private Promise<HttpResponse> handleRevokeConsent(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                context(request, true);
                ConsentRevokeRequest body = readBody(request, ConsentRevokeRequest.class);
                return consentPlugin.revokeConsent(body.consentId())
                    .map(___ -> jsonResponse(200, Map.of("consentId", body.consentId(), "revoked", true)))
                    .then(r -> Promise.of(r), e -> handleError(request, e));
            } catch (Exception e) {
                return handleError(request, e);
            }
        });
    }

    private Promise<HttpResponse> handleConsentProof(HttpRequest request) {
        try {
            context(request, false);
            String subjectId = request.getPathParameter("subjectId");
            return consentPlugin.getConsentHistory(subjectId)
                .map(records -> jsonResponse(200, Map.of("subjectId", subjectId, "records", records)))
                .then(r -> Promise.of(r), e -> handleError(request, e));
        } catch (Exception e) {
            return handleError(request, e);
        }
    }

    private Promise<HttpResponse> handleAddSuppression(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                DmOperationContext ctx = context(request, true);
                SuppressionRequest body = readBody(request, SuppressionRequest.class);
                return suppressionService.addSuppression(ctx, new SuppressionService.AddSuppressionCommand(body.email(), body.reason()))
                    .map(entry -> jsonResponse(201, new SuppressionResponse(entry.getId(), entry.getContactPointHash(), entry.isActive())))
                    .then(r -> Promise.of(r), e -> handleError(request, e));
            } catch (Exception e) {
                return handleError(request, e);
            }
        });
    }

    private Promise<HttpResponse> handleCheckSuppression(HttpRequest request) {
        try {
            DmOperationContext ctx = context(request, false);
            String email = requiredQuery(request, "email");
            return suppressionService.isSuppressed(ctx, email)
                .map(suppressed -> jsonResponse(200, Map.of("suppressed", suppressed)))
                .then(r -> Promise.of(r), e -> handleError(request, e));
        } catch (Exception e) {
            return handleError(request, e);
        }
    }

    private Promise<HttpResponse> handleUnsubscribe(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                DmOperationContext ctx = context(request, true);
                UnsubscribeRequest body = readBody(request, UnsubscribeRequest.class);
                return suppressionService.addSuppression(ctx, new SuppressionService.AddSuppressionCommand(body.email(), "unsubscribe"))
                    .then(entry -> consentPlugin.recordConsent(body.subjectId(), body.purpose(), ConsentPlugin.ConsentAction.WITHDRAW)
                        .map(record -> jsonResponse(200, Map.of(
                            "suppressed", true,
                            "suppressionHash", entry.getContactPointHash(),
                            "consentId", record.consentId()
                        ))))
                    .then(r -> Promise.of(r), e -> handleError(request, e));
            } catch (Exception e) {
                return handleError(request, e);
            }
        });
    }

    private DmOperationContext context(HttpRequest request, boolean write) {
        return httpContextFactory.buildContext(request, request.getPathParameter("workspaceId"), write);
    }

    private static <T> T readBody(HttpRequest request, Class<T> type) throws java.io.IOException {
        return MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), type);
    }

    private static String requiredQuery(HttpRequest request, String name) {
        String value = request.getQueryParameter(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static Promise<HttpResponse> handleError(HttpRequest request, Throwable e) {
        String correlationId = resolveCorrelationId(request);
        if (e instanceof IllegalArgumentException) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), correlationId, Map.of()));
        }
        if (e instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, "Access denied", correlationId, Map.of()));
        }
        LOG.error("[DMOS] Consent API failure", e);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal error", correlationId, Map.of()));
    }

    private static HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize consent response", e);
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        return header == null || header.isBlank() ? DmCorrelationId.generate().getValue() : header;
    }

    record ConsentRecordRequest(String subjectId, String purpose, Boolean granted) {}
    record ConsentRevokeRequest(String consentId) {}
    record SuppressionRequest(String email, String reason) {}
    record UnsubscribeRequest(String email, String subjectId, String purpose) {}
    record SuppressionResponse(String id, String contactPointHash, boolean active) {}
    record ConsentRecordResponse(ConsentPlugin.ConsentRecord record, String workspaceId) {}
}
