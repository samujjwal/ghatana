package com.ghatana.phr.healthcare.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Clinical document metadata record.
 *
 * <p>Stores metadata about a clinical document (lab report, imaging study,
 * discharge summary, etc.). The binary content is stored in object storage;
 * only metadata and classification live here.</p>
 *
 * <p>All clinical documents are tenant-scoped and patient-scoped.
 * Classification governs whether a consent check is required before access.</p>
 *
 * @doc.type record
 * @doc.purpose Clinical document metadata — healthcare domain
 * @doc.layer domain-pack
 * @doc.pattern ValueObject
 * @since 1.0.0
 */
public record ClinicalDocument(
    UUID documentId,
    String tenantId,
    UUID patientId,

    DocumentType type,
    String title,
    String mimeType,
    long sizeBytes,

    /** Object storage key — tenant-prefixed path. */
    String storageKey,

    DataClassification classification,

    String issuedBy,           // practitioner or facility id
    Instant issuedAt,
    Instant uploadedAt,
    String uploadedBy,

    boolean deleted
) {

    public ClinicalDocument {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patientId, "patientId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(storageKey, "storageKey must not be null");
        Objects.requireNonNull(classification, "classification must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    /**
     * Document types recognised by the healthcare domain pack.
     */
    public enum DocumentType {
        LAB_REPORT,
        IMAGING_STUDY,
        DISCHARGE_SUMMARY,
        PRESCRIPTION,
        REFERRAL_LETTER,
        VACCINATION_RECORD,
        INSURANCE_CLAIM,
        CONSENT_FORM,
        OPERATIVE_REPORT,
        PATHOLOGY_REPORT,
        OTHER
    }
}
