package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a media artifact stored in Data Cloud with comprehensive governance.
 *
 * <p><b>Purpose</b><br>
 * Captures complete metadata for audio, video, and image artifacts including
 * classification, consent, retention, ownership, and lineage information.
 * Provides first-class modality support for media assets in Data Cloud.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaArtifact artifact = MediaArtifact.builder()
 *     .tenantId("tenant-123")
 *     .agentId("agent-456")
 *     .mediaType("audio/wav")
 *     .storageUri("s3://bucket/path/to/audio.wav")
 *     .sizeBytes(1024000L)
 *     .checksum("sha256:abc123...")
 *     .durationMs(5000L)
 *     .classification(Classification.INTERNAL)
 *     .consentStatus(ConsentStatus.CONSENTED)
 *     .build();
 * 
 * // Check if consent is required
 * if (artifact.requiresConsent()) {
 *     // Verify consent before processing
 * }
 * }</pre>
 *
 * @see MediaProcessingJob
 * @see Transcript
 * @see FrameIndex
 * @doc.type class
 * @doc.purpose Media artifact with governance and lifecycle management
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_artifacts", indexes = {
    @Index(name = "idx_media_artifact_tenant", columnList = "tenant_id"),
    @Index(name = "idx_media_artifact_agent", columnList = "agent_id"),
    @Index(name = "idx_media_artifact_type", columnList = "media_type"),
    @Index(name = "idx_media_artifact_classification", columnList = "classification"),
    @Index(name = "idx_media_artifact_consent", columnList = "consent_status"),
    @Index(name = "idx_media_artifact_expires", columnList = "expires_at"),
    @Index(name = "idx_media_artifact_owner", columnList = "owner_id"),
    @Index(name = "idx_media_artifact_created", columnList = "created_at")
})
public class MediaArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "artifact_id", nullable = false, unique = true, length = 255)
    private String artifactId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @NotNull
    @Column(name = "media_type", nullable = false, length = 255)
    private String mediaType;

    @NotNull
    @Column(name = "storage_uri", nullable = false, length = 1000)
    private String storageUri;

    @NotNull
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "origin_tool_id", length = 255)
    private String originToolId;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 50)
    private Classification classification = Classification.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", length = 50)
    private ConsentStatus consentStatus;

    @Column(name = "retention_policy", length = 255)
    private String retentionPolicy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "owner_id", length = 255)
    private String ownerId;

    @Column(name = "source_system", length = 255)
    private String sourceSystem;

    /**
     * Lineage information showing parent artifacts and transformation chain.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lineage", columnDefinition = "jsonb")
    private Map<String, String> lineage = new HashMap<>();

    /**
     * Extension metadata for additional attributes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Processing jobs associated with this artifact.
     */
    @OneToMany(mappedBy = "mediaArtifact", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MediaProcessingJob> processingJobs = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Data classification levels.
     */
    public enum Classification {
        PUBLIC,      // Publicly accessible data
        INTERNAL,    // Internal company data
        CONFIDENTIAL, // Confidential business data
        RESTRICTED   // Highly restricted data
    }

    /**
     * Consent status for PII/biometric data.
     */
    public enum ConsentStatus {
        CONSENTED,   // Explicit consent obtained
        PENDING,     // Consent pending approval
        EXPIRED,     // Consent has expired
        NONE,        // No consent required/available
        REVOKED      // Consent was revoked
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getOriginToolId() {
        return originToolId;
    }

    public void setOriginToolId(String originToolId) {
        this.originToolId = originToolId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    public ConsentStatus getConsentStatus() {
        return consentStatus;
    }

    public void setConsentStatus(ConsentStatus consentStatus) {
        this.consentStatus = consentStatus;
    }

    public String getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Map<String, String> getLineage() {
        return lineage;
    }

    public void setLineage(Map<String, String> lineage) {
        this.lineage = lineage;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<MediaProcessingJob> getProcessingJobs() {
        return processingJobs;
    }

    public void setProcessingJobs(List<MediaProcessingJob> processingJobs) {
        this.processingJobs = processingJobs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ============ Business Methods ============

    /**
     * Checks if this artifact requires explicit consent.
     */
    public boolean requiresConsent() {
        return mediaType.startsWith("audio/") || mediaType.startsWith("video/");
    }

    /**
     * Checks if the artifact has valid consent for processing.
     */
    public boolean hasValidConsent() {
        if (!requiresConsent()) {
            return true;
        }
        return consentStatus == ConsentStatus.CONSENTED;
    }

    /**
     * Checks if the artifact has expired based on retention policy.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the artifact is an audio file.
     */
    public boolean isAudio() {
        return mediaType.startsWith("audio/");
    }

    /**
     * Checks if the artifact is a video file.
     */
    public boolean isVideo() {
        return mediaType.startsWith("video/");
    }

    /**
     * Checks if the artifact is an image file.
     */
    public boolean isImage() {
        return mediaType.startsWith("image/");
    }

    /**
     * Gets the media category (AUDIO, VIDEO, IMAGE).
     */
    public String getMediaCategory() {
        if (isAudio()) return "AUDIO";
        if (isVideo()) return "VIDEO";
        if (isImage()) return "IMAGE";
        return "OTHER";
    }

    /**
     * Updates the metadata with new key-value pairs.
     */
    public void updateMetadata(Map<String, String> newMetadata) {
        if (newMetadata != null) {
            this.metadata.putAll(newMetadata);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Adds a lineage entry showing transformation from parent artifact.
     */
    public void addLineageEntry(String parentArtifactId, String transformationType) {
        this.lineage.put(parentArtifactId, transformationType);
        this.updatedAt = Instant.now();
    }

    /**
     * Gets the age of the artifact in days.
     */
    public Long getAgeDays() {
        if (createdAt == null) return null;
        return java.time.Duration.between(createdAt, Instant.now()).toDays();
    }

    /**
     * Gets the human-readable size string.
     */
    public String getHumanReadableSize() {
        if (sizeBytes == null) return "Unknown";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.1f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Gets the duration in human-readable format.
     */
    public String getHumanReadableDuration() {
        if (durationMs == null || durationMs == 0) return "N/A";
        long seconds = durationMs / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format("%d:%02d", seconds / 60, seconds % 60);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds % 60);
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Set default owner if not specified
        if (ownerId == null) ownerId = agentId;
        
        // Set default source system if not specified
        if (sourceSystem == null) sourceSystem = "media-artifact-service";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String artifactId;
        private String tenantId;
        private String agentId;
        private String mediaType;
        private String storageUri;
        private Long sizeBytes;
        private String checksum;
        private Long durationMs;
        private String originToolId;
        private String correlationId;
        private Classification classification = Classification.INTERNAL;
        private ConsentStatus consentStatus;
        private String retentionPolicy;
        private Instant expiresAt;
        private String ownerId;
        private String sourceSystem;
        private Map<String, String> lineage = new HashMap<>();
        private Map<String, String> metadata = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public Builder storageUri(String storageUri) {
            this.storageUri = storageUri;
            return this;
        }

        public Builder sizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder originToolId(String originToolId) {
            this.originToolId = originToolId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder classification(Classification classification) {
            this.classification = classification;
            return this;
        }

        public Builder consentStatus(ConsentStatus consentStatus) {
            this.consentStatus = consentStatus;
            return this;
        }

        public Builder retentionPolicy(String retentionPolicy) {
            this.retentionPolicy = retentionPolicy;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder lineage(Map<String, String> lineage) {
            this.lineage = lineage;
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

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public MediaArtifact build() {
            MediaArtifact artifact = new MediaArtifact();
            artifact.id = this.id;
            artifact.artifactId = this.artifactId;
            artifact.tenantId = this.tenantId;
            artifact.agentId = this.agentId;
            artifact.mediaType = this.mediaType;
            artifact.storageUri = this.storageUri;
            artifact.sizeBytes = this.sizeBytes;
            artifact.checksum = this.checksum;
            artifact.durationMs = this.durationMs;
            artifact.originToolId = this.originToolId;
            artifact.correlationId = this.correlationId;
            artifact.classification = this.classification;
            artifact.consentStatus = this.consentStatus;
            artifact.retentionPolicy = this.retentionPolicy;
            artifact.expiresAt = this.expiresAt;
            artifact.ownerId = this.ownerId;
            artifact.sourceSystem = this.sourceSystem;
            artifact.lineage = this.lineage;
            artifact.metadata = this.metadata;
            artifact.createdAt = this.createdAt;
            artifact.updatedAt = this.updatedAt;
            return artifact;
        }
    }

    @Override
    public String toString() {
        return "MediaArtifact{" +
                "artifactId='" + artifactId + '\'' +
                ", mediaType='" + mediaType + '\'' +
                ", classification=" + classification +
                ", consentStatus=" + consentStatus +
                ", sizeBytes=" + sizeBytes +
                '}';
    }
}
