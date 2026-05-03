package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.adcopy.AdCopyGeneratorService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import com.ghatana.digitalmarketing.domain.content.GeneratorMetadata;
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

@DisplayName("DmosAdCopyServlet")
class DmosAdCopyServletTest extends EventloopTestBase {

    private FakeAdCopyGeneratorService generatorService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        generatorService = new FakeAdCopyGeneratorService();
        servlet = new DmosAdCopyServlet(generatorService, Eventloop.create()).getServlet();
    }

    private static final String GENERATE_BODY =
        "{\"strategyId\":\"strat-1\","
        + "\"brandDisplayName\":\"Acme Corp\","
        + "\"primaryOffer\":\"Plumbing Services\","
        + "\"serviceArea\":\"Denver, CO\","
        + "\"landingPageUrl\":\"https://acme.example.com\","
        + "\"voiceTone\":\"professional\","
        + "\"keywordThemes\":[\"emergency plumber\"],"
        + "\"negativeKeywords\":[\"free\"],"
        + "\"claimIds\":[\"claim-1\"]}";

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects null generatorService")
    void shouldRejectNullGeneratorService() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosAdCopyServlet(null, Eventloop.create()));
    }

    @Test
    @DisplayName("constructor rejects null eventloop")
    void shouldRejectNullEventloop() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosAdCopyServlet(generatorService, null));
    }

    // -------------------------------------------------------------------------
    // POST /v1/workspaces/:wsId/content-items/:itemId/ad-copy/generate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST generate returns 201 with content version")
    void shouldGenerateAdCopyDraftAndReturn201() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(generatorService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST generate returns 400 when X-Idempotency-Key missing")
    void shouldReturn400WhenIdempotencyKeyMissing() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST generate returns 400 when X-Idempotency-Key is blank")
    void shouldReturn400WhenIdempotencyKeyBlank() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST generate returns 400 when X-Tenant-ID missing")
    void shouldReturn400WhenTenantIdMissing() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST generate returns 403 when service throws SecurityException")
    void shouldReturn403OnSecurityException() {
        generatorService.throwOnGenerate = new SecurityException("Not authorised");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST generate returns 404 when service throws NoSuchElementException")
    void shouldReturn404OnNotFound() {
        generatorService.throwOnGenerate = new NoSuchElementException("Item not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST generate returns 400 when service throws IllegalArgumentException")
    void shouldReturn400OnIllegalArgument() {
        generatorService.throwOnGenerate = new IllegalArgumentException("Bad field");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST generate returns 409 when service throws IllegalStateException")
    void shouldReturn409OnConflict() {
        generatorService.throwOnGenerate = new IllegalStateException("Duplicate");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST generate returns 500 on unexpected service exception")
    void shouldReturn500OnUnexpectedException() {
        generatorService.throwOnGenerate = new RuntimeException("Unexpected");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST generate with optional headers succeeds with 201")
    void shouldHandleOptionalHeaders() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "sess-1")
            .withHeader(HttpHeaders.of("X-Roles"), "ADMIN,USER")
            .withHeader(HttpHeaders.of("X-Permissions"), "content:write")
            .withBody(GENERATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST generate with minimal body (no optional fields) succeeds")
    void shouldHandleMinimalBody() {
        String minimalBody =
            "{\"strategyId\":\"strat-1\","
            + "\"brandDisplayName\":\"Acme\","
            + "\"primaryOffer\":\"Services\","
            + "\"serviceArea\":\"Denver\","
            + "\"landingPageUrl\":\"https://acme.example.com\"}";

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/generate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(minimalBody.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    // -------------------------------------------------------------------------
    // GET /v1/workspaces/:wsId/content-items/:itemId/ad-copy/latest-approved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET latest-approved returns 200 with content version")
    void shouldReturnLatestApproved200() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET latest-approved returns 400 when X-Tenant-ID missing")
    void shouldReturn400OnGetWhenTenantMissing() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/latest-approved")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET latest-approved returns 403 when service throws SecurityException")
    void shouldReturn403OnGetSecurityException() {
        generatorService.throwOnGetApproved = new SecurityException("Forbidden");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET latest-approved returns 404 when service throws NoSuchElementException")
    void shouldReturn404OnGetNotFound() {
        generatorService.throwOnGetApproved = new NoSuchElementException("Not found");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET latest-approved returns 500 on unexpected service exception")
    void shouldReturn500OnGetUnexpected() {
        generatorService.throwOnGetApproved = new RuntimeException("Unexpected");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-ad-1/ad-copy/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // -------------------------------------------------------------------------
    // Test double
    // -------------------------------------------------------------------------

    private static final class FakeAdCopyGeneratorService implements AdCopyGeneratorService {
        DmOperationContext lastContext;
        Exception throwOnGenerate;
        Exception throwOnGetApproved;

        private static ContentVersion buildVersion(DmWorkspaceId wsId) {
            return ContentVersion.builder()
                .versionId("ver-1")
                .itemId("item-ad-1")
                .workspaceId(wsId)
                .versionNumber(1)
                .status(ContentVersionStatus.DRAFT)
                .contentBlocks(List.of(
                    new ContentBlock("HEADLINES", "HEADLINES", "Acme Corp | Plumbing | Denver", 0)))
                .claimReferences(List.of(
                    new ClaimReference("clm-1", "Top quality", "source-1")))
                .disclosureReferences(List.of(
                    new DisclosureReference("dis-1", "Ad approval required.", "COMPLIANCE")))
                .generatorMetadata(new GeneratorMetadata(
                    "ad-gen-v1.0", "ad-prompt-v1.0", "DETERMINISTIC", Instant.now()))
                .createdAt(Instant.now())
                .createdBy("user-alice")
                .build();
        }

        @Override
        public Promise<ContentVersion> generateAdCopyDraft(
                DmOperationContext ctx,
                GenerateAdCopyCommand command) {
            this.lastContext = ctx;
            if (throwOnGenerate != null) {
                return Promise.ofException(throwOnGenerate);
            }
            return Promise.of(buildVersion(ctx.getWorkspaceId()));
        }

        @Override
        public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
            if (throwOnGetApproved != null) {
                return Promise.ofException(throwOnGetApproved);
            }
            return Promise.of(buildVersion(ctx.getWorkspaceId()));
        }
    }
}
