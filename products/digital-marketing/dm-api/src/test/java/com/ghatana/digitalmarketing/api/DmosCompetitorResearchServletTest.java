package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.research.CompetitorResearchService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.research.CompetitorFinding;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import com.ghatana.digitalmarketing.domain.research.KeywordFinding;
import com.ghatana.digitalmarketing.domain.research.KeywordIntent;
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

@DisplayName("DmosCompetitorResearchServlet")
class DmosCompetitorResearchServletTest extends EventloopTestBase {

    private FakeResearchService researchService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        researchService = new FakeResearchService();
        servlet = new DmosCompetitorResearchServlet(researchService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCompetitorResearchServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCompetitorResearchServlet(researchService, null));
    }

    @Test
    @DisplayName("POST runs research and returns 200")
    void shouldRunResearch() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"competitorDomains\":[\"rival.com\"],\"serviceArea\":\"New York\",\"primaryOffer\":\"plumbing\"}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(researchService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(researchService.lastCommand.serviceArea()).isEqualTo("New York");
    }

    @Test
    @DisplayName("POST returns 400 when X-Idempotency-Key is missing")
    void shouldRejectRunWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(("{\"competitorDomains\":[],\"serviceArea\":\"NY\",\"primaryOffer\":\"plumbing\"}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 400 when X-Tenant-ID header is absent")
    void shouldRejectRunWithoutTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"competitorDomains\":[],\"serviceArea\":\"NY\",\"primaryOffer\":\"plumbing\"}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST returns 403 when service throws SecurityException")
    void shouldMapSecurityExceptionTo403() {
        researchService.throwOnRun = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"competitorDomains\":[],\"serviceArea\":\"NY\",\"primaryOffer\":\"plumbing\"}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET returns latest research snapshot")
    void shouldGetLatestResearch() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET returns 404 when no research snapshot exists")
    void shouldReturn404WhenNoSnapshotExists() {
        researchService.throwOnGet = new NoSuchElementException("not found");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET returns 403 when service throws SecurityException")
    void shouldMapGetSecurityExceptionTo403() {
        researchService.throwOnGet = new SecurityException("not authorized");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST returns 500 on unexpected service error")
    void shouldMapUnknownRunExceptionTo500() {
        researchService.throwOnRun = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{\"competitorDomains\":[],\"serviceArea\":\"NY\",\"primaryOffer\":\"plumbing\"}")
                .getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET returns 500 on unexpected service error")
    void shouldMapUnknownGetExceptionTo500() {
        researchService.throwOnGet = new RuntimeException("unexpected");

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/research/competitor")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- test double ----

    private static final class FakeResearchService implements CompetitorResearchService {
        DmOperationContext lastContext;
        RunCompetitorResearchCommand lastCommand;
        Exception throwOnRun;
        Exception throwOnGet;

        private static CompetitorResearchSnapshot buildSnapshot(DmWorkspaceId wsId) {
            return CompetitorResearchSnapshot.builder()
                .snapshotId("snap-1")
                .workspaceId(wsId)
                .competitorFindings(List.of(
                    new CompetitorFinding("rival.com", "Active competitor", "Targets local market", true, "user-provided")
                ))
                .keywordFindings(List.of(
                    new KeywordFinding("plumber near me", KeywordIntent.TRANSACTIONAL, 0.9,
                        "Search campaign", "High volume", "inferred-mvp")
                ))
                .opportunitySummary("1 competitor analysed")
                .generatedAt(Instant.now())
                .generatedBy("owner-1")
                .build();
        }

        @Override
        public Promise<CompetitorResearchSnapshot> runResearch(
                DmOperationContext ctx,
                RunCompetitorResearchCommand command) {
            this.lastContext = ctx;
            this.lastCommand = command;
            if (throwOnRun != null) {
                return Promise.ofException(throwOnRun);
            }
            return Promise.of(buildSnapshot(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<CompetitorResearchSnapshot> getLatestResearch(DmOperationContext ctx) {
            if (throwOnGet != null) {
                return Promise.ofException(throwOnGet);
            }
            return Promise.of(buildSnapshot(ctx.getWorkspaceId()));
        }
    }
}
