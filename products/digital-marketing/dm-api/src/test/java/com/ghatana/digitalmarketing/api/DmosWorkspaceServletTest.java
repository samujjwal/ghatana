package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
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

@DisplayName("DmosWorkspaceServlet")
class DmosWorkspaceServletTest extends EventloopTestBase {

    private FakeWorkspaceService workspaceService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        workspaceService = new FakeWorkspaceService();
        servlet = new DmosWorkspaceServlet(workspaceService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosWorkspaceServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosWorkspaceServlet(workspaceService, null));
    }

    @Test
    @DisplayName("POST create returns 201 and maps context")
    void shouldCreateWorkspace() {
        workspaceService.createResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Main\",\"description\":\"Primary workspace\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(workspaceService.lastContext.getTenantId()).isEqualTo(DmTenantId.of("tenant-1"));
        assertThat(workspaceService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("root"));
        assertThat(workspaceService.lastContext.getActor().getPrincipalId()).isEqualTo("owner-1");
        assertThat(workspaceService.lastContext.getIdempotencyKey().getValue()).isEqualTo("idk-1");
        assertThat(workspaceService.lastCreateCommand.name()).isEqualTo("Main");
    }

    @Test
    @DisplayName("POST create defaults principal to anonymous when header is blank")
    void shouldDefaultPrincipalWhenHeaderBlankOnCreate() {
        workspaceService.createResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "   ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Main\",\"description\":\"Primary workspace\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(workspaceService.lastContext.getActor().getPrincipalId()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("POST create parses role and permission headers with blanks")
    void shouldParseRolesAndPermissionsOnCreate() {
        workspaceService.createResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin, , editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "workspace:write, ,workspace:read")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-roles-1")
            .withBody("{\"name\":\"Main\",\"description\":\"Primary workspace\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST create returns 500 when payload is malformed JSON")
    void shouldReturn500OnCreateMalformedJson() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{bad-json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST create returns 400 when idempotency key is missing")
    void shouldRejectCreateWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{\"name\":\"Main\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 200")
    void shouldListWorkspaces() {
        workspaceService.listResult = Promise.of(List.of(
            workspace("ws-1", WorkspaceStatus.ACTIVE),
            workspace("ws-2", WorkspaceStatus.SUSPENDED)
        ));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET single returns 404 when workspace missing")
    void shouldReturn404WhenWorkspaceMissing() {
        workspaceService.getResult = Promise.ofException(new NoSuchElementException("not found"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-missing")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST suspend/reactivate map 409 conflict from service")
    void shouldMapSuspendReactivateConflicts() {
        workspaceService.suspendResult = Promise.ofException(new IllegalStateException("already suspended"));
        workspaceService.reactivateResult = Promise.ofException(new IllegalStateException("already active"));

        HttpRequest suspend = HttpRequest.post("http://localhost/v1/workspaces/ws-1/suspend")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .build();
        HttpRequest reactivate = HttpRequest.post("http://localhost/v1/workspaces/ws-1/reactivate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-3")
            .build();

        HttpResponse suspendResponse = runPromise(() -> servlet.serve(suspend));
        HttpResponse reactivateResponse = runPromise(() -> servlet.serve(reactivate));

        assertThat(suspendResponse.getCode()).isEqualTo(409);
        assertThat(reactivateResponse.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("maps security exceptions to 403")
    void shouldMapSecurityExceptionsTo403() {
        workspaceService.listResult = Promise.ofException(new SecurityException("denied"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET single workspace returns 200 on success")
    void shouldGetWorkspaceById() {
        workspaceService.getResult = Promise.of(workspace("ws-99", WorkspaceStatus.ACTIVE));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-99")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST suspend returns 200 on success")
    void shouldSuspendWorkspace() {
        workspaceService.suspendResult = Promise.of(workspace("ws-1", WorkspaceStatus.SUSPENDED));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/suspend")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-suspend-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST suspend returns 404 when workspace missing")
    void shouldReturn404OnSuspendMissingWorkspace() {
        workspaceService.suspendResult = Promise.ofException(new NoSuchElementException("ws-missing not found"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-missing/suspend")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-suspend-2")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST suspend returns 400 when idempotency key is missing")
    void shouldReturn400OnSuspendMissingIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/suspend")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST suspend returns 500 on unexpected service failure")
    void shouldReturn500OnSuspendUnexpectedFailure() {
        workspaceService.suspendResult = Promise.ofException(new RuntimeException("infra-failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/suspend")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-suspend-3")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST reactivate returns 200 on success")
    void shouldReactivateWorkspace() {
        workspaceService.reactivateResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/reactivate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-reactivate-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST reactivate returns 404 when workspace missing")
    void shouldReturn404OnReactivateMissingWorkspace() {
        workspaceService.reactivateResult = Promise.ofException(new NoSuchElementException("ws-gone not found"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-gone/reactivate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-reactivate-2")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST reactivate returns 400 when idempotency key is missing")
    void shouldReturn400OnReactivateMissingIdempotencyKey() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/reactivate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST reactivate returns 500 on unexpected service failure")
    void shouldReturn500OnReactivateUnexpectedFailure() {
        workspaceService.reactivateResult = Promise.ofException(new RuntimeException("infra-failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/reactivate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-reactivate-3")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST create maps SecurityException from service to 403 via handleError")
    void shouldMapCreateServiceSecurityExceptionTo403() {
        workspaceService.createResult = Promise.ofException(new SecurityException("principal not permitted"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-sec-1")
            .withBody("{\"name\":\"Denied\",\"description\":\"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET list returns 400 when X-Tenant-ID header is missing")
    void shouldReturn400OnMissingTenantHeaderForList() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET list returns 400 when X-Tenant-ID header is blank")
    void shouldReturn400OnBlankTenantHeaderForList() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "   ")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST create maps unknown service exception to 500")
    void shouldMapUnknownServiceExceptionTo500() {
        workspaceService.createResult = Promise.ofException(new RuntimeException("unexpected infra failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-500-1")
            .withBody("{\"name\":\"Test\",\"description\":\"test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET list returns 500 on unexpected service exception")
    void shouldReturn500OnListUnexpectedFailure() {
        workspaceService.listResult = Promise.ofException(new RuntimeException("unexpected list failure"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("GET single returns 500 on unexpected service exception")
    void shouldReturn500OnGetUnexpectedFailure() {
        workspaceService.getResult = Promise.ofException(new RuntimeException("unexpected get failure"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    private static Workspace workspace(String id, WorkspaceStatus status) {
        Instant now = Instant.now();
        return Workspace.builder()
            .id(DmWorkspaceId.of(id))
            .tenantId(DmTenantId.of("tenant-1"))
            .name("Workspace " + id)
            .description("desc")
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    private static final class FakeWorkspaceService implements WorkspaceService {
        private Promise<Workspace> createResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));
        private Promise<Workspace> getResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));
        private Promise<List<Workspace>> listResult = Promise.of(List.of(workspace("ws-1", WorkspaceStatus.ACTIVE)));
        private Promise<Workspace> suspendResult = Promise.of(workspace("ws-1", WorkspaceStatus.SUSPENDED));
        private Promise<Workspace> reactivateResult = Promise.of(workspace("ws-1", WorkspaceStatus.ACTIVE));
        private Promise<List<WorkspaceService.WorkspaceCapability>> capabilitiesResult = Promise.of(List.of());

        private DmOperationContext lastContext;
        private CreateWorkspaceCommand lastCreateCommand;

        @Override
        public Promise<Workspace> createWorkspace(DmOperationContext ctx, CreateWorkspaceCommand command) {
            this.lastContext = ctx;
            this.lastCreateCommand = command;
            return createResult;
        }

        @Override
        public Promise<Workspace> getWorkspace(DmOperationContext ctx, String workspaceId) {
            this.lastContext = ctx;
            return getResult;
        }

        @Override
        public Promise<List<Workspace>> listWorkspaces(DmOperationContext ctx) {
            this.lastContext = ctx;
            return listResult;
        }

        @Override
        public Promise<Workspace> suspendWorkspace(DmOperationContext ctx, String workspaceId) {
            this.lastContext = ctx;
            return suspendResult;
        }

        @Override
        public Promise<Workspace> reactivateWorkspace(DmOperationContext ctx, String workspaceId) {
            this.lastContext = ctx;
            return reactivateResult;
        }

        @Override
        public Promise<List<WorkspaceService.WorkspaceCapability>> getWorkspaceCapabilities(DmOperationContext ctx, String workspaceId) {
            this.lastContext = ctx;
            return capabilitiesResult;
        }
    }
}
