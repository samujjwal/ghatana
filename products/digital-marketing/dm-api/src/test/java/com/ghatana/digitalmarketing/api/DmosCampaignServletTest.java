package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.campaign.CampaignComplianceViolationException;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
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
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosCampaignServlet")
class DmosCampaignServletTest extends EventloopTestBase {

    private FakeCampaignService campaignService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        campaignService = new FakeCampaignService();
        Eventloop eventloop = Eventloop.create();
        servlet = new DmosCampaignServlet(campaignService, eventloop).getServlet();
    }

    @Test
    @DisplayName("constructor throws on null dependencies")
    void shouldThrowOnNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCampaignServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCampaignServlet(campaignService, null));
    }

    @Test
    @DisplayName("POST campaigns returns 201 on success")
    void shouldReturn201OnCreateCampaign() {
        campaignService.createResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Q4 Acquisition\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(201);
        assertThat(campaignService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(campaignService.lastContext.getTenantId().getValue()).isEqualTo("tenant-1");
        assertThat(campaignService.lastCommand.name()).isEqualTo("Q4 Acquisition");
    }

    @Test
    @DisplayName("POST campaigns returns 403 on unauthorized")
    void shouldReturn403OnCreateUnauthorized() {
        campaignService.createResult = Promise.ofException(new SecurityException("Not authorized"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Denied\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST campaigns returns 500 on unexpected service failure")
    void shouldReturn500OnCreateUnexpectedFailure() {
        campaignService.createResult = Promise.ofException(new RuntimeException("create-failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Q4 Acquisition\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST campaigns returns 400 when tenant header is missing")
    void shouldReturn400WhenTenantHeaderMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Test\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST campaigns returns 400 when tenant header is blank")
    void shouldReturn400WhenTenantHeaderBlank() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "   ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Test\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST campaigns uses anonymous principal when principal header is blank")
    void shouldDefaultPrincipalWhenHeaderBlankOnCreate() {
        campaignService.createResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "   ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Q4 Acquisition\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(201);
        assertThat(campaignService.lastContext.getActor().getPrincipalId()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("POST campaigns parses role and permission headers with blanks")
    void shouldParseRolesAndPermissionsOnCreate() {
        campaignService.createResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Roles"), "owner, , operator")
            .withHeader(HttpHeaders.of("X-Permissions"), "campaign:write, ,campaign:read")
            .withBody("{\"name\":\"Q4 Acquisition\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST campaigns returns 500 when payload is malformed JSON")
    void shouldReturn500OnCreateMalformedJson() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{bad-json".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST campaigns returns 400 when idempotency key is missing")
    void shouldReturn400WhenIdempotencyKeyMissingOnCreate() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{\"name\":\"Test\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET campaign returns 200 when found")
    void shouldReturn200OnGetCampaign() {
        campaignService.getResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET campaign accepts optional idempotency header and defaults blank principal")
    void shouldHandleOptionalIdempotencyAndBlankPrincipalOnGet() {
        campaignService.getResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "   ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-read-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(campaignService.lastContext.getActor().getPrincipalId()).isEqualTo("anonymous");
        assertThat(campaignService.lastContext.getIdempotencyKey()).isNotNull();
    }

    @Test
    @DisplayName("GET campaign returns 404 when missing")
    void shouldReturn404OnMissingCampaign() {
        campaignService.getResult = Promise.ofException(new NoSuchElementException("Campaign not found"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/missing")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET campaign returns 403 when unauthorized")
    void shouldReturn403OnGetUnauthorized() {
        campaignService.getResult = Promise.ofException(new SecurityException("Not authorized"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET campaign returns 400 when tenant header is missing")
    void shouldReturn400OnGetMissingTenantHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET campaign returns 500 on unexpected service failure")
    void shouldReturn500OnGetUnexpectedFailure() {
        campaignService.getResult = Promise.ofException(new RuntimeException("infra-failure"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST launch returns 200 when campaign launches")
    void shouldReturn200OnLaunch() {
        campaignService.launchResult = Promise.of(buildCampaign(CampaignStatus.LAUNCHED));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST launch returns 409 for invalid campaign state")
    void shouldReturn409OnLaunchConflict() {
        campaignService.launchResult = Promise.ofException(new IllegalStateException("Invalid state"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST launch returns 422 for compliance violation")
    void shouldReturn422OnComplianceViolation() {
        campaignService.launchResult = Promise.ofException(
            new CampaignComplianceViolationException("Failed preflight")
        );

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(422);
    }

    @Test
    @DisplayName("POST launch returns 404 when campaign missing")
    void shouldReturn404OnLaunchMissingCampaign() {
        campaignService.launchResult = Promise.ofException(new NoSuchElementException("missing"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST launch returns 400 when tenant header is missing")
    void shouldReturn400OnLaunchMissingTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST launch returns 500 on unexpected service failure")
    void shouldReturn500OnLaunchUnexpectedFailure() {
        campaignService.launchResult = Promise.ofException(new RuntimeException("launch-failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST pause returns 200 when campaign pauses")
    void shouldReturn200OnPause() {
        campaignService.pauseResult = Promise.of(buildCampaign(CampaignStatus.PAUSED));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST pause returns 409 for invalid campaign state")
    void shouldReturn409OnPauseConflict() {
        campaignService.pauseResult = Promise.ofException(new IllegalStateException("Invalid state"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("POST pause returns 403 when unauthorized")
    void shouldReturn403OnPauseUnauthorized() {
        campaignService.pauseResult = Promise.ofException(new SecurityException("Not authorized to pause"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST pause returns 404 when campaign missing")
    void shouldReturn404OnPauseMissingCampaign() {
        campaignService.pauseResult = Promise.ofException(new java.util.NoSuchElementException("missing"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST pause returns 400 when tenant header is missing")
    void shouldReturn400OnPauseMissingTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST pause returns 500 on unexpected service failure")
    void shouldReturn500OnPauseUnexpectedFailure() {
        campaignService.pauseResult = Promise.ofException(new RuntimeException("pause-failure"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("POST launch returns 403 when unauthorized")
    void shouldReturn403OnLaunchUnauthorized() {
        campaignService.launchResult = Promise.ofException(new SecurityException("Not authorized to launch"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    private static Campaign buildCampaign(CampaignStatus status) {
        Instant now = Instant.now();
        return Campaign.builder()
            .id("campaign-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Q4 Acquisition")
            .type(CampaignType.EMAIL)
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-42")
            .build();
    }

    private static final class FakeCampaignService implements CampaignService {
        private Promise<Campaign> createResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));
        private Promise<Campaign> launchResult = Promise.of(buildCampaign(CampaignStatus.LAUNCHED));
        private Promise<Campaign> pauseResult = Promise.of(buildCampaign(CampaignStatus.PAUSED));
        private Promise<Campaign> getResult = Promise.of(buildCampaign(CampaignStatus.DRAFT));

        private DmOperationContext lastContext;
        private CreateCampaignCommand lastCommand;

        @Override
        public Promise<Campaign> createCampaign(DmOperationContext ctx, CreateCampaignCommand command) {
            this.lastContext = ctx;
            this.lastCommand = command;
            return createResult;
        }

        @Override
        public Promise<Campaign> launchCampaign(DmOperationContext ctx, String campaignId) {
            this.lastContext = ctx;
            return launchResult;
        }

        @Override
        public Promise<Campaign> pauseCampaign(DmOperationContext ctx, String campaignId) {
            this.lastContext = ctx;
            return pauseResult;
        }

        @Override
        public Promise<Campaign> getCampaign(DmOperationContext ctx, String campaignId) {
            this.lastContext = ctx;
            return getResult;
        }
    }
}
