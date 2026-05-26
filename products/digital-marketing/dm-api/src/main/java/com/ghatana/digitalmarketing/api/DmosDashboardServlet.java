package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.analytics.DashboardSummaryService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
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
 * ActiveJ HTTP servlet exposing the canonical DMOS dashboard summary API.
 *
 * <p>The dashboard summary is computed by the backend so the UI, reports, and
 * exports can share the same metric source, freshness, and confidence contract.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS HTTP API servlet for canonical dashboard summary metrics
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosDashboardServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosDashboardServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final DashboardSummaryService dashboardSummaryService;
    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    /**
     * Creates the dashboard summary servlet.
     *
     * @param dashboardSummaryService backend summary service; must not be null
     * @param eventloop               ActiveJ eventloop; must not be null
     * @param httpContextFactory      shared HTTP context factory; must not be null
     */
    public DmosDashboardServlet(
            DashboardSummaryService dashboardSummaryService,
            Eventloop eventloop,
            DmosHttpContextFactory httpContextFactory) {
        this.dashboardSummaryService = Objects.requireNonNull(dashboardSummaryService, "dashboardSummaryService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/dashboard", this::handleGetDashboardSummary)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/dashboard/kpi", this::handleGetKpiMetrics)
                .build(),
            DmosMetricsCollector.disabled(),
            "dashboard"
        );
    }

    private Promise<HttpResponse> handleGetDashboardSummary(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return dashboardSummaryService.computeSummary(ctx)
                .map(summary -> jsonResponse(200, DashboardSummaryResponse.from(summary)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException) {
                        return Promise.of(DmosApiErrorResponses.error(403, "Access denied", resolveCorrelationId(request), Map.of()));
                    }
                    LOG.error("[DMOS] Failed to compute dashboard summary", e);
                    return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (SecurityException e) {
            return Promise.of(DmosApiErrorResponses.error(403, "Access denied", resolveCorrelationId(request), Map.of()));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected dashboard summary error", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleGetKpiMetrics(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String periodParam = request.getQueryParameter("period");
            DashboardSummaryService.ReportPeriod period = periodParam != null
                ? DashboardSummaryService.ReportPeriod.valueOf(periodParam.toUpperCase())
                : DashboardSummaryService.ReportPeriod.MONTHLY;

            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return dashboardSummaryService.computeKpiMetrics(ctx, period)
                .map(report -> jsonResponse(200, KpiMetricsReportResponse.from(report)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException) {
                        return Promise.of(DmosApiErrorResponses.error(403, "Access denied", resolveCorrelationId(request), Map.of()));
                    }
                    LOG.error("[DMOS] Failed to compute KPI metrics", e);
                    return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (SecurityException e) {
            return Promise.of(DmosApiErrorResponses.error(403, "Access denied", resolveCorrelationId(request), Map.of()));
        } catch (Exception e) {
            LOG.error("[DMOS] Unexpected KPI metrics error", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private static HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize dashboard response", e);
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (header == null || header.isBlank()) {
            return DmCorrelationId.generate().getValue();
        }
        return header;
    }

    record DashboardSummaryResponse(
        String workspaceId,
        DashboardSummaryService.CampaignMetrics campaignMetrics,
        DashboardSummaryService.ApprovalMetrics approvalMetrics,
        DashboardSummaryService.BudgetMetrics budgetMetrics,
        DashboardSummaryService.LeadMetrics leadMetrics,
        FreshnessResponse freshness,
        DashboardSummaryService.ConfidenceLevel confidence,
        String metricSource,
        String formulaVersion,
        String authorizationScope,
        boolean partialData
    ) {
        static DashboardSummaryResponse from(DashboardSummaryService.DashboardSummary summary) {
            boolean partialData = summary.confidence() != DashboardSummaryService.ConfidenceLevel.HIGH;
            return new DashboardSummaryResponse(
                summary.workspaceId(),
                summary.campaignMetrics(),
                summary.approvalMetrics(),
                summary.budgetMetrics(),
                summary.leadMetrics(),
                FreshnessResponse.from(summary.freshness()),
                summary.confidence(),
                "DMOS_BACKEND_SUMMARY",
                "dashboard-summary.v1",
                "tenant_workspace",
                partialData
            );
        }
    }

    record FreshnessResponse(
        String lastUpdated,
        long stalenessSeconds,
        DashboardSummaryService.StalenessStatus status
    ) {
        static FreshnessResponse from(DashboardSummaryService.FreshnessInfo freshness) {
            return new FreshnessResponse(
                freshness.lastUpdated().toString(),
                freshness.staleness().toSeconds(),
                freshness.status()
            );
        }
    }

    record KpiMetricsReportResponse(
        String workspaceId,
        String period,
        String reportGeneratedAt,
        DashboardSummaryService.CampaignKpiMetrics campaignKpi,
        DashboardSummaryService.BudgetKpiMetrics budgetKpi,
        DashboardSummaryService.LeadKpiMetrics leadKpi,
        DashboardSummaryService.ConversionKpiMetrics conversionKpi,
        DashboardSummaryService.RoiKpiMetrics roiKpi
    ) {
        static KpiMetricsReportResponse from(DashboardSummaryService.KpiMetricsReport report) {
            return new KpiMetricsReportResponse(
                report.workspaceId(),
                report.period().name(),
                report.reportGeneratedAt().toString(),
                report.campaignKpi(),
                report.budgetKpi(),
                report.leadKpi(),
                report.conversionKpi(),
                report.roiKpi()
            );
        }
    }
}
