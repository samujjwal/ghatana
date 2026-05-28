package com.ghatana.phr.api.routes;

import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrConditionRoutes}.
 *
 * <p>Verifies that the condition endpoint delegates patient-record decisions to
 * {@link PhrPolicyEvaluator} and fails closed when policy denies access.
 *
 * @doc.type class
 * @doc.purpose Condition enforcement matrix: verifies policy-gated patient-record access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrConditionRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrConditionRoutesTest extends EventloopTestBase {

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), anyString()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        servlet = new PhrConditionRoutes(eventloop(), policyEvaluator).getServlet();
    }

    @Test
    @DisplayName("200 - patient policy allow returns conditions")
    void patientMayAccessOwnConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body(response)).contains("\"items\"", "\"Type 2 diabetes mellitus\"");
        verify(policyEvaluator).canAccessPatientRecordAsync(any(), eq("patient-1"));
    }

    @Test
    @DisplayName("403 - policy denial blocks another patient's conditions")
    void patientMayNotAccessOtherConditions() throws Exception {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), eq("patient-2")))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENIED", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        verify(policyEvaluator).canAccessPatientRecordAsync(any(), eq("patient-2"));
    }

    @Test
    @DisplayName("200 - clinician policy allow returns conditions")
    void clinicianMayAccessAnyConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 - admin policy allow returns conditions")
    void adminMayAccessAnyConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 - missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/patient-1").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }
}
