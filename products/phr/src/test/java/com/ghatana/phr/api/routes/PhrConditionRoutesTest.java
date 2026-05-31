package com.ghatana.phr.api.routes;

import com.ghatana.phr.application.clinical.ClinicalService;
import com.ghatana.phr.kernel.service.PatientRecordServiceExtensions;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    @Mock
    private ClinicalService clinicalService;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), anyString()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        lenient().when(clinicalService.listConditions(any(), anyString()))
            .thenReturn(Promise.of(List.of(new ClinicalService.Condition(
                "cond-1",
                "patient-1",
                "Type 2 diabetes mellitus",
                "44054006",
                "chronic",
                "ACTIVE",
                "2020-01-01T00:00:00Z",
                null
            ))));
        lenient().when(clinicalService.getCondition(any(), eq("cond-1")))
            .thenReturn(Promise.of(Optional.of(new ClinicalService.Condition(
                "cond-1",
                "patient-1",
                "Type 2 diabetes mellitus",
                "44054006",
                "chronic",
                "ACTIVE",
                "2020-01-01T00:00:00Z",
                null
            ))));
        servlet = new PhrConditionRoutes(eventloop(), policyEvaluator, clinicalService).getServlet();
    }

    @Test
    @DisplayName("200 - patient policy allow returns conditions")
    void patientMayAccessOwnConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/?patientId=patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        assertThat(body(response)).contains("\"items\"", "\"Type 2 diabetes mellitus\"", "\"resourceType\":\"Condition\"");
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("conditions"), eq("READ"), eq("t1"), any());
        verify(clinicalService).listConditions(any(), eq("patient-1"));
    }

    @Test
    @DisplayName("403 - policy denial blocks another patient's conditions")
    void patientMayNotAccessOtherConditions() throws Exception {
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), eq("patient-2"), eq("conditions"), eq("READ"), eq("t1"), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENIED", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/?patientId=patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-2"), eq("conditions"), eq("READ"), eq("t1"), any());
        verify(clinicalService, never()).listConditions(any(), eq("patient-2"));
    }

    @Test
    @DisplayName("200 - clinician policy allow returns conditions")
    void clinicianMayAccessAnyConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/?patientId=patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 - admin policy allow returns conditions")
    void adminMayAccessAnyConditions() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/?patientId=patient-1", "t1", "admin-1", "admin");

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

    @Test
    @DisplayName("400 - missing patientId echoes request correlation id")
    void missingPatientIdEchoesCorrelationId() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    @Test
    @DisplayName("200 - condition detail returns backend DTO and FHIR condition")
    void conditionDetailUsesClinicalModel() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/cond-1?patientId=patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        assertThat(body(response)).contains(
            "\"id\":\"cond-1\"",
            "\"status\":\"chronic\"",
            "\"resourceType\":\"Condition\"",
            "\"subject\":{\"reference\":\"Patient/patient-1\"}"
        );
        verify(clinicalService).getCondition(any(), eq("cond-1"));
    }

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }
}
