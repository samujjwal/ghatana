package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.optimization.NextBestActionRecommendationService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosNextBestActionRecommendationServlet")
class DmosNextBestActionRecommendationServletTest extends EventloopTestBase {

    private FakeNextBestActionService service;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        service = new FakeNextBestActionService();
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) ->
            new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "user-1",
                "session-1",
                Set.of("operator"),
                Set.of("dmos.ai_optimization"),
                true
            );

        servlet = new DmosNextBestActionRecommendationServlet(
            service,
            Eventloop.create(),
            DmosMetricsCollector.noop(),
            new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()),
            new DmosHttpContextFactory(true, identityProvider)
        ).getServlet();
    }

    @Test
    @DisplayName("GET list returns 400 when tenant header is missing")
    void listReturns400WhenTenantMissing() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer token")
            .build();

        HttpResponse response = run(request);

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 400 when Authorization header is missing")
    void listReturns400WhenAuthorizationMissing() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = run(request);

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST publish returns 400 when idempotency key is missing")
    void publishReturns400WhenIdempotencyMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(("{" +
                "\"campaignId\":\"cmp-1\"," +
                "\"actionType\":\"INCREASE_BUDGET\"," +
                "\"title\":\"Scale\"," +
                "\"description\":\"scale budget\"," +
                "\"parameters\":{}," +
                "\"confidenceScore\":0.9," +
                "\"rationale\":\"performing well\"," +
                "\"expiresAt\":\"2030-01-01T00:00:00Z\"}" ).getBytes())
            .build();

        HttpResponse response = run(request);

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 403 when optimization capability is disabled")
    void listReturns403WhenOptimizationCapabilityDisabled() {
        DmosHttpContextFactory.IdentityProvider noOptimizationPermission = (token, tenantId) ->
            new DmosHttpContextFactory.IdentityProvider.IdentityResult(
                "user-1",
                "session-1",
                Set.of("operator"),
                Set.of("dmos.campaigns"),
                true
            );

        servlet = new DmosNextBestActionRecommendationServlet(
            service,
            Eventloop.create(),
            DmosMetricsCollector.noop(),
            new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()),
            new DmosHttpContextFactory(true, noOptimizationPermission)
        ).getServlet();

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/next-best-action-recommendations")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer token")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = run(request);

        assertThat(response.getCode()).isEqualTo(403);
    }

    private HttpResponse run(HttpRequest request) {
        return runPromise(() -> servlet.serve(request));
    }

    private static final class FakeNextBestActionService implements NextBestActionRecommendationService {
        @Override
        public Promise<NextBestActionRecommendation> publish(DmOperationContext ctx, PublishRecommendationCommand command) {
            return Promise.of(recommendation());
        }

        @Override
        public Promise<NextBestActionRecommendation> approve(DmOperationContext ctx, String recommendationId, String executedBy) {
            return Promise.of(recommendation());
        }

        @Override
        public Promise<NextBestActionRecommendation> reject(DmOperationContext ctx, String recommendationId, String reason) {
            return Promise.of(recommendation());
        }

        @Override
        public Promise<NextBestActionRecommendation> expire(DmOperationContext ctx, String recommendationId) {
            return Promise.of(recommendation());
        }

        @Override
        public Promise<Optional<NextBestActionRecommendation>> findById(DmOperationContext ctx, String recommendationId) {
            return Promise.of(Optional.of(recommendation()));
        }

        @Override
        public Promise<List<NextBestActionRecommendation>> listByWorkspace(DmOperationContext ctx) {
            return Promise.of(List.of(recommendation()));
        }

        @Override
        public Promise<List<NextBestActionRecommendation>> listByCampaign(DmOperationContext ctx, String campaignId) {
            return Promise.of(List.of(recommendation()));
        }

        @Override
        public Promise<List<NextBestActionRecommendation>> listByStatus(DmOperationContext ctx, NextBestActionStatus status) {
            return Promise.of(List.of(recommendation()));
        }

        private static NextBestActionRecommendation recommendation() {
            return NextBestActionRecommendation.builder()
                .id("rec-1")
                .tenantId("tenant-1")
                .workspaceId("ws-1")
                .campaignId("cmp-1")
                .actionType(NextBestActionType.INCREASE_BUDGET)
                .title("Scale Budget")
                .description("Increase budget by 10%")
                .parameters(Map.of())
                .confidenceScore(0.9)
                .rationale("high ROI")
                .status(NextBestActionStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        }
    }
}
