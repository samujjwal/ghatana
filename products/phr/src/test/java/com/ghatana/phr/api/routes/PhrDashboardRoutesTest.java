package com.ghatana.phr.api.routes;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.ConsentManagementServiceExtensions;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.DocumentServiceExtensions;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessLogServiceExtensions;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.MedicationServiceExtensions;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PatientRecordServiceExtensions;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.phr.repository.UserRepository;
import com.ghatana.phr.model.PHRUser;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.Optional;
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
    private TreatmentRelationshipService treatmentRelationshipService;

    @Mock
    private FchvCommunityAssignmentService fchvCommunityAssignmentService;

    @Mock
    private EmergencyAccessLogService emergencyAccessLogService;

    @Mock
    private AuditTrailService auditTrailService;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        userRepository.save(new PHRUser("patient-1", "Patient One", "patient@example.test"));
        userRepository.save(new PHRUser("dr-1", "Doctor One", "doctor@example.test"));
        userRepository.save(new PHRUser("admin-1", "Admin One", "admin@example.test"));

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
        lenient().when(patientRecordService.getPatient(anyString())).thenReturn(Promise.of(Optional.of(
            PatientRecordService.Patient.builder()
                .id("patient-1")
                .medicalHistory(new PatientRecordService.MedicalHistory(
                    List.of("Type 2 diabetes mellitus"),
                    List.of(),
                    List.of(),
                    "O+"
                ))
                .build()
        )));
        lenient().when(documentService.getPatientDocuments(anyString(), anyString())).thenReturn(Promise.of(List.of()));
        lenient().when(consentService.getPatientGrants(anyString())).thenReturn(Promise.of(List.of()));
        lenient().when(emergencyAccessLogService.getPatientEmergencyLog(anyString())).thenReturn(Promise.of(List.of()));
        lenient().when(treatmentRelationshipService.hasActiveTreatmentRelationship(anyString(), anyString()))
            .thenReturn(Promise.of(false));

        servlet = new PhrDashboardRoutes(
            eventloop(),
            userRepository,
            appointmentService,
            medicationExtensions,
            patientRecordExtensions,
            documentExtensions,
            consentExtensions,
            emergencyExtensions,
            new PhrPolicyEvaluator(
                consentService,
                treatmentRelationshipService,
                fchvCommunityAssignmentService,
                auditTrailService
            )
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
    @DisplayName("200 — clinician with treatment relationship receives patient dashboard summary")
    void clinicianReceivesDashboardWithTreatmentRelationship() throws Exception {
        lenient().when(treatmentRelationshipService.hasActiveTreatmentRelationship("dr-1", "patient-1"))
            .thenReturn(Promise.of(true));
        HttpRequest request = contextRequest(
            HttpMethod.GET,
            "/?patientId=patient-1",
            "tenant-1",
            "dr-1",
            "clinician",
            "facility-1"
        );

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(appointmentService).getNextAppointment("patient-1");
    }

    @Test
    @DisplayName("403 — clinician without treatment relationship or consent cannot view dashboard")
    void clinicianWithoutScopeCannotViewDashboard() throws Exception {
        lenient().when(consentService.validateAccess("patient-1", "dr-1", "dashboard", "READ"))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(false, "No grant", null)));
        HttpRequest request = contextRequest(
            HttpMethod.GET,
            "/?patientId=patient-1",
            "tenant-1",
            "dr-1",
            "clinician",
            "facility-1"
        );

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("403 — admin dashboard PHI access requires explicit justification path")
    void adminDashboardRequiresJustificationPath() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/?patientId=patient-1", "tenant-1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("400 — missing context headers")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }


    @Test
    @DisplayName("G11-011: X-Correlation-ID in request is echoed in the response header")
    void correlationIdIsPropagatedToResponse() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Persona"), "patient")
            .withHeader(HttpHeaders.of("X-Tier"), "core")
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
        return contextRequest(method, path, tenantId, principalId, role, null);
    }

    private static HttpRequest contextRequest(
            HttpMethod method,
            String path,
            String tenantId,
            String principalId,
            String role,
            String facilityId) {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1");
        if (facilityId != null) {
            builder.withHeader(HttpHeaders.of("X-Facility-ID"), facilityId);
        }
        return builder.build();
    }
}
