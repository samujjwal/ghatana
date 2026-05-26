package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.application.funnel.TrialOnboardingService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosTrialOnboardingServlet")
class DmosTrialOnboardingServletTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeTrialOnboardingService service;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        service = new FakeTrialOnboardingService();
        DmosHttpContextFactory contextFactory = DmosHttpContextFactory.testModeWithAnonymousFallback();
        servlet = new DmosTrialOnboardingServlet(
            Eventloop.create(),
            service,
            contextFactory,
            new ObjectMapper(),
            DmosMetricsCollector.disabled()
        ).getServlet();
    }

    @Test
    @DisplayName("POST create returns canonical 400 envelope on malformed request")
    void shouldReturnCanonicalBadRequestEnvelopeOnMalformedCreatePayload() throws Exception {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/trial-onboardings")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-malformed")
            .withBody("{".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        JsonNode envelope = MAPPER.readTree(response.getBody().getString(StandardCharsets.UTF_8));
        assertThat(envelope.get("status").asInt()).isEqualTo(400);
        assertThat(envelope.get("error").asText()).isEqualTo("BAD_REQUEST");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-malformed");
    }

    @Test
    @DisplayName("POST create maps service SecurityException to canonical 403 envelope")
    void shouldMapServiceSecurityExceptionToForbiddenEnvelope() throws Exception {
        service.createResult = Promise.ofException(new SecurityException("Access denied"));

        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/trial-onboardings")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-forbidden")
            .withBody("{\"leadId\":\"lead-1\",\"demoWorkspaceId\":\"demo-1\",\"totalSteps\":3}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        JsonNode envelope = MAPPER.readTree(response.getBody().getString(StandardCharsets.UTF_8));
        assertThat(envelope.get("status").asInt()).isEqualTo(403);
        assertThat(envelope.get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-forbidden");
    }

    private static final class FakeTrialOnboardingService implements TrialOnboardingService {

        private Promise<TrialOnboarding> createResult = Promise.ofException(new UnsupportedOperationException("create not stubbed"));

        @Override
        public Promise<TrialOnboarding> create(DmOperationContext ctx, CreateTrialOnboardingCommand command) {
            return createResult;
        }

        @Override
        public Promise<TrialOnboarding> start(DmOperationContext ctx, String onboardingId) {
            return Promise.ofException(new UnsupportedOperationException("start not stubbed"));
        }

        @Override
        public Promise<TrialOnboarding> advanceStep(DmOperationContext ctx, String onboardingId, int stepNumber, Map<String, Object> progress) {
            return Promise.ofException(new UnsupportedOperationException("advanceStep not stubbed"));
        }

        @Override
        public Promise<TrialOnboarding> complete(DmOperationContext ctx, String onboardingId) {
            return Promise.ofException(new UnsupportedOperationException("complete not stubbed"));
        }

        @Override
        public Promise<TrialOnboarding> cancel(DmOperationContext ctx, String onboardingId, String reason) {
            return Promise.ofException(new UnsupportedOperationException("cancel not stubbed"));
        }

        @Override
        public Promise<Optional<TrialOnboarding>> findById(DmOperationContext ctx, String onboardingId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Optional<TrialOnboarding>> findByLeadId(DmOperationContext ctx, String leadId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<TrialOnboarding>> list(DmOperationContext ctx) {
            return Promise.of(List.of());
        }
    }
}
