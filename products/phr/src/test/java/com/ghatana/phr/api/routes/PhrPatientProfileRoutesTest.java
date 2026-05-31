package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrPatientProfileRoutes}.
 *
 * <p>Verifies that the patient profile endpoint:
 * <ul>
 *   <li>Returns 200 with a valid context for any authenticated role.</li>
 *   <li>Returns 400 when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Patient profile enforcement matrix: verifies context handling for the profile API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrPatientProfileRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrPatientProfileRoutesTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private AsyncServlet servlet;
    private AsyncServlet settingsServlet;

    @Mock
    private PatientRecordService patientRecordService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    @BeforeEach
    void setUp() {
        PatientRecordService.Demographics demographics = new PatientRecordService.Demographics(
            "Patient",
            "One",
            "1990-01-01",
            "male",
            new PatientRecordService.Address("Ward 1", "Lalitpur", "Lalitpur", "Bagmati", "44700"),
            new PatientRecordService.Contact("977-1234567890", "patient@example.com", "977-1234567890", ""),
            "en",
            "facility-1"
        );

        PatientRecordService.Patient mockPatient = new PatientRecordService.Patient(
            "patient-1",
            "NP-123456",
            demographics,
            new PatientRecordService.MedicalHistory(java.util.List.of(), java.util.List.of(), java.util.List.of(), "O+"),
            Instant.now(),
            Instant.now(),
            false
        );

        lenient().when(patientRecordService.getPatient(anyString())).thenReturn(Promise.of(Optional.of(mockPatient)));
        lenient().when(patientRecordService.updatePatient(any(PatientRecordService.Patient.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(0)));
        lenient().when(policyEvaluator.evaluateByPolicyId(anyString(), any(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));
        lenient().when(policyEvaluator.canAccessPhiResourceAsync(any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOWED", "allowed")));

        PhrPatientProfileRoutes routes = new PhrPatientProfileRoutes(eventloop(), patientRecordService, policyEvaluator);
        servlet = routes.getServlet();
        settingsServlet = routes.getSettingsServlet();
    }

    @Test
    @DisplayName("200 — patient may read their profile")
    void patientMayReadProfile() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        JsonNode body = parseBody(response);
        assertThat(body.path("id").asText()).isEqualTo("patient-1");
        assertThat(body.path("emergencyContact").asText()).isEqualTo("977-1234567890");
        assertThat(body.path("preferredLanguage").asText()).isEqualTo("en");
        assertThat(body.path("bloodType").asText()).isEqualTo("O+");
        assertThat(body.path("fieldClassification").path("nationalId").asText()).isEqualTo("direct_identifier");
        verify(policyEvaluator).evaluateByPolicyId(eq("phr.profile.access"), any(), eq("patient-1"), any());
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("profile-settings"), eq("READ"), eq("t1"), any());
    }

    @Test
    @DisplayName("200 — clinician may read a patient's profile")
    void clinicianMayReadProfile() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/?patientId=patient-1", "t1", "dr-1", "clinician");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(patientRecordService).getPatient("patient-1");
    }

    @Test
    @DisplayName("200 — admin may read a patient's profile")
    void adminMayReadProfile() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/?patientId=patient-1", "t1", "admin-1", "admin");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("403 — policy denial blocks cross-patient profile read")
    void policyDenialBlocksCrossPatientRead() throws Exception {
        lenient().when(policyEvaluator.evaluateByPolicyId(eq("phr.profile.access"), any(), eq("patient-2"), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("TEST_DENIED", "denied")));
        HttpRequest request = contextRequest(HttpMethod.GET, "/?patientId=patient-2", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(patientRecordService, never()).getPatient("patient-2");
    }

    @Test
    @DisplayName("200 — patient update persists classified editable fields")
    void patientUpdatePersistsEditableFields() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.PUT, "/", "t1", "patient-1", "patient",
            """
            {"emergencyContact":"+977-9800000002","preferredLanguage":"ne","facilityId":"facility-2"}
            """);

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        JsonNode body = parseBody(response);
        assertThat(body.path("emergencyContact").asText()).isEqualTo("+977-9800000002");
        assertThat(body.path("preferredLanguage").asText()).isEqualTo("ne");
        assertThat(body.path("facilityId").asText()).isEqualTo("facility-2");
        assertThat(body.path("updatedFields").get(0).asText()).isEqualTo("emergencyContact");
        assertThat(body.path("updatedFields").get(1).asText()).isEqualTo("preferredLanguage");
        assertThat(body.path("updatedFields").get(2).asText()).isEqualTo("facilityId");
        verify(policyEvaluator).canAccessPhiResourceAsync(
            any(), eq("patient-1"), eq("profile-settings"), eq("WRITE"), eq("t1"), any());
        verify(patientRecordService).updatePatient(any(PatientRecordService.Patient.class));
    }

    @Test
    @DisplayName("403 — disallowed field update is rejected before persistence")
    void disallowedFieldUpdateRejected() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.PUT, "/", "t1", "patient-1", "patient",
            "{\"bloodType\":\"A+\"}");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        verify(patientRecordService, never()).updatePatient(any());
    }

    @Test
    @DisplayName("400 — invalid setting value is rejected")
    void invalidSettingValueRejected() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.PUT, "/", "t1", "patient-1", "patient",
            "{\"preferredLanguage\":\"fr\"}");

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        verify(patientRecordService, never()).updatePatient(any());
    }

    @Test
    @DisplayName("200 — settings mount evaluates settings policy")
    void settingsMountUsesSettingsPolicy() throws Exception {
        HttpRequest request = contextRequest(HttpMethod.GET, "/", "t1", "patient-1", "patient");

        HttpResponse response = runPromise(() -> settingsServlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(policyEvaluator).evaluateByPolicyId(eq("phr.settings.access"), any(), eq("patient-1"), any());
    }

    @Test
    @DisplayName("400 — missing context headers returns 400")
    void returns400WhenContextMissing() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return contextRequest(method, path, tenantId, principalId, role, null);
    }

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1");
        if (body != null) {
            builder.withBody(ByteBuf.wrapForReading(body.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
    }
}
