package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.lead.LeadService;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import com.ghatana.digitalmarketing.domain.lead.LeadStatus;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosPublicIntakeServlet")
class DmosPublicIntakeServletTest extends EventloopTestBase {

    private InMemoryLeadService leadService;
    private InMemorySuppressionService suppressionService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        leadService = new InMemoryLeadService();
        suppressionService = new InMemorySuppressionService();
        servlet = new DmosPublicIntakeServlet(leadService, suppressionService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosPublicIntakeServlet(null, suppressionService, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosPublicIntakeServlet(leadService, null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosPublicIntakeServlet(leadService, suppressionService, null));
    }

    @Test
    @DisplayName("captures lead when email is not suppressed")
    void shouldCaptureLead() {
        suppressionService.suppressed = false;
        String payload = """
            {
              "campaignId":"camp-1",
              "email":"a@example.com",
              "firstName":"A",
              "lastName":"B",
              "phone":"123",
              "source":"landing"
            }
            """;

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(leadService.lastCommand.email()).isEqualTo("a@example.com");
    }

    @Test
    @DisplayName("defaults principal to public-intake when principal header is blank")
    void shouldDefaultPrincipalWhenHeaderBlank() {
        suppressionService.suppressed = false;
        String payload = "{\"campaignId\":\"camp-1\",\"email\":\"a@example.com\"}";

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "  ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        assertThat(leadService.lastContext.getActor().getPrincipalId()).isEqualTo("public-intake");
    }

    @Test
    @DisplayName("returns 409 when email is suppressed")
    void shouldReturn409WhenSuppressed() {
        suppressionService.suppressed = true;
        String payload = """
            {
              "campaignId":"camp-1",
              "email":"blocked@example.com"
            }
            """;

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("returns 400 when idempotency key is missing")
    void shouldReturn400OnMissingIdempotency() {
        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns 400 when tenant header is missing")
    void shouldReturn400OnMissingTenantHeader() {
        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns 403 when lead service denies capture")
    void shouldReturn403WhenLeadServiceDenied() {
        leadService.captureError = new SecurityException("denied");
        String payload = "{\"campaignId\":\"camp-1\",\"email\":\"x@example.com\"}";

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("returns 400 when lead service rejects payload")
    void shouldReturn400WhenLeadServiceValidationFails() {
        leadService.captureError = new IllegalArgumentException("invalid payload");
        String payload = "{\"campaignId\":\"camp-1\",\"email\":\"x@example.com\"}";

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns 500 on unexpected lead service failure")
    void shouldReturn500WhenLeadServiceFailsUnexpectedly() {
        leadService.captureError = new RuntimeException("db down");
        String payload = "{\"campaignId\":\"camp-1\",\"email\":\"x@example.com\"}";

        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(payload.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("returns 500 when payload is malformed JSON")
    void shouldReturn500OnMalformedJson() {
        HttpRequest request = HttpRequest.post("http://localhost/public/v1/workspaces/ws-1/intake/leads")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody("{bad-json".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(500);
    }

    private static final class InMemorySuppressionService implements SuppressionService {
        private boolean suppressed;

        @Override
        public Promise<SuppressionEntry> addSuppression(
                DmOperationContext ctx,
                AddSuppressionCommand command) {
            Instant now = Instant.now();
            return Promise.of(SuppressionEntry.builder()
                .id("sup-1")
                .workspaceId(ctx.getWorkspaceId())
                .email(command.email())
                .reason(command.reason())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("test")
                .build());
        }

        @Override
        public Promise<SuppressionEntry> removeSuppression(
                DmOperationContext ctx,
                String email) {
            Instant now = Instant.now();
            return Promise.of(SuppressionEntry.builder()
                .id("sup-1")
                .workspaceId(ctx.getWorkspaceId())
                .email(email)
                .reason("removed")
                .active(false)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("test")
                .build());
        }

        @Override
        public Promise<Boolean> isSuppressed(DmOperationContext ctx, String email) {
            return Promise.of(suppressed);
        }
    }

    private static final class InMemoryLeadService implements LeadService {
        private CaptureLeadCommand lastCommand;
        private RuntimeException captureError;
        private DmOperationContext lastContext;

        @Override
        public Promise<Lead> captureLead(DmOperationContext ctx, CaptureLeadCommand command) {
            if (captureError != null) {
                return Promise.ofException(captureError);
            }
            lastContext = ctx;
            lastCommand = command;
            return Promise.of(Lead.builder()
                .id("lead-1")
                .workspaceId(ctx.getWorkspaceId())
                .campaignId(command.campaignId())
                .email(command.email())
                .firstName(command.firstName())
                .lastName(command.lastName())
                .phone(command.phone())
                .source(command.source())
                .status(LeadStatus.NEW)
                .capturedAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        }

        @Override
        public Promise<Lead> qualifyLead(DmOperationContext ctx, String leadId) {
            return getLead(ctx, leadId)
                .map(Lead::qualify);
        }

        @Override
        public Promise<Lead> convertLead(DmOperationContext ctx, String leadId) {
            return getLead(ctx, leadId)
                .map(existing -> existing.qualify().convert());
        }

        @Override
        public Promise<Lead> disqualifyLead(DmOperationContext ctx, String leadId) {
            return getLead(ctx, leadId)
                .map(Lead::disqualify);
        }

        @Override
        public Promise<Lead> getLead(DmOperationContext ctx, String leadId) {
            if (lastCommand == null) {
                return Promise.ofException(new java.util.NoSuchElementException("Lead not found: " + leadId));
            }
            return Promise.of(Lead.builder()
                .id(leadId)
                .workspaceId(ctx.getWorkspaceId())
                .campaignId(lastCommand.campaignId())
                .email(lastCommand.email())
                .firstName(lastCommand.firstName())
                .lastName(lastCommand.lastName())
                .phone(lastCommand.phone())
                .source(Optional.ofNullable(lastCommand.source()).orElse("unknown"))
                .status(LeadStatus.NEW)
                .capturedAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        }
    }
}
