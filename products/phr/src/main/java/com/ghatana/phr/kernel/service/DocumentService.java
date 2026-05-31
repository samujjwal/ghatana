package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.document.KernelDocumentOcrPlugin;

import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Document Service with FHIR R4 integration.
 *
 * <p>Manages patient documents with:
 * <ul>
 *   <li>FHIR R4 DocumentReference support</li>
 *   <li>Document-level granular consent</li>
 *   <li>Upload-time and retroactive consent management</li>
 *   <li>Metadata vs content visibility control</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR document service with FHIR R4 integration
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class DocumentService extends PhrServiceBase {

    private static final String DOCUMENT_DATASET = "phr.documents";
    private static final String CONTENT_DATASET = "phr.document.content";
    private final KernelDocumentOcrPlugin documentOcrPlugin;

    public DocumentService(KernelContext context) {
        this(context, new KernelDocumentOcrPlugin());
    }

    DocumentService(KernelContext context, KernelDocumentOcrPlugin documentOcrPlugin) {
        super(context);
        this.documentOcrPlugin = Objects.requireNonNull(documentOcrPlugin, "documentOcrPlugin cannot be null");
    }

    @Override
    public String getName() {
        return "document";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> documents = createSchema(
            DOCUMENT_DATASET,
            Map.of(
                "documentId", "string",
                "patientId", "string",
                "documentType", "string",
                "title", "string",
                "createdAt", "timestamp",
                "consent", "json"
            ),
            Map.of("retention", "25years")
        );

        Promise<Void> content = createSchema(
            CONTENT_DATASET,
            Map.of(
                "contentId", "string",
                "documentId", "string",
                "contentType", "string",
                "size", "integer"
            ),
            Map.of("retention", "25years")
        );

        return documents.then($ -> content);
    }

    // ==================== Core Document Operations ====================

    /**
     * Uploads a new document with upload-time consent.
     *
     * @param request the upload request
     * @return Promise containing the created document
     */
    public Promise<PatientDocument> uploadDocument(DocumentUploadRequest request) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(request.getPatientId(), "patientId");
        String documentType = PhrInputSanitizationUtils.requireSafeCode(request.getDocumentType(), "documentType");
        String title = PhrInputSanitizationUtils.sanitizeRequiredText(request.getTitle(), "title", 200);
        String description = PhrInputSanitizationUtils.sanitizeOptionalText(request.getDescription(), "description", 2000);
        String contentType = PhrInputSanitizationUtils.requireContentType(request.getContentType(), "contentType");
        byte[] contentBytes = PhrInputSanitizationUtils.requireBinaryContent(request.getContent(), "content", 5_000_000);
        String contentHash = PhrInputSanitizationUtils.sanitizeRequiredText(request.getContentHash(), "contentHash", 256);
        DocumentStoragePolicy storagePolicy = validateStoragePolicy(request.getStoragePolicy());
        MalwareScanAttestation malwareScan = validateMalwareScan(request.getMalwareScan());
        UploadProvenance provenance = validateProvenance(request.getProvenance(), patientId);
        String visibility = PhrInputSanitizationUtils.requireAllowedValue(
            request.getVisibility(),
            "visibility",
            java.util.Set.of("private", "shared-with-provider", "shared-with-all-granted")
        );

        String documentId = generateId("doc");
        String contentId = generateId("cnt");
        Instant now = Instant.now();

        DocumentContent content = new DocumentContent(
            contentId,
            documentId,
            contentType,
            contentBytes,
            contentHash
        );

        PatientDocument document = new PatientDocument(
            documentId,
            patientId,
            documentType,
            title,
            description,
            now,
            contentType,
            contentBytes.length,
            contentId,
            new DocumentConsent(visibility, now),
            storagePolicy,
            provenance,
            malwareScan,
            "PENDING_REVIEW",
            null,
            null,
            null,
            null,
            now,
            now,
            false
        );

        Promise<DocumentContent> contentWrite = storeContent(content);
        Promise<PatientDocument> metadataWrite = storeDocument(document);

        return Promises.all(List.of(contentWrite, metadataWrite))
            .then($ -> audit("DOCUMENT_UPLOAD", patientId,
                "Document uploaded: " + documentId + " with visibility: " + visibility))
            .map($ -> document);
    }

    /**
     * Retrieves a document by ID with consent check.
     *
     * @param documentId the document identifier
     * @param accessorId the accessor (for consent check)
     * @return Promise containing the document metadata if accessible
     */
    public Promise<Optional<PatientDocument>> getDocument(String documentId, String accessorId) {
        ensureRunning();

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                PatientDocument doc = opt.get();

                if (!isDocumentVisible(doc, accessorId)) {
                    return Promise.of(Optional.empty());
                }

                return audit("DOCUMENT_ACCESS", doc.getPatientId(),
                    "Document accessed by " + accessorId)
                    .map($ -> Optional.of(doc));
            });
    }

    /**
     * Retrieves document content with full consent check.
     *
     * @param documentId the document identifier
     * @param accessorId the accessor
     * @return Promise containing the content if accessible
     */
    public Promise<Optional<DocumentContent>> getDocumentContent(String documentId, String accessorId) {
        ensureRunning();

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                PatientDocument doc = opt.get();

                if (!isContentAccessible(doc, accessorId)) {
                    return Promise.of(Optional.empty());
                }

                return fetchContent(doc.getContentId())
                    .then(contentOpt -> {
                        if (contentOpt.isPresent()) {
                            return audit("DOCUMENT_CONTENT_ACCESS", doc.getPatientId(),
                                "Document content accessed by " + accessorId)
                                .map($ -> contentOpt);
                        }
                        return Promise.of(Optional.empty());
                    });
            });
    }

    /**
     * Changes document consent (retroactive consent change).
     *
     * @param documentId the document identifier
     * @param newVisibility the new visibility setting
     * @param patientId the patient (must be owner)
     * @return Promise completing when updated
     */
    public Promise<Void> updateDocumentConsent(String documentId, String newVisibility, String patientId) {
        ensureRunning();

        String sanitizedDocumentId = PhrInputSanitizationUtils.requireSafeIdentifier(documentId, "documentId");
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedVisibility = PhrInputSanitizationUtils.requireAllowedValue(
            newVisibility,
            "newVisibility",
            java.util.Set.of("private", "shared-with-provider", "shared-with-all-granted")
        );

        return fetchDocument(sanitizedDocumentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                if (!doc.getPatientId().equals(sanitizedPatientId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized"));
                }

                PatientDocument updated = doc.withConsent(
                    new DocumentConsent(sanitizedVisibility, Instant.now())
                ).withUpdatedAt(Instant.now());

                return storeDocument(updated)
                    .then($ -> invalidateConsentCache(sanitizedPatientId))
                    .then($ -> audit("DOCUMENT_CONSENT_CHANGE", sanitizedPatientId,
                        "Document " + sanitizedDocumentId + " visibility changed to " + sanitizedVisibility));
            });
    }

    /**
     * Gets documents for a patient with consent filtering.
     *
     * @param patientId the patient identifier
     * @param accessorId the accessor (for consent filtering)
     * @return Promise containing accessible documents
     */
    public Promise<List<PatientDocument>> getPatientDocuments(String patientId, String accessorId) {
        ensureRunning();

        boolean isOwner = patientId.equals(accessorId);

        return queryRecords(
            DOCUMENT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            100,
            0,
            PatientDocument.class
        ).map(documents -> documents.stream()
            .filter(doc -> isOwner || isDocumentVisible(doc, accessorId))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList());
    }

    /**
     * Soft-deletes a document.
     *
     * @param documentId the document identifier
     * @param patientId the patient (must be owner)
     * @return Promise completing when deleted
     */
    public Promise<Void> deleteDocument(String documentId, String patientId) {
        ensureRunning();

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                if (!doc.getPatientId().equals(patientId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized"));
                }

                PatientDocument deleted = doc.asDeleted();
                return storeDocument(deleted)
                    .then($ -> audit("DOCUMENT_DELETE", patientId,
                        "Document " + documentId + " soft-deleted"));
            });
    }

    /**
     * Converts document to FHIR R4 DocumentReference.
     *
     * @param documentId the document identifier
     * @return Promise containing FHIR JSON
     */
    public Promise<String> toFhirDocumentReference(String documentId) {
        ensureRunning();

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                String fhirJson = generateFhirDocumentReference(doc);
                return Promise.of(fhirJson);
            });
    }

    // ==================== OCR Operations ====================

    /**
     * Retrieves OCR document for review.
     *
     * @param documentId the document identifier
     * @param accessorId the accessor (for consent check)
     * @return Promise containing OCR document if accessible
     */
    public Promise<Optional<OcrDocument>> getOcrDocument(String documentId, String accessorId) {
        ensureRunning();

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                PatientDocument doc = opt.get();

                if (!isDocumentVisible(doc, accessorId)) {
                    return Promise.of(Optional.empty());
                }

                OcrDocument ocrDoc = new OcrDocument(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getOcrStatus() != null ? doc.getOcrStatus() : "PENDING_REVIEW",
                    0.0,
                    doc.getOcrCorrectedText() != null ? doc.getOcrCorrectedText() : "",
                    doc.getOcrReviewerId(),
                    doc.getOcrReviewedAt()
                );
                return Promise.of(Optional.of(ocrDoc));
            });
    }

    /**
     * Confirms OCR document with corrected text and creates FHIR draft with provenance.
     *
     * @param documentId the document identifier
     * @param reviewerId the reviewer who confirmed
     * @param correctedText the corrected text (may be null if no changes)
     * @return Promise completing when confirmed
     */
    public Promise<Void> confirmOcrDocument(String documentId, String reviewerId, String correctedText) {
        return confirmOcrDocument(documentId, reviewerId, correctedText, null).map($ -> (Void) null);
    }

    public Promise<OcrDocument> confirmOcrDocument(String documentId, String reviewerId, String correctedText, String idempotencyKey) {
        ensureRunning();

        String sanitizedDocumentId = PhrInputSanitizationUtils.requireSafeIdentifier(documentId, "documentId");
        String sanitizedReviewerId = PhrInputSanitizationUtils.requireSafeIdentifier(reviewerId, "reviewerId");
        String sanitizedText = PhrInputSanitizationUtils.sanitizeOptionalText(correctedText, "correctedText", 50_000);

        return fetchDocument(sanitizedDocumentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                if (!doc.getPatientId().equals(sanitizedReviewerId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized to confirm OCR"));
                }

                KernelDocumentOcrPlugin.ReviewTransition transition;
                try {
                    transition = documentOcrPlugin.validateReviewTransition(
                        doc.getOcrStatus(),
                        doc.getOcrIdempotencyKey(),
                        "CONFIRMED",
                        idempotencyKey
                    );
                } catch (IllegalStateException exception) {
                    return Promise.ofException(exception);
                }
                if (transition == KernelDocumentOcrPlugin.ReviewTransition.RETURN_EXISTING) {
                    return Promise.of(toOcrDocument(doc));
                }

                PatientDocument reviewed = doc.withOcrReview(
                    "CONFIRMED",
                    sanitizedReviewerId,
                    sanitizedText,
                    Instant.now(),
                    idempotencyKey
                );
                return storeDocument(reviewed)
                    .then($ -> audit("OCR_CONFIRMED", doc.getPatientId(),
                        "OCR confirmed for document " + sanitizedDocumentId + " by " + sanitizedReviewerId))
                    .map($ -> toOcrDocument(reviewed));
            });
    }

    /**
     * Rejects OCR document.
     *
     * @param documentId the document identifier
     * @param reviewerId the reviewer who rejected
     * @return Promise completing when rejected
     */
    public Promise<Void> rejectOcrDocument(String documentId, String reviewerId) {
        return rejectOcrDocument(documentId, reviewerId, null).map($ -> (Void) null);
    }

    public Promise<OcrDocument> rejectOcrDocument(String documentId, String reviewerId, String idempotencyKey) {
        ensureRunning();

        String sanitizedDocumentId = PhrInputSanitizationUtils.requireSafeIdentifier(documentId, "documentId");
        String sanitizedReviewerId = PhrInputSanitizationUtils.requireSafeIdentifier(reviewerId, "reviewerId");

        return fetchDocument(sanitizedDocumentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                if (!doc.getPatientId().equals(sanitizedReviewerId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized to reject OCR"));
                }

                KernelDocumentOcrPlugin.ReviewTransition transition;
                try {
                    transition = documentOcrPlugin.validateReviewTransition(
                        doc.getOcrStatus(),
                        doc.getOcrIdempotencyKey(),
                        "REJECTED",
                        idempotencyKey
                    );
                } catch (IllegalStateException exception) {
                    return Promise.ofException(exception);
                }
                if (transition == KernelDocumentOcrPlugin.ReviewTransition.RETURN_EXISTING) {
                    return Promise.of(toOcrDocument(doc));
                }

                PatientDocument reviewed = doc.withOcrReview(
                    "REJECTED",
                    sanitizedReviewerId,
                    null,
                    Instant.now(),
                    idempotencyKey
                );
                return storeDocument(reviewed)
                    .then($ -> audit("OCR_REJECTED", doc.getPatientId(),
                        "OCR rejected for document " + sanitizedDocumentId + " by " + sanitizedReviewerId))
                    .map($ -> toOcrDocument(reviewed));
            });
    }

    private static OcrDocument toOcrDocument(PatientDocument doc) {
        return new OcrDocument(
            doc.getId(),
            doc.getTitle(),
            doc.getOcrStatus() != null ? doc.getOcrStatus() : "PENDING_REVIEW",
            0.0,
            doc.getOcrCorrectedText() != null ? doc.getOcrCorrectedText() : "",
            doc.getOcrReviewerId(),
            doc.getOcrReviewedAt()
        );
    }

    // ==================== Private Methods ====================

    private Promise<Optional<PatientDocument>> fetchDocument(String documentId) {
        return readRecord(DOCUMENT_DATASET, documentId, PatientDocument.class);
    }

    private Promise<Optional<DocumentContent>> fetchContent(String contentId) {
        return readRecord(CONTENT_DATASET, contentId, DocumentContent.class);
    }

    private Promise<PatientDocument> storeDocument(PatientDocument document) {
        return updateRecord(
            DOCUMENT_DATASET,
            document.getId(),
            document,
            mutationMetadata(Map.of(
                "patientId", document.getPatientId(),
                "documentType", document.getDocumentType(),
                "createdAt", document.getCreatedAt().toString(),
                "storagePolicy", document.getStoragePolicy() != null ? document.getStoragePolicy().retention() : "unknown"
            ), "system"),
            "PatientDocument",
            1
        );
    }

    private Promise<DocumentContent> storeContent(DocumentContent content) {
        return createRecord(
            CONTENT_DATASET,
            content.getContentId(),
            content,
            mutationMetadata(Map.of(
                "documentId", content.getDocumentId(),
                "contentType", content.getContentType(),
                "size", String.valueOf(content.getContent().length)
            ), "system"),
            "DocumentContent",
            1
        );
    }

    private boolean isDocumentVisible(PatientDocument doc, String accessorId) {
        if (doc.isDeleted()) return false;
        if (doc.getPatientId().equals(accessorId)) return true;

        DocumentConsent consent = doc.getConsent();
        return switch (consent.getVisibility()) {
            case "private" -> false;
            case "shared-with-all-granted" -> true;
            case "shared-with-provider" -> true;
            default -> false;
        };
    }

    private boolean isContentAccessible(PatientDocument doc, String accessorId) {
        if (doc.isDeleted()) return false;
        if (doc.getPatientId().equals(accessorId)) return true;

        DocumentConsent consent = doc.getConsent();
        return "shared-with-all-granted".equals(consent.getVisibility()) ||
               "shared-with-provider".equals(consent.getVisibility());
    }

    private DocumentStoragePolicy validateStoragePolicy(DocumentStoragePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("storagePolicy is required");
        }
        KernelDocumentOcrPlugin.StoragePolicy validated = documentOcrPlugin.validateStoragePolicy(
            new KernelDocumentOcrPlugin.StoragePolicy(policy.residency(), policy.retention(), policy.encryption())
        );
        String residency = PhrInputSanitizationUtils.sanitizeRequiredText(
            validated.residency(),
            "storagePolicy.residency",
            20
        );
        String retention = PhrInputSanitizationUtils.sanitizeRequiredText(
            validated.retention(),
            "storagePolicy.retention",
            40
        );
        String encryption = PhrInputSanitizationUtils.sanitizeRequiredText(
            validated.encryption(),
            "storagePolicy.encryption",
            40
        );
        return new DocumentStoragePolicy(residency, retention, encryption);
    }

    private MalwareScanAttestation validateMalwareScan(MalwareScanAttestation attestation) {
        if (attestation == null) {
            throw new IllegalArgumentException("malwareScan is required");
        }
        KernelDocumentOcrPlugin.MalwareScanAttestation validated = documentOcrPlugin.validateMalwareScan(
            new KernelDocumentOcrPlugin.MalwareScanAttestation(
                attestation.status(),
                attestation.engine(),
                attestation.scannedAt()
            )
        );
        String status = PhrInputSanitizationUtils.sanitizeRequiredText(
            validated.status(),
            "malwareScan.status",
            40
        );
        String engine = PhrInputSanitizationUtils.sanitizeRequiredText(validated.engine(), "malwareScan.engine", 120);
        return new MalwareScanAttestation(status, engine, validated.scannedAt());
    }

    private UploadProvenance validateProvenance(UploadProvenance provenance, String patientId) {
        if (provenance == null) {
            throw new IllegalArgumentException("provenance is required");
        }
        String uploadedBy = PhrInputSanitizationUtils.requireSafeIdentifier(provenance.uploadedBy(), "provenance.uploadedBy");
        String provenancePatientId = PhrInputSanitizationUtils.requireSafeIdentifier(provenance.patientId(), "provenance.patientId");
        KernelDocumentOcrPlugin.UploadProvenance validated = documentOcrPlugin.validateUploadProvenance(
            new KernelDocumentOcrPlugin.UploadProvenance(provenance.source(), uploadedBy, provenancePatientId),
            patientId
        );
        String source = PhrInputSanitizationUtils.sanitizeRequiredText(validated.source(), "provenance.source", 120);
        return new UploadProvenance(source, uploadedBy, provenancePatientId);
    }

    private Promise<Void> invalidateConsentCache(String patientId) {
        return Promise.complete();
    }

    private String generateFhirDocumentReference(PatientDocument doc) {
        return "{" +
            "\"resourceType\":\"DocumentReference\"," +
            "\"id\":\"" + doc.getId() + "\"," +
            "\"status\":\"current\"," +
            "\"type\":{\"text\":\"" + doc.getDocumentType() + "\"}," +
            "\"subject\":{\"reference\":\"Patient/" + doc.getPatientId() + "\"}," +
            "\"content\":[{\"attachment\":{\"contentType\":\"" + doc.getContentType() + "\"," +
            "\"size\":" + doc.getSizeBytes() + "}}]}";
    }

    // ==================== Inner Types ====================

    public static class PatientDocument {
        private final String id;
        private final String patientId;
        private final String documentType;
        private final String title;
        private final String description;
        private final Instant documentDate;
        private final String contentType;
        private final int sizeBytes;
        private final String contentId;
        private final DocumentConsent consent;
        private final DocumentStoragePolicy storagePolicy;
        private final UploadProvenance provenance;
        private final MalwareScanAttestation malwareScan;
        private final String ocrStatus;
        private final String ocrReviewerId;
        private final String ocrCorrectedText;
        private final Instant ocrReviewedAt;
        private final String ocrIdempotencyKey;
        private final Instant createdAt;
        private final Instant updatedAt;
        private final boolean deleted;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public PatientDocument(
            @JsonProperty("id") String id,
            @JsonProperty("patientId") String patientId,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("documentDate") Instant documentDate,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("sizeBytes") int sizeBytes,
            @JsonProperty("contentId") String contentId,
            @JsonProperty("consent") DocumentConsent consent,
            @JsonProperty("storagePolicy") DocumentStoragePolicy storagePolicy,
            @JsonProperty("provenance") UploadProvenance provenance,
            @JsonProperty("malwareScan") MalwareScanAttestation malwareScan,
            @JsonProperty("ocrStatus") String ocrStatus,
            @JsonProperty("ocrReviewerId") String ocrReviewerId,
            @JsonProperty("ocrCorrectedText") String ocrCorrectedText,
            @JsonProperty("ocrReviewedAt") Instant ocrReviewedAt,
            @JsonProperty("ocrIdempotencyKey") String ocrIdempotencyKey,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("deleted") boolean deleted) {
            this.id = id;
            this.patientId = patientId;
            this.documentType = documentType;
            this.title = title;
            this.description = description;
            this.documentDate = documentDate;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.contentId = contentId;
            this.consent = consent;
            this.storagePolicy = storagePolicy;
            this.provenance = provenance;
            this.malwareScan = malwareScan;
            this.ocrStatus = ocrStatus;
            this.ocrReviewerId = ocrReviewerId;
            this.ocrCorrectedText = ocrCorrectedText;
            this.ocrReviewedAt = ocrReviewedAt;
            this.ocrIdempotencyKey = ocrIdempotencyKey;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.deleted = deleted;
        }

        public String getId() { return id; }
        public String getDocumentId() { return id; }
        public String getPatientId() { return patientId; }
        public String getDocumentType() { return documentType; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Instant getDocumentDate() { return documentDate; }
        public String getContentType() { return contentType; }
        public int getSizeBytes() { return sizeBytes; }
        public String getContentId() { return contentId; }
        public DocumentConsent getConsent() { return consent; }
        public DocumentStoragePolicy getStoragePolicy() { return storagePolicy; }
        public UploadProvenance getProvenance() { return provenance; }
        public MalwareScanAttestation getMalwareScan() { return malwareScan; }
        public String getOcrStatus() { return ocrStatus; }
        public String getOcrReviewerId() { return ocrReviewerId; }
        public String getOcrCorrectedText() { return ocrCorrectedText; }
        public Instant getOcrReviewedAt() { return ocrReviewedAt; }
        public String getOcrIdempotencyKey() { return ocrIdempotencyKey; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public boolean isDeleted() { return deleted; }

        public PatientDocument withConsent(DocumentConsent newConsent) {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, newConsent, storagePolicy, provenance, malwareScan,
                ocrStatus, ocrReviewerId, ocrCorrectedText, ocrReviewedAt, ocrIdempotencyKey, createdAt, updatedAt, deleted);
        }

        public PatientDocument withUpdatedAt(Instant newUpdatedAt) {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, consent, storagePolicy, provenance, malwareScan,
                ocrStatus, ocrReviewerId, ocrCorrectedText, ocrReviewedAt, ocrIdempotencyKey, createdAt, newUpdatedAt, deleted);
        }

        public PatientDocument asDeleted() {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, consent, storagePolicy, provenance, malwareScan,
                ocrStatus, ocrReviewerId, ocrCorrectedText, ocrReviewedAt, ocrIdempotencyKey, createdAt, Instant.now(), true);
        }

        public PatientDocument withOcrReview(
                String status,
                String reviewerId,
                String correctedText,
                Instant reviewedAt,
                String idempotencyKey) {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, consent, storagePolicy, provenance, malwareScan,
                status, reviewerId, correctedText, reviewedAt, idempotencyKey, createdAt, Instant.now(), deleted);
        }
    }

    public static class DocumentContent {
        private final String contentId;
        private final String documentId;
        private final String contentType;
        private final byte[] content;
        private final String contentHash; // SHA-256

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public DocumentContent(
            @JsonProperty("contentId") String contentId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("content") byte[] content,
            @JsonProperty("contentHash") String contentHash) {
            this.contentId = contentId;
            this.documentId = documentId;
            this.contentType = contentType;
            this.content = content;
            this.contentHash = contentHash;
        }

        public String getContentId() { return contentId; }
        public String getDocumentId() { return documentId; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
        public String getContentHash() { return contentHash; }
    }

    public static class DocumentConsent {
        private final String visibility; // private, shared-with-provider, shared-with-all-granted
        private final Instant setAt;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public DocumentConsent(
                @JsonProperty("visibility") String visibility,
                @JsonProperty("setAt") Instant setAt) {
            this.visibility = visibility;
            this.setAt = setAt;
        }

        public String getVisibility() { return visibility; }
        public Instant getSetAt() { return setAt; }
    }

    public record DocumentStoragePolicy(String residency, String retention, String encryption) {}

    public record UploadProvenance(String source, String uploadedBy, String patientId) {}

    public record MalwareScanAttestation(String status, String engine, Instant scannedAt) {}

    public static class DocumentUploadRequest {
        private final String patientId;
        private final String documentType;
        private final String title;
        private final String description;
        private final String contentType;
        private final byte[] content;
        private final String contentHash;
        private final String visibility;
        private final DocumentStoragePolicy storagePolicy;
        private final UploadProvenance provenance;
        private final MalwareScanAttestation malwareScan;

        public DocumentUploadRequest(String patientId, String documentType, String title,
                                      String description, String contentType, byte[] content,
                                      String contentHash, String visibility,
                                      DocumentStoragePolicy storagePolicy,
                                      UploadProvenance provenance,
                                      MalwareScanAttestation malwareScan) {
            this.patientId = patientId;
            this.documentType = documentType;
            this.title = title;
            this.description = description;
            this.contentType = contentType;
            this.content = content;
            this.contentHash = contentHash;
            this.visibility = visibility;
            this.storagePolicy = storagePolicy;
            this.provenance = provenance;
            this.malwareScan = malwareScan;
        }

        public String getPatientId() { return patientId; }
        public String getDocumentType() { return documentType; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
        public String getContentHash() { return contentHash; }
        public String getVisibility() { return visibility; }
        public DocumentStoragePolicy getStoragePolicy() { return storagePolicy; }
        public UploadProvenance getProvenance() { return provenance; }
        public MalwareScanAttestation getMalwareScan() { return malwareScan; }
    }

    public static class OcrDocument {
        private final String documentId;
        private final String title;
        private final String status;
        private final double confidence;
        private final String extractedText;
        private final String reviewerId;
        private final Instant reviewedAt;

        public OcrDocument(String documentId, String title, String status, double confidence, String extractedText) {
            this(documentId, title, status, confidence, extractedText, null, null);
        }

        public OcrDocument(String documentId, String title, String status, double confidence, String extractedText,
                           String reviewerId, Instant reviewedAt) {
            this.documentId = documentId;
            this.title = title;
            this.status = status;
            this.confidence = confidence;
            this.extractedText = extractedText;
            this.reviewerId = reviewerId;
            this.reviewedAt = reviewedAt;
        }

        public String documentId() { return documentId; }
        public String title() { return title; }
        public String status() { return status; }
        public double confidence() { return confidence; }
        public String extractedText() { return extractedText; }
        public String reviewerId() { return reviewerId; }
        public Instant reviewedAt() { return reviewedAt; }
    }
}
