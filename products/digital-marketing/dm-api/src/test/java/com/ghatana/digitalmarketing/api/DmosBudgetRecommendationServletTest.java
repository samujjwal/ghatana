package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.budget.BudgetChannelAllocation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendationStatus;
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

@DisplayName("DmosBudgetRecommendationServlet")
class DmosBudgetRecommendationServletTest extends EventloopTestBase {

    private FakeBudgetRecommendationService budgetService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        budgetService = new FakeBudgetRecommendationService();
        servlet = new DmosBudgetRecommendationServlet(budgetService, Eventloop.create()).getServlet();
    }

    private static final String GENERATE_BODY =
        "{\"strategyId\":\"strat-1\",\"totalMonthlyCap\":3000.0,\"changeThreshold\":10.0}";

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosBudgetRecommendationServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosBudgetRecommendationServlet(budgetService, null));
    }

    @Test
    @DisplayName("POST /budget-recommendation generates recommendation and returns 201")
    void shouldRecommendBudget() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(budgetService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 400 when X-Idempotency-Key is missing")
    void shouldRejectGenerateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 400 when X-Idempotency-Key is blank")
    void shouldRejectGenerateWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 400 when X-Tenant-ID is missing")
    void shouldRejectGenerateWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 403 on SecurityException")
    void shouldReturn403OnSecurityException() {
        budgetService.throwOnRecommend = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 400 on IllegalArgumentException")
    void shouldReturn400OnIllegalArgument() {
        budgetService.throwOnRecommend = new IllegalArgumentException("invalid cap");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /budget-recommendation returns 500 on unknown RuntimeException")
    void shouldReturn500OnUnknownException() {
        budgetService.throwOnRecommend = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/submit returns 200 on success")
    void shouldSubmitForApproval() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/submit returns 400 when idempotency key missing")
    void shouldRejectSubmitWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/submit returns 404 on NoSuchElement")
    void shouldReturn404OnSubmitNotFound() {
        budgetService.throwOnSubmit = new NoSuchElementException("rec not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/submit returns 409 on IllegalStateException")
    void shouldReturn409OnSubmitIllegalState() {
        budgetService.throwOnSubmit = new IllegalStateException("already submitted");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/approve returns 200 on success")
    void shouldApproveRecommendation() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/approve returns 409 on IllegalStateException")
    void shouldReturn409OnApproveIllegalState() {
        budgetService.throwOnApprove = new IllegalStateException("not in pending state");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /budget-recommendation/:id/approve returns 403 on SecurityException")
    void shouldReturn403OnApproveSecurityException() {
        budgetService.throwOnApprove = new SecurityException("not authorized to approve");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /budget-recommendation returns 200 on success")
    void shouldGetLatestRecommendation() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /budget-recommendation returns 404 on NoSuchElement")
    void shouldReturn404OnGetNotFound() {
        budgetService.throwOnGetLatest = new NoSuchElementException("no recommendation found");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /budget-recommendation returns 403 on SecurityException")
    void shouldReturn403OnGetSecurityException() {
        budgetService.throwOnGetLatest = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /budget-recommendation returns 400 when X-Tenant-ID is missing")
    void shouldReturn400WhenTenantMissingOnGet() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/budget-recommendation")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ---- fake service ----

        @Test
        @DisplayName("POST /budget-recommendation/:id/submit returns 500 on unknown RuntimeException")
        void shouldReturn500OnSubmitUnknownException() {
            budgetService.throwOnSubmit = new RuntimeException("unexpected");

            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/submit")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
                .withBody(new byte[0])
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(500);
        }

        @Test
        @DisplayName("POST /budget-recommendation/:id/approve returns 404 on NoSuchElement")
        void shouldReturn404OnApproveNotFound() {
            budgetService.throwOnApprove = new NoSuchElementException("rec not found");

            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
                .withBody(new byte[0])
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("POST /budget-recommendation/:id/approve returns 500 on unknown RuntimeException")
        void shouldReturn500OnApproveUnknownException() {
            budgetService.throwOnApprove = new RuntimeException("unexpected");

            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/budget-recommendation/rec-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
                .withBody(new byte[0])
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(500);
        }

        @Test
        @DisplayName("POST /budget-recommendation passes X-Roles and X-Permissions headers")
        void shouldPassRolesAndPermissions() {
            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/budget-recommendation")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
                .withHeader(HttpHeaders.of("X-Roles"), "admin,editor")
                .withHeader(HttpHeaders.of("X-Permissions"), "budget:write")
                .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

    private static final class FakeBudgetRecommendationService implements BudgetRecommendationService {

        DmOperationContext lastContext;
        RuntimeException throwOnRecommend;
        RuntimeException throwOnSubmit;
        RuntimeException throwOnApprove;
        RuntimeException throwOnGetLatest;

        private BudgetRecommendation stubRec() {
            return BudgetRecommendation.builder()
                .recommendationId("rec-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .strategyId("strat-1")
                .totalMonthlyCap(3000.0)
                .changeThresholdPct(10.0)
                .channelAllocations(List.of(
                    new BudgetChannelAllocation("GOOGLE_SEARCH", 2100.0, 70.0, "primary"),
                    new BudgetChannelAllocation("LANDING_PAGE", 600.0, 20.0, "conversion"),
                    new BudgetChannelAllocation("EMAIL_FOLLOW_UP", 300.0, 10.0, "follow-up")))
                .rationale("stub rationale")
                .assumptions("stub assumptions")
                .modelVersion("v1.0")
                .status(BudgetRecommendationStatus.DRAFT)
                .generatedAt(Instant.now())
                .generatedBy("system")
                .build();
        }

        @Override
        public Promise<BudgetRecommendation> recommendBudget(
                DmOperationContext ctx, GenerateBudgetCommand command) {
            this.lastContext = ctx;
            if (throwOnRecommend != null) {
                return Promise.ofException((Exception) throwOnRecommend);
            }
            return Promise.of(stubRec());
        }

        @Override
        public Promise<BudgetRecommendation> getLatestRecommendation(DmOperationContext ctx) {
            this.lastContext = ctx;
            if (throwOnGetLatest != null) {
                return Promise.ofException((Exception) throwOnGetLatest);
            }
            return Promise.of(stubRec());
        }

        @Override
        public Promise<BudgetRecommendation> submitForApproval(DmOperationContext ctx, String recId) {
            this.lastContext = ctx;
            if (throwOnSubmit != null) {
                return Promise.ofException((Exception) throwOnSubmit);
            }
            return Promise.of(stubRec().submitForApproval());
        }

        @Override
        public Promise<BudgetRecommendation> approveRecommendation(DmOperationContext ctx, String recId) {
            this.lastContext = ctx;
            if (throwOnApprove != null) {
                return Promise.ofException((Exception) throwOnApprove);
            }
            return Promise.of(stubRec().submitForApproval().approve("owner-1", Instant.now()));
        }
    }
}
