package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrClinicalRoutes}.
 *
 * <p>Verifies that clinical endpoints enforce resource/action-specific policy.
 *
 * @doc.type class
 * @doc.purpose Clinical routes enforcement matrix: verifies resource/action policy access for clinical APIs
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrClinicalRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrClinicalRoutesTest extends EventloopTestBase {

    @Mock
    private LabResultService labResultService;

    @Mock
    private MedicationService medicationService;

    @Mock
    private ImmunizationService immunizationService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrClinicalRoutes(
            eventloop(), labResultService, medicationService, immunizationService, consentService, policyEvaluator
        ).getServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")));
        lenient().when(labResultService.getPatientObservations(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(labResultService.getObservation(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(labResultService.getTrend(anyString(), anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(medicationService.getActivePrescriptions(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(medicationService.getPrescription(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(medicationService.checkDrugInteractions(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(immunizationService.getImmunizationHistory(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(immunizationService.getImmunization(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(immunizationService.getDueSchedules(anyString()))
            .thenReturn(Promise.of(List.of()));
    }

    @Nested
    @DisplayName("GET /labs - list lab observations")
    class ListLabs {

        @Test
        @DisplayName("200 - patient may list their own lab observations")
        void patientMayListOwnLabs() throws Exception {
            lenient().when(labResultService.getPatientObservations("patient-1"))
                .thenReturn(Promise.of(List.of(labObservation())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            String body = body(response);
            assertThat(body).contains(
                "\"category\":\"laboratory\"",
                "\"referenceRange\":\"0.6-1.2\"",
                "\"abnormal\":true",
                "\"status\":\"attention\"",
                "\"system\":\"lab-result-service\"",
                "\"resourceType\":\"Observation\""
            );
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("lab-results"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("200 - clinician with policy access may list patient labs")
        void clinicianWithPolicyMayListLabs() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/?patientId=patient-1", "t1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/labs/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("200 - lab detail echoes request correlation id")
        void labDetailEchoesCorrelationId() throws Exception {
            lenient().when(labResultService.getObservation("obs-1"))
                .thenReturn(Promise.of(Optional.of(labObservation())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/labs/observations/obs-1?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }
    }

    @Nested
    @DisplayName("GET /medications - list active prescriptions")
    class ListMedications {

        @Test
        @DisplayName("200 - patient may list their own medications")
        void patientMayListOwnMedications() throws Exception {
            lenient().when(medicationService.getActivePrescriptions("patient-1"))
                .thenReturn(Promise.of(List.of(prescription())));
            lenient().when(medicationService.checkDrugInteractions("patient-1"))
                .thenReturn(Promise.of(List.of(interaction())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/medications/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"medication\":\"Warfarin\"",
                "\"route\":null",
                "\"routeSource\":\"not-collected\"",
                "\"frequency\":\"daily\"",
                "\"prescriberId\":\"clinician-1\"",
                "\"startDate\":\"2026-01-01T00:00:00Z\"",
                "\"endDate\":\"2026-02-01T00:00:00Z\"",
                "\"refillsRemaining\":2",
                "\"adherenceStatus\":{",
                "\"measured\":false",
                "\"source\":\"not-collected\"",
                "\"interactions\":[\"Increased bleeding risk\"]",
                "\"warnings\":[\"HIGH: Monitor INR\"]",
                "\"resourceType\":\"MedicationRequest\""
            ).doesNotContain("\"adherenceSource\"");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("medications"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/medications/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }

        @Test
        @DisplayName("200 - medication detail returns complete DTO")
        void medicationDetailReturnsCompleteDto() throws Exception {
            lenient().when(medicationService.getPrescription("rx-1"))
                .thenReturn(Promise.of(Optional.of(prescription())));
            lenient().when(medicationService.checkDrugInteractions("patient-1"))
                .thenReturn(Promise.of(List.of(interaction())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/medications/prescriptions/rx-1?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            assertThat(body(response)).contains(
                "\"medicationName\":\"Warfarin\"",
                "\"dosage\":\"5 mg\"",
                "\"status\":\"active\"",
                "\"fhir\":{\"resourceType\":\"MedicationRequest\""
            );
        }
    }

    @Nested
    @DisplayName("GET /immunizations - list immunization history")
    class ListImmunizations {

        @Test
        @DisplayName("200 - patient may list their own immunizations")
        void patientMayListOwnImmunizations() throws Exception {
            lenient().when(immunizationService.getImmunizationHistory("patient-1"))
                .thenReturn(Promise.of(List.of(immunization())));
            lenient().when(immunizationService.getDueSchedules("patient-1"))
                .thenReturn(Promise.of(List.of(nextDueSchedule())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/immunizations/?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            String responseBody = body(response);
            assertThat(responseBody).contains(
                "\"vaccine\":\"MMR\"",
                "\"vaccineName\":\"MMR\"",
                "\"date\":\"2026-01-15T00:00:00Z\"",
                "\"occurrenceDate\":\"2026-01-15T00:00:00Z\"",
                "\"dose\":\"1\"",
                "\"doseNumber\":1",
                "\"lotNumber\":\"LOT-42\"",
                "\"cvxCode\":\"03\"",
                "\"route\":\"IM\"",
                "\"seriesName\":\"MMR series\"",
                "\"status\":\"completed\"",
                "\"system\":\"immunization-service\"",
                "\"administeredBy\":\"clinician-1\"",
                "\"nextDue\":{\"id\":\"sched-1\"",
                "\"id\":\"sched-1\"",
                "\"status\":\"due\"",
                "\"system\":\"immunization-schedule-service\"",
                "\"dueDate\":\"2026-07-15T00:00:00Z\"",
                "\"resourceType\":\"Immunization\""
            );
            assertThat(responseBody).doesNotContain("\"administeredBy\":\"unknown\"");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("immunizations"), eq("READ"), eq("t1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/immunizations/", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }

        @Test
        @DisplayName("200 - immunization detail returns complete DTO")
        void immunizationDetailReturnsCompleteDto() throws Exception {
            lenient().when(immunizationService.getImmunization("imm-1"))
                .thenReturn(Promise.of(Optional.of(immunization())));
            lenient().when(immunizationService.getDueSchedules("patient-1"))
                .thenReturn(Promise.of(List.of(nextDueSchedule())));
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/immunizations/imm-1?patientId=patient-1", "t1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            assertThat(body(response)).contains(
                "\"id\":\"imm-1\"",
                "\"vaccine\":\"MMR\"",
                "\"status\":\"completed\"",
                "\"nextDue\":{\"id\":\"sched-1\"",
                "\"fhir\":{\"resourceType\":\"Immunization\""
            );
        }
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
        return new String(runPromise(response::loadBody).asArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static LabResultService.LabObservation labObservation() {
        return new LabResultService.LabObservation(
            "obs-1",
            "patient-1",
            "enc-1",
            "order-1",
            "2160-0",
            "Creatinine",
            "Creatinine",
            new BigDecimal("1.8"),
            0.6,
            "mg/dL",
            "0.6-1.2",
            "lab-1",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"),
            LabResultService.ObservationStatus.FINAL,
            null,
            "H"
        );
    }

    private static MedicationService.Prescription prescription() {
        return new MedicationService.Prescription(
            "rx-1",
            "patient-1",
            "clinician-1",
            "enc-1",
            "B01AA03",
            "Warfarin",
            "5 mg",
            "stroke prevention",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-02-01T00:00:00Z"),
            2,
            Duration.ofDays(1),
            MedicationService.PrescriptionStatus.ACTIVE
        );
    }

    private static MedicationService.InteractionWarning interaction() {
        return new MedicationService.InteractionWarning(
            "B01AA03",
            "M01A",
            MedicationService.InteractionSeverity.HIGH,
            "Increased bleeding risk",
            "Monitor INR"
        );
    }

    private static ImmunizationService.ImmunizationRecord immunization() {
        return new ImmunizationService.ImmunizationRecord(
            "imm-1",
            "patient-1",
            "enc-1",
            "03",
            "MMR",
            "clinician-1",
            Instant.parse("2026-01-15T00:00:00Z"),
            Instant.parse("2026-01-16T00:00:00Z"),
            "LOT-42",
            LocalDate.parse("2027-01-15"),
            "IM",
            "MMR series",
            1,
            false,
            "No adverse event",
            ImmunizationService.ImmunizationStatus.ADMINISTERED
        );
    }

    private static ImmunizationService.VaccinationSchedule nextDueSchedule() {
        return new ImmunizationService.VaccinationSchedule(
            "sched-1",
            "patient-1",
            "03",
            "MMR",
            "MMR series",
            2,
            Instant.parse("2026-07-15T00:00:00Z"),
            ImmunizationService.ScheduleStatus.PENDING,
            "Second dose"
        );
    }
}
