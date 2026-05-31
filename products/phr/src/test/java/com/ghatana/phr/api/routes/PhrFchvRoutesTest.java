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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrFchvRoutes}.
 *
 * <p>Verifies that FCHV patient PHI paths use community-assignment policy
 * decisions and fail closed when that policy denies access.
 *
 * @doc.type class
 * @doc.purpose FCHV enforcement matrix: verifies role gates and policy-gated patient PHI access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrFchvRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrFchvRoutesTest extends EventloopTestBase {

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        servlet = new PhrFchvRoutes(eventloop(), policyEvaluator).getServlet();
    }

    @Test
    @DisplayName("200 - FCHV may access FCHV dashboard")
    void fchvMayAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 - admin may access FCHV dashboard")
    void adminMayAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 - patient may not access FCHV dashboard")
    void patientMayNotAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("403 - clinician may not access FCHV dashboard")
    void clinicianMayNotAccessFchvDashboard() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/dashboard", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("200 - assigned FCHV policy allow returns patient record")
    void fchvMayAccessAssignedPatient() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patients/patient-1", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("fchv-patient-summary"), eq("READ"), eq("t1"), any());
    }

    @Test
    @DisplayName("403 - community policy denial blocks patient record")
    void policyDenialBlocksFchvPatient() throws Exception {
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), eq("patient-2"), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("FCHV_NO_COMMUNITY_ACCESS", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patients/patient-2", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("403 - patient role is denied by FCHV patient policy")
    void patientRoleIsDeniedByFchvPatientPolicy() throws Exception {
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), eq("patient-1"), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("FCHV_ROLE_REQUIRED", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patients/patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("fchv-patient-summary"), eq("READ"), eq("t1"), any());
    }

    @Test
    @DisplayName("201 - vitals write uses FCHV policy")
    void vitalsWriteUsesPolicy() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.POST, "/patients/patient-1/vitals", "t1", "fchv-1", "fchv");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(201);
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("fchv-vitals"), eq("WRITE"), eq("t1"), any());
    }

    @Test
    @DisplayName("400 - missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/dashboard").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
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
}
