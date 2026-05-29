package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.ImagingService;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrDocumentImagingRoutes}.
 *
 * <p>Verifies that document and imaging endpoints enforce resource/action-specific policy.
 *
 * @doc.type class
 * @doc.purpose Document imaging routes enforcement matrix: verifies resource/action policy access
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrDocumentImagingRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrDocumentImagingRoutesTest extends EventloopTestBase {

    @Mock
    private DocumentService documentService;

    @Mock
    private ImagingService imagingService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrDocumentImagingRoutes(
            eventloop(), documentService, imagingService, consentService, policyEvaluator
        ).getServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("TEST_ALLOW", "Allowed by test policy")));
        lenient().when(documentService.getPatientDocuments(anyString(), anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(documentService.getDocument(anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(imagingService.getPatientOrders(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(imagingService.getPatientStudies(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(imagingService.createOrder(any()))
            .thenReturn(Promise.of(new ImagingService.ImagingOrder(
                "ord-1",
                "patient-1",
                null,
                "dr-1",
                "XR",
                "CHEST",
                "cough",
                ImagingService.OrderStatus.REQUESTED,
                Instant.parse("2026-01-01T00:00:00Z"),
                null
            )));
    }

    @Nested
    @DisplayName("GET /documents - list documents")
    class ListDocuments {

        @Test
        @DisplayName("200 - patient may list their own documents")
        void patientMayListOwnDocuments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("documents"), eq("READ"), eq("tenant-1"), nullable(String.class));
        }

        @Test
        @DisplayName("200 - clinician with policy access may list patient documents")
        void clinicianWithPolicyMayListDocuments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/documents/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /imaging/orders - create imaging order")
    class CreateImagingOrder {

        @Test
        @DisplayName("201 - imaging order uses write policy")
        void imagingOrderUsesWritePolicy() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/imaging/orders",
                "tenant-1",
                "patient-1",
                "patient",
                imagingOrderBody()
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("imaging"), eq("WRITE"), eq("tenant-1"), nullable(String.class));
        }
    }

    @Nested
    @DisplayName("GET /imaging - list imaging studies")
    class ListImagingStudies {

        @Test
        @DisplayName("200 - patient may list their own imaging studies")
        void patientMayListOwnStudies() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/imaging/studies?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("imaging"), eq("READ"), eq("tenant-1"), nullable(String.class));
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/imaging/studies", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
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

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static String imagingOrderBody() {
        return """
            {
              "patientId": "patient-1",
              "orderingProviderId": "dr-1",
              "modalityCode": "XR",
              "bodyPart": "CHEST",
              "clinicalIndication": "cough",
              "status": "REQUESTED",
              "orderedAt": "2026-01-01T00:00:00Z"
            }
            """;
    }
}
