package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ConsentManagementServiceExtensions;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DocumentServiceExtensions;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessLogServiceExtensions;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.MedicationServiceExtensions;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PatientRecordServiceExtensions;
import com.ghatana.phr.repository.UserRepository;
import com.ghatana.phr.model.PHRUser;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.List;

/**
 * Enforcement matrix tests for {@link PhrDashboardRoutes}.
 *
 * <p>Verifies that the dashboard endpoint:
 * <ul>
 *   <li>Returns 200 with a valid context for any authenticated role.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Dashboard enforcement matrix: verifies request context handling for the dashboard API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrDashboardRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrDashboardRoutesTest extends EventloopTestBase {

    private AsyncServlet servlet;
    private UserRepository userRepository;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private MedicationService medicationService;

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private DocumentService documentService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private EmergencyAccessLogService emergencyAccessLogService;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        userRepository.save(new PHRUser("patient-1", "Patient One", "patient@example.test"));
        userRepository.save(new PHRUser("dr-1", "Doctor One", "doctor@example.test"));
        userRepository.save(new PHRUser("admin-1", "Admin One", "admin@example.test"));

        // Create extension instances with mocked services
        // Their default implementations return empty data, which is sufficient for testing
        MedicationServiceExtensions medicationExtensions = new MedicationServiceExtensions(medicationService);
        PatientRecordServiceExtensions patientRecordExtensions = new PatientRecordServiceExtensions(patientRecordService);
        DocumentServiceExtensions documentExtensions = new DocumentServiceExtensions(documentService);
        ConsentManagementServiceExtensions consentExtensions = new ConsentManagementServiceExtensions(consentService);
        EmergencyAccessLogServiceExtensions emergencyExtensions = new EmergencyAccessLogServiceExtensions(emergencyAccessLogService);

        // Mock service methods that extensions call
        // Create a mock appointment with necessary fields
        AppointmentService.Appointment mockAppointment = new AppointmentService.Appointment(
            "apt-1",
            "patient-1",
            "provider-1",
            "slot-1",
            java.time.Instant.now(),
            30,
            "checkup",
            "scheduled",
            "IN_PERSON",
            java.time.Instant.now(),
            java.time.Instant.now(),
            1
        );
        lenient().when(appointmentService.getNextAppointment(anyString())).thenReturn(Promise.of(mockAppointment));
        lenient().when(medicationService.getActivePrescriptions(anyString())).thenReturn(Promise.of(List.of()));

        servlet = new PhrDashboardRoutes(
            eventloop(),
            userRepository,
            appointmentService,
            medicationExtensions,
            patientRecordExtensions,
            documentExtensions,
            consentExtensions,
            emergencyExtensions
        ).getServlet();
    }

    @Test
    @DisplayName("200 — patient with valid context receives dashboard summary")
    void patientReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — clinician with valid context receives dashboard summary")
    void clinicianReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("200 — admin with valid context receives dashboard summary")
    void adminReceivesDashboard() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("400 — missing context headers")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }


    @Test
    @DisplayName("G11-011: X-Correlation-ID in request is echoed in the response header")
    void correlationIdIsPropagatedToResponse() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "trace-propagation-test-001")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID")))
            .isEqualTo("trace-propagation-test-001");
    }
    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}
