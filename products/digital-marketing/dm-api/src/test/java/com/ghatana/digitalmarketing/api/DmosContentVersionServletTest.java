package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.content.ContentItemService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
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

@DisplayName("DmosContentVersionServlet")
class DmosContentVersionServletTest extends EventloopTestBase {

    private FakeContentItemService contentItemService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        contentItemService = new FakeContentItemService();
        servlet = new DmosContentVersionServlet(contentItemService, Eventloop.create()).getServlet();
    }

    private static final String CREATE_ITEM_BODY =
        "{\"title\":\"Test Landing Page\",\"itemType\":\"LANDING_PAGE\",\"description\":\"A page\"}";

    private static final String CREATE_VERSION_BODY =
        "{\"contentBlocks\":[{\"blockId\":\"blk-1\",\"blockType\":\"HERO\","
        + "\"bodyText\":\"Hero text\",\"ordering\":0}],"
        + "\"claimReferences\":[{\"claimId\":\"clm-1\",\"claimText\":\"Top product\","
        + "\"claimSource\":\"RESEARCH\"}],"
        + "\"disclosureReferences\":[{\"disclosureId\":\"dis-1\","
        + "\"disclosureText\":\"Results may vary.\",\"disclosureType\":\"LEGAL\"}],"
        + "\"modelVersion\":\"model-v1\","
        + "\"promptVersion\":\"prompt-v2\","
        + "\"sourceStrategy\":\"TEMPLATE\"}";

    // ---- constructor validation ----

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosContentVersionServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosContentVersionServlet(contentItemService, null));
    }

    // ---- POST /content-items ----

    @Test
    @DisplayName("POST /content-items creates item and returns 201")
    void shouldCreateItem() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(contentItemService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
    }

    @Test
    @DisplayName("POST /content-items returns 400 when X-Idempotency-Key missing")
    void shouldRejectCreateItemWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items returns 400 when X-Tenant-ID missing")
    void shouldRejectCreateItemWithoutTenant() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items returns 400 for invalid itemType")
    void shouldRejectInvalidItemType() {
        String body = "{\"title\":\"Test\",\"itemType\":\"UNKNOWN_TYPE\",\"description\":\"\"}";
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items returns 403 on SecurityException")
    void shouldReturn403OnCreateItemSecurityException() {
        contentItemService.throwOnCreateItem = new SecurityException("not authorized");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /content-items returns 500 on unknown RuntimeException")
    void shouldReturn500OnCreateItemUnknownException() {
        contentItemService.throwOnCreateItem = new RuntimeException("unexpected");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /content-items returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnCreateItemIllegalArgument() {
        contentItemService.throwOnCreateItem = new IllegalArgumentException("invalid");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ---- POST /content-items/:itemId/versions ----

    @Test
    @DisplayName("POST /content-items/:itemId/versions creates version and returns 201")
    void shouldCreateVersion() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(CREATE_VERSION_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST /content-items/:itemId/versions returns 400 when X-Idempotency-Key missing")
    void shouldRejectCreateVersionWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(CREATE_VERSION_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items/:itemId/versions returns 403 on SecurityException")
    void shouldReturn403OnCreateVersionSecurityException() {
        contentItemService.throwOnCreateVersion = new SecurityException("not authorized");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(CREATE_VERSION_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST /content-items/:itemId/versions returns 500 on unknown RuntimeException")
    void shouldReturn500OnCreateVersionUnknownException() {
        contentItemService.throwOnCreateVersion = new RuntimeException("unexpected");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(CREATE_VERSION_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /content-items/:itemId/versions with null contentBlocks in body returns 400")
    void shouldHandleNullListsInCreateVersionBody() {
        String bodyWithNulls =
            "{\"contentBlocks\":null,\"claimReferences\":null,\"disclosureReferences\":null,"
            + "\"modelVersion\":\"model-v1\",\"promptVersion\":\"prompt-v2\","
            + "\"sourceStrategy\":\"TEMPLATE\"}";
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(bodyWithNulls.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        // null contentBlocks → empty list passed to command → IAE from non-empty validation → 400
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items/:itemId/versions returns 400 on IllegalArgumentException from service")
    void shouldReturn400OnCreateVersionIllegalArgument() {
        contentItemService.throwOnCreateVersion = new IllegalArgumentException("invalid block");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(CREATE_VERSION_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ---- GET /content-items/:itemId/versions/latest-approved ----

    @Test
    @DisplayName("GET .../versions/latest-approved returns 200 on success")
    void shouldGetLatestApproved() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET .../versions/latest-approved returns 400 when X-Tenant-ID missing")
    void shouldRejectLatestApprovedWithoutTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/latest-approved")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET .../versions/latest-approved returns 404 on NoSuchElementException")
    void shouldReturn404WhenNoApprovedVersion() {
        contentItemService.throwOnGetLatestApproved = new NoSuchElementException("not found");
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET .../versions/latest-approved returns 403 on SecurityException")
    void shouldReturn403OnGetLatestApprovedSecurityException() {
        contentItemService.throwOnGetLatestApproved = new SecurityException("not authorized");
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET .../versions/latest-approved returns 500 on unknown RuntimeException")
    void shouldReturn500OnGetLatestApprovedUnknownException() {
        contentItemService.throwOnGetLatestApproved = new RuntimeException("unexpected");
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/latest-approved")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- POST /content-items/:itemId/versions/:versionId/approve ----

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 200 on success")
    void shouldApproveVersion() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 400 when X-Idempotency-Key missing")
    void shouldRejectApproveWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 404 on NoSuchElementException")
    void shouldReturn404OnApproveNotFound() {
        contentItemService.throwOnApprove = new NoSuchElementException("not found");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 409 on IllegalStateException")
    void shouldReturn409OnApproveIllegalState() {
        contentItemService.throwOnApprove = new IllegalStateException("already approved");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 403 on SecurityException")
    void shouldReturn403OnApproveSecurityException() {
        contentItemService.throwOnApprove = new SecurityException("not authorized");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST .../versions/:versionId/approve returns 500 on unknown RuntimeException")
    void shouldReturn500OnApproveUnknownException() {
        contentItemService.throwOnApprove = new RuntimeException("unexpected");
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions/ver-1/approve")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .withBody(new byte[0])
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    // ---- GET /content-items/:itemId/versions ----

    @Test
    @DisplayName("GET /content-items/:itemId/versions returns 200 on success")
    void shouldGetVersionHistory() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /content-items/:itemId/versions returns 400 when X-Tenant-ID missing")
    void shouldRejectGetVersionHistoryWithoutTenant() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /content-items/:itemId/versions returns 403 on SecurityException")
    void shouldReturn403OnGetVersionHistorySecurityException() {
        contentItemService.throwOnGetHistory = new SecurityException("not authorized");
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET /content-items/:itemId/versions returns 500 on unknown RuntimeException")
    void shouldReturn500OnGetVersionHistoryUnknownException() {
        contentItemService.throwOnGetHistory = new RuntimeException("unexpected");
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-items/item-1/versions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST /content-items returns 400 when X-Idempotency-Key is blank")
    void shouldRejectCreateItemWithBlankIdempotencyKey() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "   ")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST /content-items passes optional correlation and session headers")
    void shouldPassOptionalHeadersOnCreateItem() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-items")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-abc")
            .withHeader(HttpHeaders.of("X-Session-ID"), "sess-xyz")
            .withHeader(HttpHeaders.of("X-Roles"), "editor, , reviewer")
            .withHeader(HttpHeaders.of("X-Permissions"), "content:write")
            .withBody(CREATE_ITEM_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    // ---- fake service ----

    private static final class FakeContentItemService implements ContentItemService {

        DmOperationContext lastContext;
        RuntimeException throwOnCreateItem;
        RuntimeException throwOnCreateVersion;
        RuntimeException throwOnGetLatestApproved;
        RuntimeException throwOnApprove;
        RuntimeException throwOnGetHistory;

        private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-1");
        private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

        private ContentItem stubItem() {
            return ContentItem.builder()
                .itemId("item-1")
                .workspaceId(WS)
                .title("Landing Page")
                .itemType(ContentItemType.LANDING_PAGE)
                .description("A page")
                .createdAt(NOW)
                .createdBy("user-alice")
                .build();
        }

        private ContentVersion stubVersion() {
            return ContentVersion.builder()
                .versionId("ver-1")
                .itemId("item-1")
                .workspaceId(WS)
                .versionNumber(1)
                .contentBlocks(List.of(new ContentBlock("blk-1", "HERO", "Text", 0)))
                .claimReferences(List.of(new ClaimReference("clm-1", "Top", "RESEARCH")))
                .disclosureReferences(List.of(new DisclosureReference("dis-1", "Disclaimer", "LEGAL")))
                .generatorMetadata(new GeneratorMetadata("model-v1", "prompt-v2", "TEMPLATE", NOW))
                .status(ContentVersionStatus.DRAFT)
                .createdAt(NOW)
                .createdBy("user-alice")
                .build();
        }

        private ContentVersion stubApprovedVersion() {
            return stubVersion().submitForReview().approve("user-alice", NOW);
        }

        @Override
        public Promise<ContentItem> createItem(DmOperationContext ctx, CreateContentItemCommand command) {
            this.lastContext = ctx;
            if (throwOnCreateItem != null) {
                return Promise.ofException((Exception) throwOnCreateItem);
            }
            return Promise.of(stubItem());
        }

        @Override
        public Promise<ContentVersion> createVersion(DmOperationContext ctx, CreateContentVersionCommand command) {
            this.lastContext = ctx;
            if (throwOnCreateVersion != null) {
                return Promise.ofException((Exception) throwOnCreateVersion);
            }
            return Promise.of(stubVersion());
        }

        @Override
        public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
            this.lastContext = ctx;
            if (throwOnGetLatestApproved != null) {
                return Promise.ofException((Exception) throwOnGetLatestApproved);
            }
            return Promise.of(stubApprovedVersion());
        }

        @Override
        public Promise<ContentVersion> approveVersion(DmOperationContext ctx, String versionId) {
            this.lastContext = ctx;
            if (throwOnApprove != null) {
                return Promise.ofException((Exception) throwOnApprove);
            }
            return Promise.of(stubApprovedVersion());
        }

        @Override
        public Promise<List<ContentVersion>> getVersionHistory(DmOperationContext ctx, String itemId) {
            this.lastContext = ctx;
            if (throwOnGetHistory != null) {
                return Promise.ofException((Exception) throwOnGetHistory);
            }
            return Promise.of(List.of(stubVersion()));
        }
    }
}
