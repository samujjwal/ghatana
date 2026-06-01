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
        lenient().when(documentService.getOcrDocument(anyString(), anyString()))
            .thenReturn(Promise.of(Optional.of(ocrDocument("PENDING_REVIEW"))));
        lenient().when(documentService.confirmOcrDocument(anyString(), anyString(), nullable(String.class), any()))
            .thenReturn(Promise.of(ocrDocument("CONFIRMED")));
        lenient().when(documentService.rejectOcrDocument(anyString(), anyString(), any()))
            .thenReturn(Promise.of(ocrDocument("REJECTED")));
        lenient().when(documentService.toFhirDocumentReference(anyString()))
            .thenReturn(Promise.of("{\"resourceType\":\"DocumentReference\",\"id\":\"doc-1\"}"));
        lenient().when(documentService.uploadDocument(any()))
            .thenReturn(Promise.of(patientDocument()));
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
    @DisplayName("OCR review lifecycle")
    class OcrReviewLifecycle {

        @Test
        @DisplayName("200 - OCR fetch returns web contract shape with normalized status")
        void ocrFetchReturnsContractShape() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/doc-1/ocr", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"id\":\"doc-1\"",
                "\"documentId\":\"doc-1\"",
                "\"ocrText\":\"Extracted OCR text\"",
                "\"extractedText\":\"Extracted OCR text\"",
                "\"status\":\"pending_review\""
            );
        }

        @Test
        @DisplayName("200 - OCR confirm accepts empty body and returns normalized review DTO")
        void ocrConfirmAcceptsEmptyBody() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/doc-1/ocr/confirm",
                "tenant-1",
                "patient-1",
                "patient",
                ""
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"id\":\"doc-1\"",
                "\"confirmed\":true",
                "\"status\":\"confirmed\"",
                "\"fhirResource\":\"{\\\"resourceType\\\":\\\"DocumentReference\\\",\\\"id\\\":\\\"doc-1\\\"}\""
            );
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("ocr-review-scope"), eq("ocr-document-review"), eq("WRITE"), eq("tenant-1"), nullable(String.class));
        }

        @Test
        @DisplayName("403 - OCR reject respects route policy before mutation")
        void ocrRejectRespectsRoutePolicy() throws Exception {
            lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("ocr-review-scope"), eq("ocr-document-review"), eq("WRITE"), eq("tenant-1"), nullable(String.class)))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied("OCR_REVIEW_DENIED", "denied")));
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/doc-1/ocr/reject",
                "tenant-1",
                "caregiver-1",
                "caregiver",
                ""
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(body(response)).contains("OCR_REVIEW_DENIED");
        }

        @Test
        @DisplayName("200 - OCR reject returns normalized review DTO and reject marker")
        void ocrRejectReturnsNormalizedDto() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/doc-1/ocr/reject",
                "tenant-1",
                "patient-1",
                "patient",
                ""
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(body(response)).contains(
                "\"id\":\"doc-1\"",
                "\"documentId\":\"doc-1\"",
                "\"rejected\":true",
                "\"status\":\"rejected\""
            );
        }
    }

    @Nested
    @DisplayName("POST /documents - upload policy")
    class UploadDocument {

        @Test
        @DisplayName("201 - upload requires policy, clean malware scan, provenance, and patient write access")
        void uploadRequiresPolicyAndCleanScan() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/",
                "tenant-1",
                "patient-1",
                "patient",
                documentUploadBody()
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            String body = body(response);
            assertThat(body).contains(
                "\"storagePolicy\":{\"residency\":\"NP\"",
                "\"provenance\":{\"source\":\"patient-upload\"",
                "\"malwareScan\":{\"status\":\"clean\""
            );
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("documents"), eq("WRITE"), eq("tenant-1"), nullable(String.class));
            verify(documentService).uploadDocument(any());
        }

        @Test
        @DisplayName("400 - upload without storage policy is rejected before service call")
        void uploadWithoutStoragePolicyRejected() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/",
                "tenant-1",
                "patient-1",
                "patient",
                documentUploadBodyWithoutStoragePolicy()
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(body(response)).contains("storagePolicy is required");
        }

        @Test
        @DisplayName("400 - upload with non-clean malware scan is rejected")
        void uploadWithNonCleanScanRejected() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST,
                "/documents/",
                "tenant-1",
                "patient-1",
                "patient",
                documentUploadBody().replace("\"status\": \"clean\"", "\"status\": \"infected\"")
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(body(response)).contains("malwareScan.status must be clean");
        }
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
            HttpRequest request = HttpRequest.get("http://localhost/documents/?patientId=patient-1")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
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
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }

    private static String documentUploadBody() {
        return """
            {
              "patientId": "patient-1",
              "documentType": "LAB_REPORT",
              "title": "Lab report",
              "description": "Uploaded lab report",
              "contentType": "application/pdf",
              "content": "SGVsbG8=",
              "contentHash": "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969",
              "visibility": "shared-with-provider",
              "storagePolicy": {
                "residency": "NP",
                "retention": "25years",
                "encryption": "managed-kms"
              },
              "provenance": {
                "source": "patient-upload",
                "uploadedBy": "patient-1",
                "patientId": "patient-1"
              },
              "malwareScan": {
                "status": "clean",
                "engine": "clamav",
                "scannedAt": "2026-01-01T00:00:00Z"
              }
            }
            """;
    }

    private static String documentUploadBodyWithoutStoragePolicy() {
        return """
            {
              "patientId": "patient-1",
              "documentType": "LAB_REPORT",
              "title": "Lab report",
              "description": "Uploaded lab report",
              "contentType": "application/pdf",
              "content": "SGVsbG8=",
              "contentHash": "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969",
              "visibility": "shared-with-provider",
              "provenance": {
                "source": "patient-upload",
                "uploadedBy": "patient-1",
                "patientId": "patient-1"
              },
              "malwareScan": {
                "status": "clean",
                "engine": "clamav",
                "scannedAt": "2026-01-01T00:00:00Z"
              }
            }
            """;
    }

    private static DocumentService.PatientDocument patientDocument() {
        return new DocumentService.PatientDocument(
            "doc-1",
            "patient-1",
            "LAB_REPORT",
            "Lab report",
            "Uploaded lab report",
            Instant.parse("2026-01-01T00:00:00Z"),
            "application/pdf",
            5,
            "content-1",
            new DocumentService.DocumentConsent("shared-with-provider", Instant.parse("2026-01-01T00:00:00Z")),
            new DocumentService.DocumentStoragePolicy("NP", "25years", "managed-kms"),
            new DocumentService.UploadProvenance("patient-upload", "patient-1", "patient-1"),
            new DocumentService.MalwareScanAttestation("clean", "clamav", Instant.parse("2026-01-01T00:00:00Z")),
            "PENDING_REVIEW",
            null,
            null,
            null,
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"),
            false
        );
    }

    private static DocumentService.OcrDocument ocrDocument(String status) {
        return new DocumentService.OcrDocument(
            "doc-1",
            "Lab report",
            status,
            0.91,
            "Extracted OCR text",
            "patient-1",
            Instant.parse("2026-01-02T00:00:00Z")
        );
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
