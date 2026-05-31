/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents the result of a media processing operation in Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Stores structured results from media processing jobs including transcription text,
 * frame indices, vision analysis outputs, and other processing artifacts. Provides
 * comprehensive result tracking with validation, metadata, and provenance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaProcessingResultRecord result = MediaProcessingResultRecord.builder()
 *     .jobId(job.getJobId())
 *     .tenantId(job.getTenantId())
 *     .resultType(ResultType.TRANSCRIPT)
 *     .status(ResultStatus.VALIDATED)
 *     .data(Map.of("transcript", "Hello world", "confidence", 0.95))
 *     .build();
 * }</pre>
 *
 * @see MediaProcessingJob
 * @see Transcript
 * @see FrameIndex
 * @doc.type class
 * @doc.purpose Media processing result with validation and metadata
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_processing_results", indexes = {
    @Index(name = "idx_media_result_tenant", columnList = "tenant_id"),
    @Index(name = "idx_media_result_job", columnList = "job_id"),
    @Index(name = "idx_media_result_type", columnList = "result_type"),
    @Index(name = "idx_media_result_status", columnList = "status"),
    @Index(name = "idx_media_result_created", columnList = "created_at")
})
public class MediaProcessingResultRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "result_id", nullable = false, unique = true, length = 255)
    private String resultId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "job_id", nullable = false, length = 255)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", insertable = false, updatable = false)
    private MediaProcessingJob job;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "result_type", nullable = false, length = 50)
    private ResultType resultType;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    private ResultStatus status = ResultStatus.PENDING;

    @Column(name = "format", length = 50)
    private String format;

    @Column(name = "encoding", length = 50)
    private String encoding;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Result data (transcript text, frame indices, analysis results, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private Map<String, Object> data = new HashMap<>();

    /**
     * Validation results for the processing output.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private ValidationResult validation;

    /**
     * Quality metrics (confidence scores, accuracy, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_metrics", columnDefinition = "jsonb")
    private Map<String, Object> qualityMetrics = new HashMap<>();

    /**
     * Storage URI if the result is stored externally.
     */
    @Column(name = "storage_uri", length = 1000)
    private String storageUri;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Types of media processing results.
     */
    public enum ResultType {
        TRANSCRIPT,          // Speech-to-text transcript
        FRAME_INDEX,         // Video frame index
        VISION_ANALYSIS,     // Computer vision analysis
        AUDIO_FEATURES,      // Audio feature extraction
        VIDEO_FEATURES,      // Video feature extraction
        TEXT_ANALYSIS,       // Text analysis output
        CLASSIFICATION,      // Classification results
        DETECTION,           // Object/face detection
        SEGMENTATION,        // Image/video segmentation
        EMBEDDING,           // Vector embeddings
        CUSTOM_RESULT        // Custom processing result
    }

    /**
     * Result validation status.
     */
    public enum ResultStatus {
        PENDING,             // Awaiting validation
        VALIDATING,          // Currently validating
        VALIDATED,           // Successfully validated
        INVALID,             // Validation failed
        APPROVED,            // Approved for use
        REJECTED,            // Rejected
        ARCHIVED             // Archived
    }

    /**
     * Validation result structure.
     */
    public record ValidationResult(
        boolean valid,
        String validationType,
        List<String> errors,
        List<String> warnings,
        Map<String, Object> details,
        Instant validatedAt
    ) {
        public ValidationResult {
            if (errors == null) errors = List.of();
            if (warnings == null) warnings = List.of();
            if (details == null) details = Map.of();
            if (validatedAt == null) validatedAt = Instant.now();
        }
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public MediaProcessingJob getJob() {
        return job;
    }

    public void setJob(MediaProcessingJob job) {
        this.job = job;
        if (job != null) {
            this.jobId = job.getJobId();
        }
    }

    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
    }

    public ResultStatus getStatus() {
        return status;
    }

    public void setStatus(ResultStatus status) {
        this.status = status;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public ValidationResult getValidation() {
        return validation;
    }

    public void setValidation(ValidationResult validation) {
        this.validation = validation;
    }

    public Map<String, Object> getQualityMetrics() {
        return qualityMetrics;
    }

    public void setQualityMetrics(Map<String, Object> qualityMetrics) {
        this.qualityMetrics = qualityMetrics;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    // ============ Business Methods ============

    /**
     * Validates the result with the given validation result.
     */
    public void validate(ValidationResult validationResult) {
        this.status = validationResult.valid() ? ResultStatus.VALIDATED : ResultStatus.INVALID;
        this.validation = validationResult;
        this.validatedAt = Instant.now();
    }

    /**
     * Approves the result for use.
     */
    public void approve() {
        if (status != ResultStatus.VALIDATED) {
            throw new IllegalStateException("Result must be validated before approval");
        }
        this.status = ResultStatus.APPROVED;
    }

    /**
     * Rejects the result.
     */
    public void reject(String reason) {
        this.status = ResultStatus.REJECTED;
        this.metadata = new HashMap<>(this.metadata);
        this.metadata.put("rejectionReason", reason);
    }

    /**
     * Archives the result.
     */
    public void archive() {
        this.status = ResultStatus.ARCHIVED;
    }

    /**
     * Checks if the result is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the result is approved for use.
     */
    public boolean isApproved() {
        return status == ResultStatus.APPROVED;
    }

    /**
     * Checks if the result is valid.
     */
    public boolean isValid() {
        return status == ResultStatus.VALIDATED || status == ResultStatus.APPROVED;
    }

    /**
     * Gets the human-readable size.
     */
    public String getHumanReadableSize() {
        if (sizeBytes == null) return "Unknown";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.1f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        
        // Generate result ID if not provided
        if (resultId == null) {
            resultId = resultType.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String resultId;
        private String tenantId;
        private String jobId;
        private MediaProcessingJob job;
        private ResultType resultType;
        private ResultStatus status = ResultStatus.PENDING;
        private String format;
        private String encoding;
        private Long sizeBytes;
        private Long durationMs;
        private Map<String, Object> data = new HashMap<>();
        private ValidationResult validation;
        private Map<String, Object> qualityMetrics = new HashMap<>();
        private String storageUri;
        private String checksum;
        private String mimeType;
        private String languageCode;
        private Double confidenceScore;
        private Long processingTimeMs;
        private String modelVersion;
        private String modelName;
        private List<String> tags = new ArrayList<>();
        private Map<String, String> metadata = new HashMap<>();
        private Instant createdAt;
        private Instant validatedAt;
        private Instant expiresAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder resultId(String resultId) {
            this.resultId = resultId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder job(MediaProcessingJob job) {
            this.job = job;
            if (job != null) {
                this.jobId = job.getJobId();
            }
            return this;
        }

        public Builder resultType(ResultType resultType) {
            this.resultType = resultType;
            return this;
        }

        public Builder status(ResultStatus status) {
            this.status = status;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder sizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder validation(ValidationResult validation) {
            this.validation = validation;
            return this;
        }

        public Builder qualityMetrics(Map<String, Object> qualityMetrics) {
            this.qualityMetrics = qualityMetrics;
            return this;
        }

        public Builder storageUri(String storageUri) {
            this.storageUri = storageUri;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder languageCode(String languageCode) {
            this.languageCode = languageCode;
            return this;
        }

        public Builder confidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder processingTimeMs(Long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder validatedAt(Instant validatedAt) {
            this.validatedAt = validatedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public MediaProcessingResultRecord build() {
            MediaProcessingResultRecord result = new MediaProcessingResultRecord();
            result.id = this.id;
            result.resultId = this.resultId;
            result.tenantId = this.tenantId;
            result.jobId = this.jobId;
            result.job = this.job;
            result.resultType = this.resultType;
            result.status = this.status;
            result.format = this.format;
            result.encoding = this.encoding;
            result.sizeBytes = this.sizeBytes;
            result.durationMs = this.durationMs;
            result.data = this.data;
            result.validation = this.validation;
            result.qualityMetrics = this.qualityMetrics;
            result.storageUri = this.storageUri;
            result.checksum = this.checksum;
            result.mimeType = this.mimeType;
            result.languageCode = this.languageCode;
            result.confidenceScore = this.confidenceScore;
            result.processingTimeMs = this.processingTimeMs;
            result.modelVersion = this.modelVersion;
            result.modelName = this.modelName;
            result.tags = this.tags;
            result.metadata = this.metadata;
            result.createdAt = this.createdAt;
            result.validatedAt = this.validatedAt;
            result.expiresAt = this.expiresAt;
            return result;
        }
    }

    @Override
    public String toString() {
        return "MediaProcessingResultRecord{" +
                "resultId='" + resultId + '\'' +
                ", resultType=" + resultType +
                ", status=" + status +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}
