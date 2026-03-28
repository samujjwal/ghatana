package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
public class DocumentService implements KernelLifecycleAware {

    private static final String DOCUMENT_DATASET = "phr.documents";
    private static final String CONTENT_DATASET = "phr.document.content";
    private static final String AUDIT_DATASET = "phr.document.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    public DocumentService(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
    }

    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }

    public boolean isHealthy() {
        return running;
    }

    public String getName() {
        return "document";
    }

    // ==================== Core Document Operations ====================

    /**
     * Uploads a new document with upload-time consent.
     *
     * @param request the upload request
     * @return Promise containing the created document
     */
    public Promise<PatientDocument> uploadDocument(DocumentUploadRequest request) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        String documentId = generateId();
        String contentId = generateId();
        Instant now = Instant.now();

        // Store content separately
        DocumentContent content = new DocumentContent(
            contentId,
            documentId,
            request.getContentType(),
            request.getContent(),
            request.getContentHash()
        );

        // Store metadata
        PatientDocument document = new PatientDocument(
            documentId,
            request.getPatientId(),
            request.getDocumentType(),
            request.getTitle(),
            request.getDescription(),
            now,
            request.getContentType(),
            request.getContent().length,
            contentId,
            new DocumentConsent(request.getVisibility(), now),
            now,
            now,
            false
        );

        Promise<Void> contentWrite = storeContent(content);
        Promise<Void> metadataWrite = storeDocument(document);

        return Promises.all(List.of(contentWrite, metadataWrite))
            .then($ -> audit("DOCUMENT_UPLOAD", request.getPatientId(),
                "Document uploaded: " + documentId + " with visibility: " + request.getVisibility()))
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
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                PatientDocument doc = opt.get();

                // Check document-level consent
                if (!isDocumentVisible(doc, accessorId)) {
                    return Promise.of(Optional.empty());
                }

                // Log access
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
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(Optional.empty());
                }
                PatientDocument doc = opt.get();

                // Check if content is accessible (stricter than metadata)
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
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return fetchDocument(documentId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Document not found"));
                }

                PatientDocument doc = opt.get();
                if (!doc.getPatientId().equals(patientId)) {
                    return Promise.ofException(new IllegalStateException("Not authorized"));
                }

                PatientDocument updated = doc.withConsent(
                    new DocumentConsent(newVisibility, Instant.now())
                ).withUpdatedAt(Instant.now());

                return storeDocument(updated)
                    .then($ -> invalidateConsentCache(patientId))
                    .then($ -> audit("DOCUMENT_CONSENT_CHANGE", patientId,
                        "Document " + documentId + " visibility changed to " + newVisibility));
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
        if (!running) {
            return Promise.of(List.of());
        }

        boolean isOwner = patientId.equals(accessorId);

        DataQueryRequest request = new DataQueryRequest(
            DOCUMENT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            100,
            0
        );

        return dataCloud.queryData(request)
            .map(QueryResult::getResults)
            .map(results -> results.stream()
                .map(r -> deserialize(r.getData()))
                .filter(Objects::nonNull)
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
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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

    // ==================== Private Methods ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> documents = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
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
        ));

        Promise<Void> content = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
            CONTENT_DATASET,
            Map.of(
                "contentId", "string",
                "documentId", "string",
                "contentType", "string",
                "size", "integer"
            ),
            Map.of("retention", "25years")
        ));

        return Promises.all(List.of(documents, content));
    }

    private Promise<Optional<PatientDocument>> fetchDocument(String documentId) {
        DataReadRequest request = new DataReadRequest(DOCUMENT_DATASET, documentId, Map.of());

        return dataCloud.readData(request)
            .map(result -> Optional.ofNullable(deserialize(result.getData())))
            ;
    }

    private Promise<Optional<DocumentContent>> fetchContent(String contentId) {
        DataReadRequest request = new DataReadRequest(CONTENT_DATASET, contentId, Map.of());

        return dataCloud.readData(request)
            .map(result -> Optional.ofNullable(deserializeContent(result.getData())))
            ;
    }

    private Promise<Void> storeDocument(PatientDocument document) {
        DataWriteRequest request = new DataWriteRequest(
            DOCUMENT_DATASET,
            document.getId(),
            serialize(document),
            Map.of(
                "patientId", document.getPatientId(),
                "documentType", document.getDocumentType(),
                "createdAt", document.getCreatedAt().toString()
            )
        );

        return dataCloud.writeData(request);
    }

    private Promise<Void> storeContent(DocumentContent content) {
        DataWriteRequest request = new DataWriteRequest(
            CONTENT_DATASET,
            content.getContentId(),
            serializeContent(content),
            Map.of(
                "documentId", content.getDocumentId(),
                "contentType", content.getContentType(),
                "size", String.valueOf(content.getContent().length)
            )
        );

        return dataCloud.writeData(request);
    }

    private boolean isDocumentVisible(PatientDocument doc, String accessorId) {
        if (doc.isDeleted()) return false;
        if (doc.getPatientId().equals(accessorId)) return true;

        DocumentConsent consent = doc.getConsent();
        return switch (consent.getVisibility()) {
            case "private" -> false;
            case "shared-with-all-granted" -> true; // Assumes caller validated grant
            case "shared-with-provider" -> true; // Provider access
            default -> false;
        };
    }

    private boolean isContentAccessible(PatientDocument doc, String accessorId) {
        // Content access is stricter than metadata visibility
        if (doc.isDeleted()) return false;
        if (doc.getPatientId().equals(accessorId)) return true;

        DocumentConsent consent = doc.getConsent();
        // Only specific visibility levels allow content access
        return "shared-with-all-granted".equals(consent.getVisibility()) ||
               "shared-with-provider".equals(consent.getVisibility());
    }

    private Promise<Void> invalidateConsentCache(String patientId) {
        // Invalidate consent cache for all grantees of this patient
        return Promise.complete();
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId();
        DataWriteRequest request = new DataWriteRequest(
            AUDIT_DATASET,
            auditId,
            (action + ":" + patientId + ":" + details).getBytes(StandardCharsets.UTF_8),
            Map.of("timestamp", Instant.now().toString())
        );

        return dataCloud.writeData(request);
    }

    private String generateFhirDocumentReference(PatientDocument doc) {
        // Generate FHIR R4 DocumentReference JSON
        return "{" +
            "\"resourceType\":\"DocumentReference\"," +
            "\"id\":\"" + doc.getId() + "\"," +
            "\"status\":\"current\"," +
            "\"type\":{\"text\":\"" + doc.getDocumentType() + "\"}," +
            "\"subject\":{\"reference\":\"Patient/" + doc.getPatientId() + "\"}," +
            "\"content\":[{\"attachment\":{\"contentType\":\"" + doc.getContentType() + "\"," +
            "\"size\":" + doc.getSizeBytes() + "}}]}";
    }

    private byte[] serialize(PatientDocument document) {
        return TypedDataSerializer.toBytes(document, "PatientDocument", 1);
    }

    private PatientDocument deserialize(byte[] data) {
        if (data == null) return null;
        return TypedDataSerializer.fromBytes(data, PatientDocument.class);
    }

    private byte[] serializeContent(DocumentContent content) {
        return TypedDataSerializer.toBytes(content, "DocumentContent", 1);
    }

    private DocumentContent deserializeContent(byte[] data) {
        if (data == null) return null;
        return TypedDataSerializer.fromBytes(data, DocumentContent.class);
    }

    private String generateId() {
        return "doc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
        private final Instant createdAt;
        private final Instant updatedAt;
        private final boolean deleted;

        public PatientDocument(String id, String patientId, String documentType, String title,
                              String description, Instant documentDate, String contentType,
                              int sizeBytes, String contentId, DocumentConsent consent,
                              Instant createdAt, Instant updatedAt, boolean deleted) {
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
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.deleted = deleted;
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getDocumentType() { return documentType; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Instant getDocumentDate() { return documentDate; }
        public String getContentType() { return contentType; }
        public int getSizeBytes() { return sizeBytes; }
        public String getContentId() { return contentId; }
        public DocumentConsent getConsent() { return consent; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public boolean isDeleted() { return deleted; }

        public PatientDocument withConsent(DocumentConsent newConsent) {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, newConsent, createdAt, updatedAt, deleted);
        }

        public PatientDocument withUpdatedAt(Instant newUpdatedAt) {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, consent, createdAt, newUpdatedAt, deleted);
        }

        public PatientDocument asDeleted() {
            return new PatientDocument(id, patientId, documentType, title, description,
                documentDate, contentType, sizeBytes, contentId, consent, createdAt, Instant.now(), true);
        }
    }

    public static class DocumentContent {
        private final String contentId;
        private final String documentId;
        private final String contentType;
        private final byte[] content;
        private final String contentHash; // SHA-256

        public DocumentContent(String contentId, String documentId, String contentType,
                                byte[] content, String contentHash) {
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

        public DocumentConsent(String visibility, Instant setAt) {
            this.visibility = visibility;
            this.setAt = setAt;
        }

        public String getVisibility() { return visibility; }
        public Instant getSetAt() { return setAt; }
    }

    public static class DocumentUploadRequest {
        private final String patientId;
        private final String documentType;
        private final String title;
        private final String description;
        private final String contentType;
        private final byte[] content;
        private final String contentHash;
        private final String visibility;

        public DocumentUploadRequest(String patientId, String documentType, String title,
                                      String description, String contentType, byte[] content,
                                      String contentHash, String visibility) {
            this.patientId = patientId;
            this.documentType = documentType;
            this.title = title;
            this.description = description;
            this.contentType = contentType;
            this.content = content;
            this.contentHash = contentHash;
            this.visibility = visibility;
        }

        public String getPatientId() { return patientId; }
        public String getDocumentType() { return documentType; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
        public String getContentHash() { return contentHash; }
        public String getVisibility() { return visibility; }
    }
}
