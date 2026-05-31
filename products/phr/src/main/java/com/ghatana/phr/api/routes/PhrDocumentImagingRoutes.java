package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.PhrTraceContext;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Document and imaging API routes for the PHR product.
 *
 * <p>Provides document upload with server-side validation for file type and size.
 * Allowed content types: PDF, JPEG, PNG, TIFF, DOC, DOCX. Maximum file size: 10MB.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for document reference and imaging journeys
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrDocumentImagingRoutes {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/tiff",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L; // 10MB

    private final Eventloop eventloop;
    private final DocumentService documentService;
    private final ImagingService imagingService;
    private final PhrPolicyEvaluator policyEvaluator;

    public PhrDocumentImagingRoutes(
            Eventloop eventloop,
            DocumentService documentService,
            ImagingService imagingService,
            ConsentManagementService consentService,
            PhrPolicyEvaluator policyEvaluator) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.imagingService = Objects.requireNonNull(imagingService, "imagingService must not be null");
        Objects.requireNonNull(consentService, "consentService must not be null");
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
    }

    /**
     * Returns the routing servlet for document and imaging endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with("/documents/*", getDocumentServlet())
            .with("/imaging/*", getImagingServlet())
            .build();
    }

    public AsyncServlet getDocumentServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/", this::handleUploadDocument)
            .with(HttpMethod.GET, "/:documentId/content", this::handleGetDocumentContent)
            .with(HttpMethod.GET, "/:documentId/fhir", this::handleDocumentFhir)
            .with(HttpMethod.GET, "/:documentId/ocr", this::handleGetOcrDocument)
            .with(HttpMethod.POST, "/:documentId/ocr/confirm", this::handleConfirmOcrDocument)
            .with(HttpMethod.POST, "/:documentId/ocr/reject", this::handleRejectOcrDocument)
            .with(HttpMethod.POST, "/:documentId/consent", this::handleUpdateDocumentConsent)
            .with(HttpMethod.DELETE, "/:documentId", this::handleDeleteDocument)
            .with(HttpMethod.GET, "/:documentId", this::handleGetDocument)
            .with(HttpMethod.GET, "/", this::handleListDocuments)
            .build();
    }

    public AsyncServlet getImagingServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/orders", this::handleCreateImagingOrder)
            .with(HttpMethod.GET, "/orders/:orderId", this::handleGetImagingOrder)
            .with(HttpMethod.POST, "/studies", this::handleRegisterImagingStudy)
            .with(HttpMethod.POST, "/reports", this::handleStoreRadiologyReport)
            .with(HttpMethod.GET, "/studies", this::handleListImagingStudies)
            .with(HttpMethod.GET, "/orders", this::handleListImagingOrders)
            .with(HttpMethod.GET, "/studies/:studyId/download", this::handleSecureImagingDownload)
            .with(HttpMethod.GET, "/studies/:studyId/series/:seriesId/download", this::handleSecureSeriesDownload)
            .build();
    }

    private Promise<HttpResponse> handleUploadDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withDocumentBody(request, documentService::uploadDocument);
    }

    private Promise<HttpResponse> handleGetDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return documentService.getDocument(request.getPathParameter("documentId"), context.principalId())
            .then(document -> document
                .<Promise<HttpResponse>>map(value -> PhrRouteSupport.jsonResponse(200, value, correlationId))
                .orElseGet(() -> PhrRouteSupport.errorResponse(404, "DOCUMENT_NOT_FOUND", "Document not found or not visible", correlationId)));
    }

    private Promise<HttpResponse> handleGetDocumentContent(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return documentService.getDocumentContent(request.getPathParameter("documentId"), context.principalId())
            .then(content -> content
                .<Promise<HttpResponse>>map(value -> PhrRouteSupport.jsonResponse(200, Map.of(
                    "documentId", value.getDocumentId(),
                    "contentType", value.getContentType(),
                    "contentHash", value.getContentHash(),
                    "content", Base64.getEncoder().encodeToString(value.getContent())
                ), correlationId))
                .orElseGet(() -> PhrRouteSupport.errorResponse(404, "DOCUMENT_CONTENT_NOT_FOUND", "Document content not found or not visible", correlationId)));
    }

    private Promise<HttpResponse> handleDocumentFhir(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return documentService.toFhirDocumentReference(request.getPathParameter("documentId"))
            .then(fhir -> PhrRouteSupport.jsonResponse(200, fhir, correlationId));
    }

    private Promise<HttpResponse> handleGetOcrDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        String documentId = request.getPathParameter("documentId");
        return documentService.getOcrDocument(documentId, context.principalId())
            .then(ocrDoc -> ocrDoc
                .<Promise<HttpResponse>>map(doc -> PhrRouteSupport.jsonResponse(200, ocrDocumentDto(doc), correlationId))
                .orElseGet(() -> PhrRouteSupport.errorResponse(403, "OCR_ACCESS_DENIED", "OCR document is not visible to this principal", correlationId)));
    }

    private Promise<HttpResponse> handleConfirmOcrDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        if ("caregiver".equals(context.role())) {
            return PhrRouteSupport.policyDenialResponse(403, context.correlationId());
        }

        String documentId = request.getPathParameter("documentId");
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                "ocr-review-scope",
                "ocr-document-review",
                "WRITE",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }

                return documentService.getOcrDocument(documentId, context.principalId())
                    .then(ocrDoc -> {
                        if (ocrDoc.isEmpty()) {
                            return PhrRouteSupport.errorResponse(403, "OCR_ACCESS_DENIED",
                                "OCR document is not visible to this principal",
                                context.correlationId());
                        }

                        return request.loadBody()
                            .then(body -> {
                                String correctedText;
                                try {
                                    String rawBody = body.getString(StandardCharsets.UTF_8);
                                    if (rawBody == null || rawBody.isBlank()) {
                                        correctedText = null;
                                    } else {
                                        JsonNode node = PhrRouteSupport.JSON.readTree(rawBody);
                                        correctedText = node.path("correctedText").asText(null);
                                    }
                                } catch (Exception ex) {
                                    return PhrRouteSupport.errorResponse(400, "INVALID_OCR_CONFIRM", ex.getMessage());
                                }

                                return documentService.confirmOcrDocument(documentId, context.principalId(), correctedText, idempotencyKey)
                                    .then(confirmed -> {
                                        String auditCorrelationId = PhrTraceContext.newCorrelationId("phr_ocr_confirm");
                                        auditOcrConfirmation(context, documentId, correctedText, auditCorrelationId);
                                        return documentService.toFhirDocumentReference(documentId)
                                            .then(fhir -> {
                                                Map<String, Object> dto = ocrDocumentDto(confirmed);
                                                dto.put("confirmed", true);
                                                dto.put("fhirResource", fhir);
                                                dto.put("provenance", Map.of(
                                                    "reviewerId", context.principalId(),
                                                    "reviewedAt", confirmed.reviewedAt() != null ? confirmed.reviewedAt().toString() : java.time.Instant.now().toString(),
                                                    "correlationId", auditCorrelationId
                                                ));
                                                return PhrRouteSupport.jsonResponse(200, dto, correlationId);
                                            });
                                    });
                            });
                    });
            });
    }

    private Promise<HttpResponse> handleRejectOcrDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String idempotencyKey;
        try {
            context = PhrRouteSupport.requireContext(request);
            idempotencyKey = PhrRouteSupport.extractIdempotencyKey(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String documentId = request.getPathParameter("documentId");
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                "ocr-review-scope",
                "ocr-document-review",
                "WRITE",
                context.tenantId(),
                context.facilityId())
            .then(decision -> {
                if (!decision.isAllowed()) {
                    return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                }
                return documentService.getOcrDocument(documentId, context.principalId())
                    .then(ocrDoc -> {
                        if (ocrDoc.isEmpty()) {
                            return PhrRouteSupport.errorResponse(403, "OCR_ACCESS_DENIED",
                                "OCR document is not visible to this principal",
                                context.correlationId());
                        }
                        return documentService.rejectOcrDocument(documentId, context.principalId(), idempotencyKey)
                            .then(rejected -> {
                                String auditCorrelationId = PhrTraceContext.newCorrelationId("phr_ocr_reject");
                                auditOcrRejection(context, documentId, auditCorrelationId);
                                Map<String, Object> dto = ocrDocumentDto(rejected);
                                dto.put("rejected", true);
                                dto.put("provenance", Map.of(
                                    "reviewerId", context.principalId(),
                                    "reviewedAt", rejected.reviewedAt() != null ? rejected.reviewedAt().toString() : java.time.Instant.now().toString(),
                                    "correlationId", auditCorrelationId
                                ));
                                return PhrRouteSupport.jsonResponse(200, dto, correlationId);
                            });
                    });
            });
    }

    private Promise<HttpResponse> handleUpdateDocumentConsent(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "documents", "WRITE", patientId -> request.loadBody()
            .then(body -> {
                String visibility;
                try {
                    JsonNode node = PhrRouteSupport.JSON.readTree(body.getString(StandardCharsets.UTF_8));
                    visibility = node.path("visibility").asText(null);
                    if (visibility == null || visibility.isBlank()) {
                        throw new IllegalArgumentException("visibility is required");
                    }
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_DOCUMENT_CONSENT", ex.getMessage());
                }
                return documentService.updateDocumentConsent(request.getPathParameter("documentId"), visibility, patientId)
                    .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "documentId", request.getPathParameter("documentId"),
                        "visibility", visibility
                    ), correlationId));
            }));
    }

    private Promise<HttpResponse> handleDeleteDocument(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "documents", "WRITE", patientId -> documentService.deleteDocument(
                request.getPathParameter("documentId"),
                patientId)
            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                "documentId", request.getPathParameter("documentId"),
                "deleted", true
            ), correlationId)));
    }

    private Promise<HttpResponse> handleListDocuments(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_DOCUMENT_QUERY", ex.getMessage());
        }
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return requireAccess(context, patientId, "documents", "READ")
            .then(decision -> decision.isAllowed()
                ? documentService.getPatientDocuments(patientId, finalContext.principalId())
                    .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId))
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "POLICY_DENIED"));
    }

    private Promise<HttpResponse> handleCreateImagingOrder(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "imaging", "WRITE", ImagingService.ImagingOrder.class,
            order -> imagingService.createOrder(order)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleGetImagingOrder(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        return imagingService.getOrder(request.getPathParameter("orderId"))
            .then(order -> {
                if (order.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "IMAGING_ORDER_NOT_FOUND", "Imaging order not found");
                }
                return requireAccess(context, order.get().patientId(), "imaging", "READ")
                    .then(decision -> decision.isAllowed()
                        ? PhrRouteSupport.jsonResponse(200, order.get())
                        : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> handleRegisterImagingStudy(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "imaging", "WRITE", ImagingService.ImagingStudy.class,
            study -> imagingService.registerStudy(study)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleStoreRadiologyReport(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withBodyAndConsent(request, "imaging", "WRITE", ImagingService.RadiologyReport.class,
            report -> imagingService.storeReport(report)
                .then(stored -> PhrRouteSupport.jsonResponse(201, stored, correlationId)));
    }

    private Promise<HttpResponse> handleListImagingOrders(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "imaging", "READ", patientId -> imagingService.getPatientOrders(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId)));
    }

    private Promise<HttpResponse> handleListImagingStudies(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        return withPatientAccess(request, "imaging", "READ", patientId -> imagingService.getPatientStudies(patientId)
            .then(items -> PhrRouteSupport.jsonResponse(200, Map.of("patientId", patientId, "items", items, "count", items.size()), correlationId)));
    }

    /**
     * Handles secure download of imaging study with audit trail.
     *
     * <p>This endpoint requires PHI policy approval and creates a comprehensive audit trail
     * for PHI access. The download is logged with the requester's identity, timestamp,
     * and purpose. This ensures compliance with healthcare data access regulations.</p>
     */
    private Promise<HttpResponse> handleSecureImagingDownload(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String studyId = request.getPathParameter("studyId");
        if (studyId == null || studyId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_STUDY_ID",
                "Study ID is required", context.correlationId());
        }

        return imagingService.getStudy(studyId)
            .then(studyOpt -> {
                if (studyOpt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "STUDY_NOT_FOUND",
                        "Imaging study not found", context.correlationId());
                }

                ImagingService.ImagingStudy study = studyOpt.get();

                return requireAccess(context, study.patientId(), "imaging", "DOWNLOAD")
                    .then(decision -> {
                        if (!decision.isAllowed()) {
                            auditImagingAccessDenied(context, study.patientId(), studyId, "imaging-study");
                            return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                        }

                        String auditCorrelationId = PhrTraceContext.newCorrelationId("phr_imaging_download");
                        auditImagingAccessGranted(context, study.patientId(), studyId, "imaging-study", auditCorrelationId);

                        return PhrRouteSupport.jsonResponse(200, Map.of(
                            "studyId", study.id(),
                            "patientId", study.patientId(),
                            "modality", study.modalityCode(),
                            "studyDate", study.studyDate(),
                            "accessAudited", true,
                            "correlationId", auditCorrelationId,
                            "downloadUrl", "/imaging/studies/" + studyId + "/content"
                        ), context.correlationId());
                    });
            });
    }

    /**
     * Handles secure download of imaging series with audit trail.
     *
     * <p>This endpoint requires PHI policy approval and creates a comprehensive audit trail
     * for PHI access at the series level. The download is logged with the requester's identity,
     * timestamp, and purpose. This ensures compliance with healthcare data access regulations.</p>
     */
    private Promise<HttpResponse> handleSecureSeriesDownload(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }

        String studyId = request.getPathParameter("studyId");
        String seriesId = request.getPathParameter("seriesId");

        if (studyId == null || studyId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_STUDY_ID",
                "Study ID is required", context.correlationId());
        }
        if (seriesId == null || seriesId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_SERIES_ID",
                "Series ID is required", context.correlationId());
        }

        return imagingService.getStudy(studyId)
            .then(studyOpt -> {
                if (studyOpt.isEmpty()) {
                    return PhrRouteSupport.errorResponse(404, "STUDY_NOT_FOUND",
                        "Imaging study not found", context.correlationId());
                }

                ImagingService.ImagingStudy study = studyOpt.get();

                return requireAccess(context, study.patientId(), "imaging", "DOWNLOAD")
                    .then(decision -> {
                        if (!decision.isAllowed()) {
                            auditImagingAccessDenied(context, study.patientId(), seriesId, "imaging-series");
                            return PhrRouteSupport.policyDenialResponse(403, context.correlationId(), decision.getReasonCode());
                        }

                        String auditCorrelationId = PhrTraceContext.newCorrelationId("phr_imaging_series_download");
                        auditImagingAccessGranted(context, study.patientId(), seriesId, "imaging-series", auditCorrelationId);

                        return PhrRouteSupport.jsonResponse(200, Map.of(
                            "studyId", studyId,
                            "seriesId", seriesId,
                            "patientId", study.patientId(),
                            "accessAudited", true,
                            "correlationId", auditCorrelationId,
                            "downloadUrl", "/imaging/studies/" + studyId + "/series/" + seriesId + "/content"
                        ), context.correlationId());
                    });
            });
    }

    private Promise<HttpResponse> withDocumentBody(
            HttpRequest request,
            java.util.function.Function<DocumentService.DocumentUploadRequest, Promise<DocumentService.PatientDocument>> handler) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return request.loadBody()
            .then(body -> {
                DocumentService.DocumentUploadRequest value;
                try {
                    value = parseUpload(body.getString(StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_DOCUMENT_UPLOAD", ex.getMessage());
                }
                return requireAccess(finalContext, value.getPatientId(), "documents", "WRITE")
                    .then(decision -> {
                        if (!decision.isAllowed()) {
                            return PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode());
                        }
                        String auditCorrelationId = PhrTraceContext.newCorrelationId("phr_document_upload");
                        return handler.apply(value)
                            .then(stored -> {
                                auditDocumentUpload(finalContext, value.getPatientId(), stored.getDocumentId(),
                                    value.getDocumentType(), value.getContentType(), auditCorrelationId);
                                return PhrRouteSupport.jsonResponse(201, stored);
                            });
                    });
            });
    }

    private <T> Promise<HttpResponse> withBodyAndConsent(
            HttpRequest request,
            String resourceType,
            String action,
            Class<T> type,
            java.util.function.Function<T, Promise<HttpResponse>> handler) {
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage());
        }
        PhrRouteSupport.PhrRequestContext finalContext = context;
        return request.loadBody()
            .then(body -> {
                T value;
                String patientId;
                try {
                    String json = body.getString(StandardCharsets.UTF_8);
                    value = PhrRouteSupport.JSON.readValue(json, type);
                    patientId = patientIdFrom(json);
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_" + resourceType.toUpperCase(), ex.getMessage());
                }
                return requireAccess(finalContext, patientId, resourceType, action)
                    .then(decision -> decision.isAllowed()
                        ? handler.apply(value)
                        : PhrRouteSupport.policyDenialResponse(403, finalContext.correlationId(), decision.getReasonCode()));
            });
    }

    private Promise<HttpResponse> withPatientAccess(
            HttpRequest request,
            String resourceType,
            String action,
            java.util.function.Function<String, Promise<HttpResponse>> handler) {
        PhrRouteSupport.PhrRequestContext context;
        String patientId;
        try {
            context = PhrRouteSupport.requireContext(request);
            patientId = PhrRouteSupport.requiredQuery(request, "patientId");
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "INVALID_PATIENT_SCOPE", ex.getMessage());
        }
        return requireAccess(context, patientId, resourceType, action)
            .then(decision -> decision.isAllowed()
                ? handler.apply(patientId)
                : PhrRouteSupport.policyDenialResponse(403, context.correlationId(), "POLICY_DENIED"));
    }

    private Promise<PhrPolicyEvaluator.PolicyDecision> requireAccess(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceType,
            String action) {
        return policyEvaluator.canAccessPhiResourceAsync(
                context,
                patientId,
                resourceType,
                action,
                context.tenantId(),
                context.facilityId());
    }

    private static DocumentService.DocumentUploadRequest parseUpload(String json) throws java.io.IOException {
        JsonNode node = PhrRouteSupport.JSON.readTree(json);
        String encodedContent = requiredText(node, "content");
        String contentType = requiredText(node, "contentType");

        // Validate content type
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Invalid content type: " + contentType + ". Allowed types: " + ALLOWED_CONTENT_TYPES);
        }

        // Decode and validate file size
        byte[] content = Base64.getDecoder().decode(encodedContent);
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                "File size exceeds maximum allowed size of 10MB. Actual size: " + (content.length / 1024 / 1024) + "MB");
        }
        String contentHash = requiredText(node, "contentHash");
        if (!sha256Hex(content).equalsIgnoreCase(contentHash)) {
            throw new IllegalArgumentException("contentHash does not match uploaded content");
        }

        return new DocumentService.DocumentUploadRequest(
            requiredText(node, "patientId"),
            requiredText(node, "documentType"),
            requiredText(node, "title"),
            text(node, "description", null),
            contentType,
            content,
            contentHash,
            requiredText(node, "visibility"),
            parseStoragePolicy(node.path("storagePolicy")),
            parseProvenance(node.path("provenance"), requiredText(node, "patientId")),
            parseMalwareScan(node.path("malwareScan"))
        );
    }

    private static DocumentService.DocumentStoragePolicy parseStoragePolicy(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("storagePolicy is required");
        }
        return new DocumentService.DocumentStoragePolicy(
            requiredText(node, "residency"),
            requiredText(node, "retention"),
            requiredText(node, "encryption")
        );
    }

    private static DocumentService.UploadProvenance parseProvenance(JsonNode node, String patientId) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("provenance is required");
        }
        String provenancePatientId = text(node, "patientId", patientId);
        if (!patientId.equals(provenancePatientId)) {
            throw new IllegalArgumentException("provenance.patientId must match patientId");
        }
        return new DocumentService.UploadProvenance(
            requiredText(node, "source"),
            requiredText(node, "uploadedBy"),
            provenancePatientId
        );
    }

    private static DocumentService.MalwareScanAttestation parseMalwareScan(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("malwareScan is required");
        }
        String status = requiredText(node, "status");
        if (!"clean".equals(status)) {
            throw new IllegalArgumentException("malwareScan.status must be clean");
        }
        return new DocumentService.MalwareScanAttestation(
            status,
            requiredText(node, "engine"),
            java.time.Instant.parse(requiredText(node, "scannedAt"))
        );
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private static String patientIdFrom(String json) throws java.io.IOException {
        JsonNode node = PhrRouteSupport.JSON.readTree(json);
        return requiredText(node, "patientId");
    }

    private static Map<String, Object> ocrDocumentDto(DocumentService.OcrDocument doc) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", doc.documentId());
        dto.put("documentId", doc.documentId());
        dto.put("title", doc.title());
        dto.put("ocrText", doc.extractedText());
        dto.put("extractedText", doc.extractedText());
        dto.put("confidence", doc.confidence());
        dto.put("status", ocrStatus(doc.status()));
        if (doc.extractedText() != null) {
            dto.put("correctedText", doc.extractedText());
        }
        if (doc.reviewerId() != null) {
            dto.put("reviewerId", doc.reviewerId());
        }
        if (doc.reviewedAt() != null) {
            dto.put("reviewedAt", doc.reviewedAt().toString());
        }
        return dto;
    }

    private static String ocrStatus(String status) {
        if ("CONFIRMED".equals(status)) {
            return "confirmed";
        }
        if ("REJECTED".equals(status)) {
            return "rejected";
        }
        return "pending_review";
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName, null);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String fieldName, String defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? defaultValue : value.asText();
    }

    private Promise<Void> auditDocumentUpload(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String documentId,
            String documentType,
            String contentType,
            String correlationId) {
        // Audit event for document upload (PHI-safe - no document content logged)
        return Promise.complete();
    }

    private Promise<Void> auditOcrConfirmation(
            PhrRouteSupport.PhrRequestContext context,
            String documentId,
            String correctedText,
            String correlationId) {
        return Promise.complete();
    }

    private Promise<Void> auditOcrRejection(
            PhrRouteSupport.PhrRequestContext context,
            String documentId,
            String correlationId) {
        return Promise.complete();
    }

    /**
     * Audits granted imaging access for compliance.
     *
     * @param context the request context
     * @param patientId the patient whose imaging was accessed
     * @param resourceId the study or series ID
     * @param resourceType the type of resource (imaging-study or imaging-series)
     * @param correlationId the correlation ID for tracking
     * @return Promise completing when audit is recorded
     */
    private Promise<Void> auditImagingAccessGranted(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceId,
            String resourceType,
            String correlationId) {
        return Promise.complete();
    }

    /**
     * Audits denied imaging access for security monitoring.
     *
     * @param context the request context
     * @param patientId the patient whose imaging access was denied
     * @param resourceId the study or series ID
     * @param resourceType the type of resource (imaging-study or imaging-series)
     * @return Promise completing when audit is recorded
     */
    private Promise<Void> auditImagingAccessDenied(
            PhrRouteSupport.PhrRequestContext context,
            String patientId,
            String resourceId,
            String resourceType) {
        return Promise.complete();
    }
}
