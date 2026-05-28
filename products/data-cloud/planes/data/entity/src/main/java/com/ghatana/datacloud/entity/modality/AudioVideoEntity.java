package com.ghatana.datacloud.entity.modality;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audio-video entity for first-class media modality support.
 *
 * <p><b>Purpose</b><br>
 * Represents audio and video content as first-class entities in Data Cloud.
 * Provides metadata, storage references, and governance for media assets.
 *
 * <p><b>Capabilities</b><br>
 * <ul>
 *   <li><b>Metadata</b>: Duration, format, resolution, codec information</li>
 *   <li><b>Storage</b>: Reference to blob storage for media content</li>
 *   <li><b>Transcoding</b>: Track transcoding status and variants</li>
 *   <li><b>Governance</b>: Classification, retention, and access control</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Audio-video entity for first-class media modality
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@jakarta.persistence.Entity(name = "audio_video_entities")
@Table(name = "audio_video_entities", indexes = {
    @Index(name = "idx_av_tenant", columnList = "tenant_id"),
    @Index(name = "idx_av_collection", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_av_modality", columnList = "modality"),
    @Index(name = "idx_av_status", columnList = "transcoding_status"),
    @Index(name = "idx_av_created", columnList = "created_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioVideoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Base entity reference.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private Entity entity;

    /**
     * Media modality (AUDIO, VIDEO, AUDIO_VIDEO).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private MediaModality modality;

    /**
     * Media format (MP4, WAV, MP3, etc.).
     */
    @Column(name = "format", length = 50)
    private String format;

    /**
     * Duration in milliseconds.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * File size in bytes.
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * Video resolution (e.g., "1920x1080").
     */
    @Column(name = "resolution", length = 20)
    private String resolution;

    /**
     * Video codec (e.g., "H.264", "VP9").
     */
    @Column(name = "video_codec", length = 50)
    private String videoCodec;

    /**
     * Audio codec (e.g., "AAC", "Opus").
     */
    @Column(name = "audio_codec", length = 50)
    private String audioCodec;

    /**
     * Bitrate in kbps.
     */
    @Column(name = "bitrate_kbps")
    private Integer bitrateKbps;

    /**
     * Frame rate for video (fps).
     */
    @Column(name = "frame_rate")
    private Double frameRate;

    /**
     * Storage location/URI for the media file.
     */
    @Column(name = "storage_uri", length = 500)
    private String storageUri;

    /**
     * Storage provider (S3, GCS, Azure, etc.).
     */
    @Column(name = "storage_provider", length = 50)
    private String storageProvider;

    /**
     * Transcoding status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transcoding_status", length = 20)
    @Builder.Default
    private TranscodingStatus transcodingStatus = TranscodingStatus.PENDING;

    /**
     * Transcoding variants (different resolutions/formats).
     */
    @Column(name = "transcoding_variants", columnDefinition = "jsonb")
    private String transcodingVariants;

    /**
     * Thumbnail URI.
     */
    @Column(name = "thumbnail_uri", length = 500)
    private String thumbnailUri;

    /**
     * Media classification (PUBLIC, INTERNAL, CONFIDENTIAL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 20)
    @Builder.Default
    private MediaClassification classification = MediaClassification.INTERNAL;

    /**
     * Content hash for integrity verification.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * When the media was uploaded.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When the media was last modified.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (transcodingStatus == null) {
            transcodingStatus = TranscodingStatus.PENDING;
        }
        if (classification == null) {
            classification = MediaClassification.INTERNAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if this is a video asset.
     */
    public boolean isVideo() {
        return modality == MediaModality.VIDEO || modality == MediaModality.AUDIO_VIDEO;
    }

    /**
     * Check if this is an audio asset.
     */
    public boolean isAudio() {
        return modality == MediaModality.AUDIO || modality == MediaModality.AUDIO_VIDEO;
    }

    /**
     * Check if transcoding is complete.
     */
    public boolean isTranscodingComplete() {
        return transcodingStatus == TranscodingStatus.COMPLETED;
    }

    /**
     * Check if transcoding is in progress.
     */
    public boolean isTranscodingInProgress() {
        return transcodingStatus == TranscodingStatus.PROCESSING;
    }

    /**
     * Mark transcoding as started.
     */
    public void startTranscoding() {
        this.transcodingStatus = TranscodingStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark transcoding as complete.
     */
    public void completeTranscoding(String variants) {
        this.transcodingStatus = TranscodingStatus.COMPLETED;
        this.transcodingVariants = variants;
        this.updatedAt = Instant.now();
    }

    /**
     * Mark transcoding as failed.
     */
    public void failTranscoding(String error) {
        this.transcodingStatus = TranscodingStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioVideoEntity that = (AudioVideoEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AudioVideoEntity{" +
                "id=" + id +
                ", modality=" + modality +
                ", format='" + format + '\'' +
                ", durationMs=" + durationMs +
                ", transcodingStatus=" + transcodingStatus +
                '}';
    }

    /**
     * Media modality enum.
     */
    public enum MediaModality {
        AUDIO,
        VIDEO,
        AUDIO_VIDEO
    }

    /**
     * Transcoding status enum.
     */
    public enum TranscodingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    /**
     * Media classification enum.
     */
    public enum MediaClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        RESTRICTED
    }
}
