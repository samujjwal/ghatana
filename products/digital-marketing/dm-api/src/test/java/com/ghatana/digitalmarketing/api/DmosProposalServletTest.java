package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.proposal.ProposalService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.proposal.PricingOption;
import com.ghatana.digitalmarketing.domain.proposal.Proposal;
import com.ghatana.digitalmarketing.domain.proposal.ProposalDeliverable;
import com.ghatana.digitalmarketing.domain.proposal.ProposalStatus;
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

@DisplayName("DmosProposalServlet")
class DmosProposalServletTest extends EventloopTestBase {

    private FakeProposalService proposalService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        proposalService = new FakeProposalService();
        servlet = new DmosProposalServlet(proposalService, Eventloop.create()).getServlet();
    }

    private static final String GENERATE_BODY =
        "{\"strategyId\":\"strat-1\",\"templateId\":\"tmpl-1\","
        + "\"templateVersion\":\"v1.0\",\"assumptions\":\"Standard 30-day period\"}";

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosProposalServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosProposalServlet(proposalService, null));
    }

    // ---- POST /proposal ----

    @Test
    @DisplayName("POST /proposal generates proposal and returns 201")
    void shouldGenerateProposal() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(proposalService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /proposal returns 400 when X-Idempotency-Key is missing")
    void shouldRejectGenerateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /proposal returns 400 when X-Idempotency-Key is blank")
    void shouldRejectGenerateWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /proposal returns 400 when X-Tenant-ID is missing")
    void shouldRejectGenerateWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /proposal returns 403 on SecurityException")
    void shouldReturn403OnGenerateSecurityException() {
        proposalService.throwOnGenerate = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /proposal returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnGenerateIllegalArgument() {
        proposalService.throwOnGenerate = new IllegalArgumentException("invalid strategyId");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /proposal returns 500 on unknown RuntimeException")
    void shouldReturn500OnGenerateUnknownException() {
        proposalService.throwOnGenerate = new RuntimeException("boom");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /proposal passes X-Roles and X-Permissions headers")
    void shouldPassRolesAndPermissions() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "proposal:write")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    // ---- POST /proposal/:id/submit ----

    @Test
    @DisplayName("POST /proposal/:id/submit returns 200 on success")
    void shouldSubmitForReview() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /proposal/:id/submit returns 400 when X-Idempotency-Key missing")
    void shouldRejectSubmitWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /proposal/:id/submit returns 404 on NoSuchElement")
    void shouldReturn404OnSubmitNotFound() {
        proposalService.throwOnSubmit = new NoSuchElementException("proposal not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /proposal/:id/submit returns 409 on IllegalStateException")
    void shouldReturn409OnSubmitIllegalState() {
        proposalService.throwOnSubmit = new IllegalStateException("already submitted");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /proposal/:id/submit returns 403 on SecurityException")
    void shouldReturn403OnSubmitSecurityException() {
        proposalService.throwOnSubmit = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /proposal/:id/submit returns 500 on unknown RuntimeException")
    void shouldReturn500OnSubmitUnknownException() {
        proposalService.throwOnSubmit = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- POST /proposal/:id/approve ----

    @Test
    @DisplayName("POST /proposal/:id/approve returns 200 on success")
    void shouldApproveProposal() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST /proposal/:id/approve returns 409 on IllegalStateException")
    void shouldReturn409OnApproveIllegalState() {
        proposalService.throwOnApprove = new IllegalStateException("not in pending review");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST /proposal/:id/approve returns 403 on SecurityException")
    void shouldReturn403OnApproveSecurityException() {
        proposalService.throwOnApprove = new SecurityException("not authorized to approve");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /proposal/:id/approve returns 404 on NoSuchElement")
    void shouldReturn404OnApproveNotFound() {
        proposalService.throwOnApprove = new NoSuchElementException("proposal not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST /proposal/:id/approve returns 500 on unknown RuntimeException")
    void shouldReturn500OnApproveUnknownException() {
        proposalService.throwOnApprove = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

        @Test
        @DisplayName("POST /proposal/:id/approve returns 400 when X-Idempotency-Key is missing")
        void shouldRejectApproveWithoutIdempotencyKey() {
            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withBody(new byte[0])
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("POST /proposal/:id/approve returns 400 when X-Idempotency-Key is blank")
        void shouldRejectApproveWithBlankIdempotencyKey() {
            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/proposal/prop-1/approve")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
                .withBody(new byte[0])
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("POST /proposal passes X-Correlation-ID and X-Session-ID headers")
        void shouldPassCorrelationAndSessionHeaders() {
            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/proposal")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
                .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
                .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("POST /proposal passes X-Roles with blank tokens (filter branch)")
        void shouldHandleRolesWithBlankTokens() {
            HttpRequest request = HttpRequest.post(
                    "http://localhost/v1/workspaces/ws-1/proposal")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
                .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
                .withHeader(HttpHeaders.of("X-Roles"), "admin,,editor")
                .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

    // ---- GET /proposal/:id ----

    @Test
    @DisplayName("GET /proposal/:id returns 200 when proposal found and ID matches")
    void shouldGetProposalById() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /proposal/:id returns 404 when proposalId does not match")
    void shouldReturn404WhenProposalIdMismatch() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/nonexistent-id")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /proposal/:id returns 404 on NoSuchElement from service")
    void shouldReturn404OnGetNoSuchElement() {
        proposalService.throwOnGet = new NoSuchElementException("no proposal found");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /proposal/:id returns 403 on SecurityException")
    void shouldReturn403OnGetSecurityException() {
        proposalService.throwOnGet = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /proposal/:id returns 400 when X-Tenant-ID is missing")
    void shouldReturn400WhenTenantMissingOnGet() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /proposal/:id returns 500 on unknown RuntimeException")
    void shouldReturn500OnGetUnknownException() {
        proposalService.throwOnGet = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/proposal/prop-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- fake service ----

    private static final class FakeProposalService implements ProposalService {

        DmOperationContext lastContext;
        RuntimeException throwOnGenerate;
        RuntimeException throwOnSubmit;
        RuntimeException throwOnApprove;
        RuntimeException throwOnGet;

        private Proposal stubProposal() {
            return Proposal.builder()
                .proposalId("prop-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .strategyId("strat-1")
                .templateId("tmpl-1")
                .templateVersion("v1.0")
                .deliverables(List.of(
                    new ProposalDeliverable("GOOGLE_SEARCH_CAMPAIGN", "Search campaign", 14, "campaign", 1)))
                .pricingOptions(List.of(
                    new PricingOption("MONTHLY_RETAINER", 2500.00, "USD", "Full management retainer")))
                .assumptions("Standard 30-day period")
                .timeline("30-day onboarding")
                .rationale("Generated from strategy strat-1")
                .disclaimer("Results not guaranteed")
                .exclusions("Creative production excluded")
                .measurementPlan("Monthly KPI reviews")
                .modelVersion("v1.0")
                .status(ProposalStatus.DRAFT)
                .generatedAt(Instant.now())
                .generatedBy("system")
                .build();
        }

        @Override
        public Promise<Proposal> generateProposal(DmOperationContext ctx, GenerateProposalCommand command) {
            this.lastContext = ctx;
            if (throwOnGenerate != null) {
                return Promise.ofException((Exception) throwOnGenerate);
            }
            return Promise.of(stubProposal());
        }

        @Override
        public Promise<Proposal> getProposal(DmOperationContext ctx) {
            this.lastContext = ctx;
            if (throwOnGet != null) {
                return Promise.ofException((Exception) throwOnGet);
            }
            return Promise.of(stubProposal());
        }

        @Override
        public Promise<Proposal> submitForReview(DmOperationContext ctx, String proposalId) {
            this.lastContext = ctx;
            if (throwOnSubmit != null) {
                return Promise.ofException((Exception) throwOnSubmit);
            }
            return Promise.of(stubProposal().submitForReview());
        }

        @Override
        public Promise<Proposal> approveProposal(DmOperationContext ctx, String proposalId) {
            this.lastContext = ctx;
            if (throwOnApprove != null) {
                return Promise.ofException((Exception) throwOnApprove);
            }
            return Promise.of(stubProposal().submitForReview().approve("owner-1", Instant.now()));
        }
    }
}
