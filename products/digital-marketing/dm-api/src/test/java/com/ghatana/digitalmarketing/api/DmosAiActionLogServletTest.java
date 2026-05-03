package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.transparency.AiActionLogService;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosAiActionLogServlet")
class DmosAiActionLogServletTest extends EventloopTestBase {

    private FakeAiActionLogService service;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        service = new FakeAiActionLogService();
        servlet = new DmosAiActionLogServlet(service, Eventloop.create()).routes();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException().isThrownBy(() -> new DmosAiActionLogServlet(null, Eventloop.create()));
        assertThatNullPointerException().isThrownBy(() -> new DmosAiActionLogServlet(service, null));
    }

    @Test
    @DisplayName("POST record returns 201")
    void shouldRecordAction201() {
        String body = """
            {
              "actionType":"DRAFT_GENERATED",
              "status":"PROPOSED",
              "actor":"agent",
              "initiatedByAi":true,
              "confidence":0.77,
              "summary":"Generated draft",
              "details":"Used approved strategy",
              "relatedEntityId":"content-1"
            }
            """;
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST record returns 400 when idempotency key missing")
    void shouldReturn400WhenIdempotencyMissing() {
        String body = """
            {"actionType":"DRAFT_GENERATED","status":"PROPOSED","actor":"agent","initiatedByAi":true,"summary":"x","details":"y"}
            """;
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST record returns 400 when tenant header missing")
    void shouldReturn400WhenRecordTenantMissing() {
        String body = """
            {"actionType":"DRAFT_GENERATED","status":"PROPOSED","actor":"agent","initiatedByAi":true,"summary":"x","details":"y"}
            """;
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST record returns 500 for malformed JSON")
    void shouldReturn500ForMalformedRecordBody() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{not-json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST record maps service argument errors to 400")
    void shouldReturn400ForRecordArgumentError() {
        service.throwOnRecord = new IllegalArgumentException("bad record");
        String body = """
            {
              "actionType":"DRAFT_GENERATED",
              "status":"PROPOSED",
              "actor":"agent",
              "initiatedByAi":true,
              "summary":"Generated draft",
              "details":"Used approved strategy"
            }
            """;

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST record maps unexpected service failures to 500")
    void shouldReturn500ForRecordUnexpectedError() {
        service.throwOnRecord = new RuntimeException("boom");
        String body = """
            {
              "actionType":"DRAFT_GENERATED",
              "status":"PROPOSED",
              "actor":"agent",
              "initiatedByAi":true,
              "summary":"Generated draft",
              "details":"Used approved strategy"
            }
            """;

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET list returns 200")
    void shouldListActions200() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=10")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET list returns 400 for invalid limit")
    void shouldFailInvalidLimit() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=oops")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 400 for non-positive limit")
    void shouldFailNonPositiveLimit() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=0")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 500 on unexpected service error")
    void shouldReturn500ForListUnexpectedError() {
        service.throwOnList = new RuntimeException("boom");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=10")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET by id returns 404 when not found")
    void shouldReturn404ForMissingAction() {
        service.throwOnGet = new NoSuchElementException("missing");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions/act-missing")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET list returns 400 when tenant header missing")
    void shouldReturn400WhenTenantMissing() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions").build();
        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 403 when service denies access")
    void shouldReturn403ForListSecurityError() {
        service.throwOnList = new SecurityException("forbidden");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "marketing-admin")
            .withHeader(HttpHeaders.of("X-Permissions"), "ai-action-log:read")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST record maps service security errors to 403")
    void shouldReturn403ForRecordSecurityError() {
        service.throwOnRecord = new SecurityException("forbidden");
        String body = """
            {
              "actionType":"DRAFT_GENERATED",
              "status":"PROPOSED",
              "actor":"agent",
              "initiatedByAi":true,
              "summary":"Generated draft",
              "details":"Used approved strategy"
            }
            """;

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET by id returns 500 for unexpected service failure")
    void shouldReturn500ForUnknownGetFailure() {
        service.throwOnGet = new RuntimeException("boom");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions/act-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET by id returns 403 for security error")
    void shouldReturn403ForGetSecurityError() {
        service.throwOnGet = new SecurityException("forbidden");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions/act-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET list maps service argument errors to 400")
    void shouldReturn400ForListArgumentError() {
        service.throwOnList = new IllegalArgumentException("bad query");
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=10")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET by id returns 400 when tenant header missing")
    void shouldReturn400ForGetByIdMissingTenant() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions/act-1").build();
        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 200 with blank limit defaults to 50")
    void shouldReturn200WithBlankLimit() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions?limit=")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();
        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST record uses correlation ID header when provided")
    void shouldUseProvidedCorrelationId() {
        String body = """
            {
              "actionType":"DRAFT_GENERATED",
              "status":"PROPOSED",
              "actor":"agent",
              "initiatedByAi":true,
              "summary":"Generated draft",
              "details":"Used approved strategy"
            }
            """;
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/ai-actions")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-explicit")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-42")
            .withHeader(HttpHeaders.of("X-Session-ID"), "sess-99")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("GET by id returns 200 for happy path")
    void shouldReturn200ForGetById() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/ai-actions/act-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Roles"), "analyst")
            .withHeader(HttpHeaders.of("X-Permissions"), "ai-action-log:read")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    private static final class FakeAiActionLogService implements AiActionLogService {
        RuntimeException throwOnRecord;
        RuntimeException throwOnList;
        RuntimeException throwOnGet;

        private static AiActionLogEntry sample(String id) {
            return new AiActionLogEntry(
                id,
                "ws-1",
                "corr-1",
                AiActionType.RECOMMENDATION_GENERATED,
                AiActionStatus.PROPOSED,
                "agent",
                true,
                0.9,
                List.of("https://evidence"),
                List.of("policy:ok"),
                "Generated recommendation",
                "Used approved context",
                "strategy-1",
                Instant.now(),
                0L
            );
        }

        @Override
        public Promise<AiActionLogEntry> recordAction(com.ghatana.digitalmarketing.contracts.DmOperationContext ctx, RecordActionCommand command) {
            if (throwOnRecord != null) {
                return Promise.ofException(throwOnRecord);
            }
            return Promise.of(sample("act-1"));
        }

        @Override
        public Promise<List<AiActionLogEntry>> listActions(com.ghatana.digitalmarketing.contracts.DmOperationContext ctx, ListActionsQuery query) {
            if (throwOnList != null) {
                return Promise.ofException(throwOnList);
            }
            return Promise.of(new ArrayList<>(List.of(sample("act-1"))));
        }

        @Override
        public Promise<AiActionLogEntry> getAction(com.ghatana.digitalmarketing.contracts.DmOperationContext ctx, String actionId) {
            if (throwOnGet != null) {
                return Promise.ofException(throwOnGet);
            }
            return Promise.of(sample(actionId));
        }
    }
}
