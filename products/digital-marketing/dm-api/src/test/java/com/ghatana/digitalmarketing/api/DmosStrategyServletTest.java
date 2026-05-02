package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.CampaignPlan;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyChannel;
import com.ghatana.digitalmarketing.domain.strategy.StrategyGoal;
import com.ghatana.digitalmarketing.domain.strategy.StrategyStatus;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosStrategyServlet")
class DmosStrategyServletTest extends EventloopTestBase {

    private FakeStrategyService strategyService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        strategyService = new FakeStrategyService();
        servlet = new DmosStrategyServlet(strategyService, Eventloop.create()).getServlet();
    }

    private static final String GENERATE_BODY =
        "{\"intakeCompletionPct\":80,\"serviceArea\":\"New York\",\"monthlyBudget\":2000," +
        "\"auditFindingCount\":2,\"trackingGapsDetected\":true,\"keywordOpportunityCount\":4," +
        "\"topCompetitorCount\":2,\"primaryOffer\":\"HVAC\"}";

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosStrategyServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosStrategyServlet(strategyService, null));
    }

    @Test
    @DisplayName("POST /strategy generates strategy and returns 200")
    void shouldGenerateStrategy() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(strategyService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /strategy returns 400 when X-Idempotency-Key is missing")
    void shouldRejectGenerateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy returns 400 when X-Idempotency-Key is blank")
    void shouldRejectGenerateWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy returns 400 when X-Tenant-ID is missing")
    void shouldRejectGenerateWithoutTenant() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy returns 403 on SecurityException")
    void shouldReturn403OnSecurityExceptionGenerate() {
        strategyService.throwOnGenerate = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /strategy returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnIllegalArgumentGenerate() {
        strategyService.throwOnGenerate = new IllegalArgumentException("invalid field");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy returns 500 on unknown RuntimeException")
    void shouldReturn500OnUnknownGenerateException() {
        strategyService.throwOnGenerate = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /strategy returns 500 when payload is malformed JSON")
    void shouldReturn500OnMalformedJsonGenerate() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{not valid json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /strategy/:id/submit returns 200 on success")
    void shouldSubmitForApproval() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /strategy/:id/submit returns 400 when X-Idempotency-Key is missing")
    void shouldRejectSubmitWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy/:id/submit returns 404 on NoSuchElementException")
    void shouldReturn404OnSubmitNotFound() {
        strategyService.throwOnSubmit = new NoSuchElementException("not found");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /strategy/:id/submit returns 409 on IllegalStateException")
    void shouldReturn409OnSubmitWrongState() {
        strategyService.throwOnSubmit = new IllegalStateException("wrong state");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /strategy/:id/submit returns 500 on unknown error")
    void shouldReturn500OnSubmitUnknownError() {
        strategyService.throwOnSubmit = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 200 on success")
    void shouldApproveStrategy() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 400 when X-Idempotency-Key missing")
    void shouldRejectApproveWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 403 on SecurityException")
    void shouldReturn403OnApproveUnauthorized() {
        strategyService.throwOnApprove = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 404 on NoSuchElementException")
    void shouldReturn404OnApproveNotFound() {
        strategyService.throwOnApprove = new NoSuchElementException("not found");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 409 on IllegalStateException")
    void shouldReturn409OnApproveWrongState() {
        strategyService.throwOnApprove = new IllegalStateException("not pending");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /strategy/:id/approve returns 500 on unknown error")
    void shouldReturn500OnApproveUnknownError() {
        strategyService.throwOnApprove = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/strategy/strat-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET /strategy returns latest strategy 200")
    void shouldGetLatestStrategy() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /strategy returns 400 when X-Tenant-ID missing")
    void shouldRejectGetWithoutTenant() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /strategy returns 404 on NoSuchElementException")
    void shouldReturn404OnGetNotFound() {
        strategyService.throwOnGet = new NoSuchElementException("not found");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /strategy returns 403 on SecurityException")
    void shouldReturn403OnGetUnauthorized() {
        strategyService.throwOnGet = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /strategy returns 500 on unknown error")
    void shouldReturn500OnGetUnknownError() {
        strategyService.throwOnGet = new RuntimeException("boom");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET /strategy accepts X-Roles and X-Permissions CSV headers")
    void shouldAcceptRolesAndPermissionsHeaders() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/strategy")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "read,write")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ---- test double ----

    private static final class FakeStrategyService implements StrategyGeneratorService {
        DmOperationContext lastContext;
        Exception throwOnGenerate;
        Exception throwOnGet;
        Exception throwOnSubmit;
        Exception throwOnApprove;

        private static MarketingStrategy buildStrategy(DmWorkspaceId wsId) {
            return MarketingStrategy.builder()
                .strategyId("strat-1")
                .workspaceId(wsId)
                .status(StrategyStatus.DRAFT)
                .goals(List.of(new StrategyGoal("lead-generation", "Generate leads", "10 leads", "CRM count")))
                .channelPlans(List.of(new CampaignPlan(
                    StrategyChannel.GOOGLE_SEARCH, "Drive traffic", 1400,
                    List.of("Best service"), List.of("hvac near me"))))
                .budgetCap(2000)
                .rationale("Test rationale")
                .assumptions("Test assumptions")
                .measurementPlan("Weekly review")
                .contentPlan("Week 1: ads")
                .modelVersion("v1.0")
                .generatedAt(Instant.now())
                .generatedBy("owner-1")
                .approvedAt(null)
                .approvedBy(null)
                .build();
        }

        @Override
        public Promise<MarketingStrategy> generateStrategy(
                DmOperationContext ctx,
                StrategyGeneratorService.GenerateStrategyCommand command) {
            this.lastContext = ctx;
            if (throwOnGenerate != null) {
                return Promise.ofException(throwOnGenerate);
            }
            return Promise.of(buildStrategy(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<MarketingStrategy> getLatestStrategy(DmOperationContext ctx) {
            if (throwOnGet != null) {
                return Promise.ofException(throwOnGet);
            }
            return Promise.of(buildStrategy(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<MarketingStrategy> submitForApproval(DmOperationContext ctx, String strategyId) {
            if (throwOnSubmit != null) {
                return Promise.ofException(throwOnSubmit);
            }
            MarketingStrategy strategy = buildStrategy(ctx.getWorkspaceId());
            return Promise.of(strategy.submitForApproval());
        }

        @Override
        public Promise<MarketingStrategy> approveStrategy(DmOperationContext ctx, String strategyId) {
            if (throwOnApprove != null) {
                return Promise.ofException(throwOnApprove);
            }
            MarketingStrategy strategy = buildStrategy(ctx.getWorkspaceId());
            MarketingStrategy pending = strategy.submitForApproval();
            return Promise.of(pending.approve("owner-1", Instant.now()));
        }
    }
}
