package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.application.capabilities.DmosCapabilityRegistry;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Objects;

/**
 * Boundary reporting servlet for locked analytics surfaces.
 *
 * <p>These routes are part of the canonical product contract, but their
 * production analytics runtime is not yet complete. They fail explicitly with
 * HTTP 423 instead of returning synthetic metrics or empty success payloads.</p>
 *
 * @doc.type class
 * @doc.purpose Explicit locked responses for boundary reporting APIs
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosBoundaryReportingServlet {

    private final Eventloop eventloop;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosBoundaryReportingServlet(Eventloop eventloop, DmosHttpContextFactory httpContextFactory) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/funnel-analytics", request ->
                    handleLockedReportingRoute(request, "Funnel analytics"))
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/attribution", request ->
                    handleLockedReportingRoute(request, "Attribution reporting"))
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/roi-roas", request ->
                    handleLockedReportingRoute(request, "ROI/ROAS reporting"))
                .build(),
            DmosMetricsCollector.noop(),
            "boundary-reporting"
        );
    }

    private Promise<HttpResponse> handleLockedReportingRoute(HttpRequest request, String featureName) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            httpContextFactory.buildContext(request, workspaceId, false, DmosCapabilityRegistry.REPORTING);
            return Promise.of(DmosApiErrorResponses.error(
                423,
                featureName + " is locked until the canonical analytics runtime is available.",
                DmosApiHeaderValidator.getCorrelationId(request),
                Map.of(
                    "capability", DmosCapabilityRegistry.REPORTING,
                    "workspaceId", workspaceId,
                    "reason", "boundary_feature"
                )
            ));
        } catch (SecurityException e) {
            return Promise.of(DmosApiErrorResponses.error(403, e.getMessage(), request));
        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
        }
    }
}
