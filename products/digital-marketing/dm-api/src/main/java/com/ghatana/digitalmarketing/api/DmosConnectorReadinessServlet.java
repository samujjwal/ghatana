package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadiness;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsConnectorReadinessService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
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

import java.util.Map;
import java.util.Objects;

/**
 * HTTP API for connector runtime readiness.
 *
 * @doc.type class
 * @doc.purpose Exposes persisted Google Ads connector readiness for cockpit/runtime truth
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosConnectorReadinessServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosConnectorReadinessServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";
    private static final String CONNECTOR_CAPABILITY = "dmos.connectors";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final DmGoogleAdsConnectorReadinessService googleAdsReadinessService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosConnectorReadinessServlet(
            DmGoogleAdsConnectorReadinessService googleAdsReadinessService,
            Eventloop eventloop) {
        this(googleAdsReadinessService, eventloop, new DmosHttpContextFactory(false, null));
    }

    public DmosConnectorReadinessServlet(
            DmGoogleAdsConnectorReadinessService googleAdsReadinessService,
            Eventloop eventloop,
            DmosHttpContextFactory httpContextFactory) {
        this.googleAdsReadinessService = Objects.requireNonNull(
            googleAdsReadinessService,
            "googleAdsReadinessService must not be null"
        );
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(
                    HttpMethod.GET,
                    "/v1/workspaces/:workspaceId/connectors/google-ads/:connectorId/readiness",
                    this::handleGoogleAdsReadiness
                )
                .build(),
            DmosMetricsCollector.disabled(),
            "connector-readiness"
        );
    }

    private Promise<HttpResponse> handleGoogleAdsReadiness(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String connectorId = request.getPathParameter("connectorId");
            DmOperationContext ctx = httpContextFactory.buildContext(
                request,
                workspaceId,
                false,
                CONNECTOR_CAPABILITY,
                "read"
            );

            return googleAdsReadinessService.checkReadiness(ctx, connectorId)
                .map(readiness -> jsonResponse(200, GoogleAdsReadinessResponse.from(readiness)))
                .then(response -> Promise.of(response), error -> {
                    if (error instanceof SecurityException) {
                        return Promise.of(DmosApiErrorResponses.error(
                            403,
                            "Access denied",
                            resolveCorrelationId(request),
                            Map.of("connectorId", connectorId)
                        ));
                    }
                    if (error instanceof IllegalArgumentException) {
                        return Promise.of(DmosApiErrorResponses.error(
                            400,
                            error.getMessage(),
                            resolveCorrelationId(request),
                            Map.of("connectorId", connectorId)
                        ));
                    }
                    LOG.error("[DMOS] Failed to resolve Google Ads readiness", error);
                    return Promise.of(DmosApiErrorResponses.error(
                        500,
                        "Internal error",
                        resolveCorrelationId(request),
                        Map.of("connectorId", connectorId)
                    ));
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(
                400,
                e.getMessage(),
                resolveCorrelationId(request),
                Map.of("request", e.getMessage())
            ));
        } catch (SecurityException e) {
            return Promise.of(DmosApiErrorResponses.error(
                403,
                "Access denied",
                resolveCorrelationId(request),
                Map.of()
            ));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected connector readiness error", e);
            return Promise.of(DmosApiErrorResponses.error(
                500,
                "Internal error",
                resolveCorrelationId(request),
                Map.of()
            ));
        }
    }

    private static HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize connector readiness response", e);
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (header == null || header.isBlank()) {
            return DmCorrelationId.generate().getValue();
        }
        return header;
    }

    record GoogleAdsReadinessResponse(
        String connectorId,
        String provider,
        String readinessState,
        String connectorStatus,
        String reason,
        String checkedAt,
        boolean ready,
        String source
    ) {
        static GoogleAdsReadinessResponse from(DmGoogleAdsConnectorReadiness readiness) {
            return new GoogleAdsReadinessResponse(
                readiness.connectorId(),
                "google-ads",
                readiness.readinessState().name(),
                readiness.connectorStatus().name(),
                readiness.reason(),
                readiness.checkedAt().toString(),
                readiness.ready(),
                "DMOS_CONNECTOR_RUNTIME"
            );
        }
    }
}
