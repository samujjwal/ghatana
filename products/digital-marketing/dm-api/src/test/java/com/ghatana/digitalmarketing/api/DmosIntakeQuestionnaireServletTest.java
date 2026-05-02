package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.intake.IntakeQuestionnaireService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
import com.ghatana.digitalmarketing.domain.intake.IntakeStatus;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmosIntakeQuestionnaireServlet")
class DmosIntakeQuestionnaireServletTest extends EventloopTestBase {

    private FakeIntakeService intakeService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        intakeService = new FakeIntakeService();
        servlet = new DmosIntakeQuestionnaireServlet(intakeService, Eventloop.create()).getServlet();
    }

    @Test
    @DisplayName("constructor rejects null dependencies")
    void shouldRejectNullDependencies() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosIntakeQuestionnaireServlet(null, Eventloop.create()));
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosIntakeQuestionnaireServlet(intakeService, null));
    }

    @Test
    @DisplayName("PUT draft saves intake questionnaire")
    void shouldSaveDraft() {
        HttpRequest request = HttpRequest.put("http://localhost/v1/workspaces/ws-1/intake/questionnaire/draft")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "owner-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Roles"), "admin, , editor")
            .withHeader(HttpHeaders.of("X-Permissions"), "intake:write, ,intake:read")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-1")
            .withBody(("{"
                + "\"businessName\":\"Acme\"," 
                + "\"websiteUrl\":\"https://acme.test\"," 
                + "\"offerSummary\":\"SEO retainers\"," 
                + "\"targetAudience\":\"SMBs\"," 
                + "\"primaryGeography\":\"US\"," 
                + "\"monthlyBudgetAmount\":1800," 
                + "\"growthGoal\":\"Book 20 consultations\""
                + "}").getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(intakeService.lastContext.getWorkspaceId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(intakeService.lastSaveDraftCommand.businessName()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("PUT draft returns 400 when idempotency key is missing")
    void shouldRejectSaveDraftWithoutIdempotencyKey() {
        HttpRequest request = HttpRequest.put("http://localhost/v1/workspaces/ws-1/intake/questionnaire/draft")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET draft returns 200 when intake exists")
    void shouldGetDraft() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/intake/questionnaire/draft")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET draft returns 404 when intake does not exist")
    void shouldReturn404WhenDraftMissing() {
        intakeService.getResult = Promise.ofException(new NoSuchElementException("Intake draft not found"));

        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/intake/questionnaire/draft")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST submit returns 200 when intake is submitted")
    void shouldSubmitIntake() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/intake/questionnaire/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody(("{"
                + "\"aiSummary\":\"Focus on high-intent landing pages\"," 
                + "\"aiConfidenceScore\":0.79," 
                + "\"aiUnknowns\":[\"No CAC baseline\"]"
                + "}").getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(intakeService.lastSubmitCommand.aiUnknowns()).containsExactly("No CAC baseline");
    }

    @Test
    @DisplayName("POST submit returns 400 when tenant header is blank")
    void shouldReturn400OnBlankTenant() {
        HttpRequest request = HttpRequest.post("http://localhost/v1/workspaces/ws-1/intake/questionnaire/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "   ")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));
        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST submit maps security and runtime errors")
    void shouldMapSubmitErrors() {
        intakeService.submitResult = Promise.ofException(new SecurityException("denied"));

        HttpRequest securityRequest = HttpRequest.post("http://localhost/v1/workspaces/ws-1/intake/questionnaire/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody("{\"aiSummary\":\"Summary\",\"aiConfidenceScore\":0.7}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse securityResponse = runPromise(() -> servlet.serve(securityRequest));
        assertThat(securityResponse.getCode()).isEqualTo(403);

        intakeService.submitResult = Promise.ofException(new RuntimeException("infra"));

        HttpRequest runtimeRequest = HttpRequest.post("http://localhost/v1/workspaces/ws-1/intake/questionnaire/submit")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-2")
            .withBody("{\"aiSummary\":\"Summary\",\"aiConfidenceScore\":0.7}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse runtimeResponse = runPromise(() -> servlet.serve(runtimeRequest));
        assertThat(runtimeResponse.getCode()).isEqualTo(500);
    }

    private static final class FakeIntakeService implements IntakeQuestionnaireService {
        private Promise<BusinessIntakeProfile> saveResult = Promise.of(sampleProfile(IntakeStatus.DRAFT));
        private Promise<BusinessIntakeProfile> getResult = Promise.of(sampleProfile(IntakeStatus.DRAFT));
        private Promise<BusinessIntakeProfile> submitResult = Promise.of(sampleProfile(IntakeStatus.SUBMITTED));

        private DmOperationContext lastContext;
        private SaveDraftCommand lastSaveDraftCommand;
        private SubmitIntakeCommand lastSubmitCommand;

        @Override
        public Promise<BusinessIntakeProfile> saveDraft(DmOperationContext ctx, SaveDraftCommand command) {
            this.lastContext = ctx;
            this.lastSaveDraftCommand = command;
            return saveResult;
        }

        @Override
        public Promise<BusinessIntakeProfile> getDraft(DmOperationContext ctx) {
            this.lastContext = ctx;
            return getResult;
        }

        @Override
        public Promise<BusinessIntakeProfile> submitIntake(DmOperationContext ctx, SubmitIntakeCommand command) {
            this.lastContext = ctx;
            this.lastSubmitCommand = command;
            return submitResult;
        }

        private static BusinessIntakeProfile sampleProfile(IntakeStatus status) {
            Instant now = Instant.now();
            return BusinessIntakeProfile.builder()
                .intakeId("intake-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .businessName("Acme")
                .websiteUrl("https://acme.test")
                .offerSummary("SEO retainers")
                .targetAudience("SMBs")
                .primaryGeography("US")
                .monthlyBudgetAmount(new BigDecimal("1800"))
                .competitorDomains(List.of("comp-a.test"))
                .constraints(List.of("No cold calling"))
                .growthGoal("Book 20 consultations")
                .riskTolerance("MEDIUM")
                .aiSummary(status == IntakeStatus.SUBMITTED ? "summary" : "")
                .aiConfidenceScore(status == IntakeStatus.SUBMITTED ? 0.79 : 0.0)
                .aiUnknowns(status == IntakeStatus.SUBMITTED ? List.of("No CAC baseline") : List.of())
                .status(status)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("user-1")
                .submittedAt(status == IntakeStatus.SUBMITTED ? now : null)
                .build();
        }
    }
}
