package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.scoring.LeadScoringService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.scoring.LeadGrade;
import com.ghatana.digitalmarketing.domain.scoring.LeadScore;
import com.ghatana.digitalmarketing.domain.scoring.ScoreDimension;
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

@DisplayName("DmosLeadScoringServlet")
class DmosLeadScoringServletTest extends EventloopTestBase {

    private FakeLeadScoringService scoringService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        scoringService = new FakeLeadScoringService();
        servlet = new DmosLeadScoringServlet(scoringService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosLeadScoringServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosLeadScoringServlet(scoringService, null));
    }

    @Test
    @DisplayName("POST generates score and returns 200")
    void shouldGenerateScore() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"intakeCompletionPct\":80,\"auditFindingCount\":3,\"trackingGapsDetected\":true," +
                "\"keywordOpportunityCount\":4,\"serviceArea\":\"New York\",\"monthlyBudgetHint\":2000}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(scoringService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(scoringService.lastCommand.serviceArea()).isEqualTo("New York");
    }

    @Test
    @DisplayName("POST returns 400 when X-Idempotency-Key is missing")
    void shouldRejectGenerateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 400 when X-Tenant-ID header is absent")
    void shouldRejectGenerateWithoutTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 403 when service throws SecurityException")
    void shouldMapSecurityExceptionTo403OnGenerate() {
        scoringService.throwOnGenerate = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST returns 500 on unexpected service error")
    void shouldMapUnknownGenerateExceptionTo500() {
        scoringService.throwOnGenerate = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET returns latest lead score")
    void shouldGetLatestScore() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET returns 404 when no lead score exists")
    void shouldReturn404WhenNoScoreExists() {
        scoringService.throwOnGet = new NoSuchElementException("not found");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET returns 403 when service throws SecurityException")
    void shouldMapGetSecurityExceptionTo403() {
        scoringService.throwOnGet = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET returns 500 on unexpected service error")
    void shouldMapUnknownGetExceptionTo500() {
        scoringService.throwOnGet = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET returns 400 when X-Tenant-ID header is absent")
    void shouldRejectGetWithoutTenantHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 500 when payload is malformed JSON")
    void shouldReturn500WhenBodyIsMalformedJson() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{not valid json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST returns 400 when X-Idempotency-Key is blank whitespace")
    void shouldRejectGenerateWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 400 when service throws IllegalArgumentException")
    void shouldMapIllegalArgumentExceptionTo400OnGenerate() {
        scoringService.throwOnGenerate = new IllegalArgumentException("invalid payload");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"intakeCompletionPct\":50,\"auditFindingCount\":0,\"trackingGapsDetected\":false," +
                "\"keywordOpportunityCount\":0,\"serviceArea\":\"NY\",\"monthlyBudgetHint\":0}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET accepts X-Roles and X-Permissions CSV headers")
    void shouldAcceptRolesAndPermissionsHeaders() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/lead-score")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "read,write")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // ---- test double ----

    private static final class FakeLeadScoringService implements LeadScoringService {
        DmOperationContext lastContext;
        LeadScoringService.GenerateLeadScoreCommand lastCommand;
        Exception throwOnGenerate;
        Exception throwOnGet;

        private static LeadScore buildScore(DmWorkspaceId wsId) {
            return LeadScore.builder()
                .scoreId("score-1")
                .workspaceId(wsId)
                .score(72)
                .grade(LeadGrade.B)
                .dimensions(List.of(new ScoreDimension("fit", 30, "Good match")))
                .confidence(0.85)
                .requiresHumanReview(false)
                .recommendedNextAction("Send proposal")
                .modelVersion("v1.0")
                .scoredAt(Instant.now())
                .scoredBy("owner-1")
                .build();
        }

        @Override
        public Promise<LeadScore> generateScore(
                DmOperationContext ctx,
                LeadScoringService.GenerateLeadScoreCommand command) {
            this.lastContext = ctx;
            this.lastCommand = command;
            if (throwOnGenerate != null) {
                return Promise.ofException(throwOnGenerate);
            }
            return Promise.of(buildScore(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<LeadScore> getLatestScore(DmOperationContext ctx) {
            if (throwOnGet != null) {
                return Promise.ofException(throwOnGet);
            }
            return Promise.of(buildScore(ctx.getWorkspaceId()));
        }
    }
}
