package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DmosCampaignServlet}.
 */
@DisplayName("DmosCampaignServlet")
@ExtendWith(MockitoExtension.class)
class DmosCampaignServletTest extends EventloopTestBase {

    @Mock private CampaignService campaignService;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        Eventloop eventloop = Eventloop.create();
        servlet = new DmosCampaignServlet(campaignService, eventloop).getServlet();
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("constructor throws on null campaignService")
    void shouldThrowOnNullCampaignService() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCampaignServlet(null, Eventloop.create()));
    }

    @Test
    @DisplayName("constructor throws on null eventloop")
    void shouldThrowOnNullEventloop() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosCampaignServlet(campaignService, null));
    }

    // -----------------------------------------------------------------------
    // POST /v1/workspaces/:workspaceId/campaigns
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST campaigns returns 201 with campaign response")
    void shouldReturn201OnCreateCampaign() {
        Campaign campaign = buildCampaign();
        when(campaignService.createCampaign(any(), any())).thenReturn(Promise.of(campaign));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Q4 Acquisition\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("POST campaigns returns 403 when service throws SecurityException")
    void shouldReturn403OnUnauthorized() {
        when(campaignService.createCampaign(any(), any()))
            .thenReturn(Promise.ofException(new SecurityException("Not authorized")));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Test\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST campaigns returns 400 when X-Tenant-ID is missing")
    void shouldReturn400WhenTenantHeaderMissing() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/campaigns")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{\"name\":\"Test\",\"type\":\"EMAIL\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    // -----------------------------------------------------------------------
    // GET /v1/workspaces/:workspaceId/campaigns/:id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET campaign returns 200 with campaign response")
    void shouldReturn200OnGetCampaign() {
        Campaign campaign = buildCampaign();
        when(campaignService.getCampaign(any(), eq("campaign-1"))).thenReturn(Promise.of(campaign));

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/campaigns/campaign-1")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET campaign returns 404 when campaign not found")
    void shouldReturn404WhenNotFound() {
        when(campaignService.getCampaign(any(), any()))
            .thenReturn(Promise.ofException(new NoSuchElementException("Campaign not found")));

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/campaigns/missing")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    // -----------------------------------------------------------------------
    // POST launch
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST launch returns 200 with launched campaign")
    void shouldReturn200OnLaunch() {
        Campaign launched = buildCampaign().launch();
        when(campaignService.launchCampaign(any(), eq("campaign-1"))).thenReturn(Promise.of(launched));

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST launch returns 409 when campaign is in invalid state")
    void shouldReturn409OnInvalidState() {
        when(campaignService.launchCampaign(any(), any()))
            .thenReturn(Promise.ofException(new IllegalStateException("Cannot launch COMPLETED")));

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/launch")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(409);
    }

    // -----------------------------------------------------------------------
    // POST pause
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST pause returns 200 with paused campaign")
    void shouldReturn200OnPause() {
        Campaign paused = buildCampaign().launch().pause();
        when(campaignService.pauseCampaign(any(), eq("campaign-1"))).thenReturn(Promise.of(paused));

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/campaigns/campaign-1/pause")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Campaign buildCampaign() {
        Instant now = Instant.now();
        return Campaign.builder()
            .id("campaign-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .name("Q4 Acquisition")
            .type(CampaignType.EMAIL)
            .status(CampaignStatus.DRAFT)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-42")
            .build();
    }
}
