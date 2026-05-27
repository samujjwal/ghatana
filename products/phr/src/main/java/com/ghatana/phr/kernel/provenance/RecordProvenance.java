package com.ghatana.phr.kernel.provenance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical record provenance model for PHR data imports.
 *
 * <p>This model tracks the origin, transformation history, and chain of custody
 * for clinical data imported from external sources (FHIR, documents, OCR, voice, HIE).
 * Provenance is required for healthcare compliance and auditability.</p>
 *
 * <p>Provenance includes:
 * <ul>
 *   <li>Source system and identifier</li>
 *   <li>Import method (FHIR, HIE, manual upload, OCR, voice)</li>
 *   <li>Timestamps for import and any transformations</li>
 *   <li>Actor who performed the import or confirmation</li>
 *   <li>Transformation history (e.g., OCR corrections, data mappings)</li>
 *   <li>Verification status and confidence scores</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canonical provenance model for clinical data imports
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public final class RecordProvenance {

    private final String provenanceId;
    private final String recordId;
    private final String recordType; // FHIR-Resource, Document, LabResult, etc.
    private final String sourceSystem; // HIE-Facility, FHIR-Server, OCR-Engine, Voice-Transcriber
    private final String sourceIdentifier; // External system's ID for the record
    private final ImportMethod importMethod;
    private final Instant importedAt;
    private final String importedBy; // Principal ID
    private final Instant verifiedAt;
    private final String verifiedBy; // Principal ID (may be same as importedBy)
    private final VerificationStatus verificationStatus;
    private final double confidenceScore; // 0.0 to 1.0 for OCR/voice/ML imports
    private final Map<String, String> transformationHistory; // JSON string of transformations
    private final Map<String, String> metadata; // Additional provenance metadata

    public RecordProvenance(
            String provenanceId,
            String recordId,
            String recordType,
            String sourceSystem,
            String sourceIdentifier,
            ImportMethod importMethod,
            Instant importedAt,
            String importedBy,
            Instant verifiedAt,
            String verifiedBy,
            VerificationStatus verificationStatus,
            double confidenceScore,
            Map<String, String> transformationHistory,
            Map<String, String> metadata) {
        this.provenanceId = Objects.requireNonNull(provenanceId, "provenanceId must not be null");
        this.recordId = Objects.requireNonNull(recordId, "recordId must not be null");
        this.recordType = Objects.requireNonNull(recordType, "recordType must not be null");
        this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        this.sourceIdentifier = Objects.requireNonNull(sourceIdentifier, "sourceIdentifier must not be null");
        this.importMethod = Objects.requireNonNull(importMethod, "importMethod must not be null");
        this.importedAt = Objects.requireNonNull(importedAt, "importedAt must not be null");
        this.importedBy = Objects.requireNonNull(importedBy, "importedBy must not be null");
        this.verifiedAt = verifiedAt;
        this.verifiedBy = verifiedBy;
        this.verificationStatus = verificationStatus != null ? verificationStatus : VerificationStatus.PENDING;
        this.confidenceScore = confidenceScore;
        this.transformationHistory = transformationHistory != null ? Map.copyOf(transformationHistory) : Map.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public String provenanceId() { return provenanceId; }
    public String recordId() { return recordId; }
    public String recordType() { return recordType; }
    public String sourceSystem() { return sourceSystem; }
    public String sourceIdentifier() { return sourceIdentifier; }
    public ImportMethod importMethod() { return importMethod; }
    public Instant importedAt() { return importedAt; }
    public String importedBy() { return importedBy; }
    public Instant verifiedAt() { return verifiedAt; }
    public String verifiedBy() { return verifiedBy; }
    public VerificationStatus verificationStatus() { return verificationStatus; }
    public double confidenceScore() { return confidenceScore; }
    public Map<String, String> transformationHistory() { return transformationHistory; }
    public Map<String, String> metadata() { return metadata; }

    /**
     * Import method enumeration.
     */
    public enum ImportMethod {
        FHIR_IMPORT,
        HIE_EXPORT,
        HIE_IMPORT,
        DOCUMENT_UPLOAD,
        OCR_EXTRACTION,
        VOICE_TRANSCRIPTION,
        MANUAL_ENTRY,
        LAB_IMPORT,
        PRESCRIPTION_IMPORT
    }

    /**
     * Verification status enumeration.
     */
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED,
        CORRECTED,
        AUTO_VERIFIED
    }

    /**
     * Builder for RecordProvenance.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String provenanceId;
        private String recordId;
        private String recordType;
        private String sourceSystem;
        private String sourceIdentifier;
        private ImportMethod importMethod;
        private Instant importedAt;
        private String importedBy;
        private Instant verifiedAt;
        private String verifiedBy;
        private VerificationStatus verificationStatus;
        private double confidenceScore = 1.0;
        private Map<String, String> transformationHistory;
        private Map<String, String> metadata;

        public Builder provenanceId(String provenanceId) {
            this.provenanceId = provenanceId;
            return this;
        }

        public Builder recordId(String recordId) {
            this.recordId = recordId;
            return this;
        }

        public Builder recordType(String recordType) {
            this.recordType = recordType;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder sourceIdentifier(String sourceIdentifier) {
            this.sourceIdentifier = sourceIdentifier;
            return this;
        }

        public Builder importMethod(ImportMethod importMethod) {
            this.importMethod = importMethod;
            return this;
        }

        public Builder importedAt(Instant importedAt) {
            this.importedAt = importedAt;
            return this;
        }

        public Builder importedBy(String importedBy) {
            this.importedBy = importedBy;
            return this;
        }

        public Builder verifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
            return this;
        }

        public Builder verifiedBy(String verifiedBy) {
            this.verifiedBy = verifiedBy;
            return this;
        }

        public Builder verificationStatus(VerificationStatus verificationStatus) {
            this.verificationStatus = verificationStatus;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder transformationHistory(Map<String, String> transformationHistory) {
            this.transformationHistory = transformationHistory;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public RecordProvenance build() {
            if (importedAt == null) {
                importedAt = Instant.now();
            }
            if (provenanceId == null) {
                provenanceId = "prov-" + recordType + "-" + System.currentTimeMillis();
            }
            return new RecordProvenance(
                provenanceId, recordId, recordType, sourceSystem, sourceIdentifier,
                importMethod, importedAt, importedBy, verifiedAt, verifiedBy,
                verificationStatus, confidenceScore, transformationHistory, metadata
            );
        }
    }
}
