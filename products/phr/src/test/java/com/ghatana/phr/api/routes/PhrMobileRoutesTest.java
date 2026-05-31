package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.service.PatientRecordService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrMobileRoutes}.
 *
 * <p>Verifies that the mobile dashboard endpoint:
 * <ul>
 *   <li>Returns 200 with a valid context for any authenticated role.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Mobile routes enforcement matrix: verifies session-header enforcement on the mobile dashboard API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrMobileRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrMobileRoutesTest extends EventloopTestBase {

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private DocumentService documentService;

    @Mock
    private DurablePhrNotificationSender notificationSender;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrMobileRoutes(
            eventloop(),
            patientRecordService,
            consentService,
            documentService,
            notificationSender
        ).getServlet();

        PatientRecordService.Patient patient = PatientRecordService.Patient.builder()
            .id("patient-1")
            .nationalId("NP-1")
            .demographics(new PatientRecordService.Demographics(
                "Test",
                "Patient",
                "1990-01-01",
                "male",
                new PatientRecordService.Address("Ward 1", "Kathmandu", "Kathmandu", "Bagmati", "44600"),
                new PatientRecordService.Contact("9800000000", "patient@example.com", "Guardian", "9811111111")
            ))
            .medicalHistory(new PatientRecordService.MedicalHistory(List.of(), List.of(), List.of(), "O+"))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .deleted(false)
            .build();

        lenient().when(patientRecordService.getPatient(anyString()))
            .thenReturn(Promise.of(Optional.of(patient)));
        lenient().when(documentService.getPatientDocuments(anyString(), anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(consentService.getPatientGrants(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(notificationSender.getPendingNotifications(anyString(), anyInt()))
            .thenReturn(Promise.of(List.of()));
    }

    @Test
    @DisplayName("200 — patient with valid context receives mobile dashboard")
    void patientReceivesMobileDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/dashboard", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 — caregiver with valid context is denied mobile dashboard")
    void caregiverReceivesForbidden() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/dashboard", "t1", "cg-1", "caregiver");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("400 — missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/dashboard").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
