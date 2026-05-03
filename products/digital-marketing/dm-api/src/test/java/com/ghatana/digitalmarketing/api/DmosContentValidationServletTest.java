package com.ghatana.digitalmarketing.api;

import com.ghatana.digitalmarketing.application.validation.ContentValidationService;
import com.ghatana.digitalmarketing.application.validation.ContentValidationService.ValidateContentVersionCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult.ValidationOutcome;
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

@DisplayName("DmosContentValidationServlet")
class DmosContentValidationServletTest extends EventloopTestBase {

    private FakeContentValidationService validationService;
    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        validationService = new FakeContentValidationService();
        servlet = new DmosContentValidationServlet(validationService, Eventloop.create()).routes();
    }

    private static final String VALIDATE_BODY =
        "{\"forbiddenTerms\":[\"spam\"],\"requiredClaimIds\":[\"claim-1\"]}";

    private static final String EMPTY_VALIDATE_BODY = "{}";

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects null validationService")
    void rejectsNullService() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosContentValidationServlet(null, Eventloop.create()));
    }

    @Test
    @DisplayName("constructor rejects null eventloop")
    void rejectsNullEventloop() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DmosContentValidationServlet(validationService, null));
    }

    // -------------------------------------------------------------------------
    // POST validate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST validate returns 200 with PASS outcome")
    void postValidateReturns200() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asArray() != null
            ? new String(response.getBody().asArray(), StandardCharsets.UTF_8)
            : "";
        assertThat(body).contains("PASS");
    }

    @Test
    @DisplayName("POST validate with empty body (null lists) returns 200")
    void postValidateEmptyBodyReturns200() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(EMPTY_VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST validate returns 403 when service throws SecurityException")
    void postValidateReturns403OnSecurity() {
        validationService.throwOnValidate = new SecurityException("Denied");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST validate returns 404 when service throws NoSuchElementException")
    void postValidateReturns404OnNotFound() {
        validationService.throwOnValidate = new NoSuchElementException("Version not found");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("POST validate returns 400 when service throws IllegalArgumentException")
    void postValidateReturns400OnBadArg() {
        validationService.throwOnValidate = new IllegalArgumentException("Bad input");

        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST validate returns 400 when X-Tenant-ID header is missing")
    void postValidateReturns400WhenTenantMissing() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody(VALIDATE_BODY.getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // GET validation-results
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET validation-results returns 200 with results list")
    void getValidationResultsReturns200() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validation-results")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET validation-results returns 403 when service throws SecurityException")
    void getValidationResultsReturns403() {
        validationService.throwOnList = new SecurityException("Denied");

        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validation-results")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("GET validation-results returns 400 when X-Tenant-ID header is missing")
    void getValidationResultsReturns400WhenTenantMissing() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validation-results")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("POST validate with null forbiddenTerms and requiredClaimIds defaults to empty lists")
    void postValidateWithNullsDefaultsToEmpty() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody("{}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST validate with malformed JSON returns 400")
    void postValidateWithMalformedJson() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody("{bad json}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET validation-results with empty list returns 200")
    void getValidationResultsWithEmptyList() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-999/validation-results")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST validate with explicit empty arrays returns 200")
    void postValidateWithExplicitEmptyArrays() {
        HttpRequest request = HttpRequest.post(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validate")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withBody("{\"forbiddenTerms\":[],\"requiredClaimIds\":[]}".getBytes(StandardCharsets.UTF_8))
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET validation-results with correlation ID and roles returns 200")
    void getValidationResultsWithOptionalHeaders() {
        HttpRequest request = HttpRequest.get(
                "http://localhost/v1/workspaces/ws-1/content-versions/ver-1/validation-results")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-alice")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-123")
            .withHeader(HttpHeaders.of("X-Session-ID"), "sess-456")
            .withHeader(HttpHeaders.of("X-Roles"), "admin,reviewer")
            .withHeader(HttpHeaders.of("X-Permissions"), "read,write")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    static final class FakeContentValidationService implements ContentValidationService {

        RuntimeException throwOnValidate = null;
        RuntimeException throwOnList     = null;
        List<ContentValidationResult> storedResults = new java.util.ArrayList<>();

        @Override
        public Promise<ContentValidationResult> validateVersion(
                DmOperationContext ctx, ValidateContentVersionCommand command) {
            if (throwOnValidate != null) {
                return Promise.ofException(throwOnValidate);
            }
            ContentValidationResult result = new ContentValidationResult(
                command.versionId(),
                ValidationOutcome.PASS,
                List.of(),
                Instant.now(),
                ctx.getActor().getPrincipalId());
            storedResults.add(result);
            return Promise.of(result);
        }

        @Override
        public Promise<List<ContentValidationResult>> listResults(
                DmOperationContext ctx, String versionId) {
            if (throwOnList != null) {
                return Promise.ofException(throwOnList);
            }
            return Promise.of(storedResults.stream()
                .filter(r -> r.versionId().equals(versionId))
                .toList());
        }
    }
}
