package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.application.record.RecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrTimelineRoutes}.
 *
 * <p>Verifies that timeline endpoints delegate patient-record decisions to
 * {@link PhrPolicyEvaluator} and fail closed when policy denies access.
 *
 * @doc.type class
 * @doc.purpose Timeline enforcement matrix: verifies policy-gated patient-record access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrTimelineRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrTimelineRoutesTest extends EventloopTestBase {

    @Mock
    private RecordService recordService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), anyString()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        lenient().when(recordService.getRecordTimeline(any(), anyString()))
            .thenReturn(Promise.of(new RecordService.RecordTimeline(
                "patient-1",
                List.of(timelineEntry()),
                "2026-01-01T00:00:00Z"
            )));
        lenient().when(recordService.getTimelineByCategory(any(), anyString(), anyString()))
            .thenReturn(Promise.of(List.of(timelineEntry())));
        servlet = new PhrTimelineRoutes(eventloop(), recordService, policyEvaluator).getServlet();
    }

    @Test
    @DisplayName("200 - patient policy allow returns timeline")
    void patientMayAccessOwnTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(policyEvaluator).canAccessPatientRecordAsync(any(), eq("patient-1"));
        verify(recordService).getRecordTimeline(any(), eq("patient-1"));
    }

    @Test
    @DisplayName("403 - policy denial blocks another patient's timeline")
    void patientMayNotAccessOtherTimeline() throws Exception {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), eq("patient-2")))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENIED", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(policyEvaluator).canAccessPatientRecordAsync(any(), eq("patient-2"));
        verify(recordService, never()).getRecordTimeline(any(), eq("patient-2"));
    }

    @Test
    @DisplayName("200 - clinician policy allow returns timeline")
    void clinicianMayAccessAnyTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 - admin policy allow returns timeline")
    void adminMayAccessAnyTimeline() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 - category timeline also uses policy evaluator")
    void categoryTimelineUsesPolicyEvaluator() throws Exception {
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-1/category/labs", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(policyEvaluator).canAccessPatientRecordAsync(any(), eq("patient-1"));
        verify(recordService).getTimelineByCategory(any(), eq("patient-1"), eq("labs"));
    }

    @Test
    @DisplayName("403 - category timeline denies before service lookup")
    void categoryTimelineDeniesBeforeServiceLookup() throws Exception {
        lenient().when(policyEvaluator.canAccessPatientRecordAsync(any(), eq("patient-2")))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENIED", "denied")));
        HttpRequest request = contextRequest(
            HttpMethod.GET, "/patient-2/category/labs", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        verify(recordService, never()).getTimelineByCategory(any(), eq("patient-2"), eq("labs"));
    }

    @Test
    @DisplayName("400 - missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/patient-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
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

    private static RecordService.TimelineEntry timelineEntry() {
        return new RecordService.TimelineEntry(
            "entry-1",
            "2026-01-01T00:00:00Z",
            "labs",
            "LAB_RESULT",
            "Normal",
            Map.of("status", "ok")
        );
    }
}
