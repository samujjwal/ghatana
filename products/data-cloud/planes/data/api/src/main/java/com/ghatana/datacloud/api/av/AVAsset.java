/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.av;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Audio-Video asset entity model for Data Cloud.
 * 
 * P8.1: AV asset/entity model (file, stream, transcript, frame index, metadata, consent, retention).
 * Represents audio and video content as first-class Data Cloud entities with full governance support.
 * 
 * @doc.type class
 * @doc.purpose AV asset entity model for Data Cloud
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class AVAsset {

    private final String id;
    private final String tenantId;
    private final AVAssetType type;
    private final AVAssetFormat format;
    private final String sourceUri;
    private final String storageUri;
    private final long sizeBytes;
    private final long durationMs;
    private final AVTranscript transcript;
    private final AVFrameIndex frameIndex;
    private final AVMetadata metadata;
    private final AVConsent consent;
    private final AVRetention retention;
    private final String status;
    private final String ownerId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Map<String, Object> tags;

    private AVAsset(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.type = builder.type;
        this.format = builder.format;
        this.sourceUri = builder.sourceUri;
        this.storageUri = builder.storageUri;
        this.sizeBytes = builder.sizeBytes;
        this.durationMs = builder.durationMs;
        this.transcript = builder.transcript;
        this.frameIndex = builder.frameIndex;
        this.metadata = builder.metadata;
        this.consent = builder.consent;
        this.retention = builder.retention;
        this.status = builder.status;
        this.ownerId = builder.ownerId;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.tags = Map.copyOf(builder.tags);
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public AVAssetType type() { return type; }
    public AVAssetFormat format() { return format; }
    public String sourceUri() { return sourceUri; }
    public String storageUri() { return storageUri; }
    public long sizeBytes() { return sizeBytes; }
    public long durationMs() { return durationMs; }
    public Optional<AVTranscript> transcript() { return Optional.ofNullable(transcript); }
    public Optional<AVFrameIndex> frameIndex() { return Optional.ofNullable(frameIndex); }
    public AVMetadata metadata() { return metadata; }
    public AVConsent consent() { return consent; }
    public AVRetention retention() { return retention; }
    public String status() { return status; }
    public String ownerId() { return ownerId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Map<String, Object> tags() { return tags; }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .type(type)
            .format(format)
            .sourceUri(sourceUri)
            .storageUri(storageUri)
            .sizeBytes(sizeBytes)
            .durationMs(durationMs)
            .transcript(transcript)
            .frameIndex(frameIndex)
            .metadata(metadata)
            .consent(consent)
            .retention(retention)
            .status(status)
            .ownerId(ownerId)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .tags(tags);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String tenantId;
        private AVAssetType type;
        private AVAssetFormat format;
        private String sourceUri;
        private String storageUri;
        private long sizeBytes;
        private long durationMs;
        private AVTranscript transcript;
        private AVFrameIndex frameIndex;
        private AVMetadata metadata;
        private AVConsent consent;
        private AVRetention retention;
        private String status = "PENDING";
        private String ownerId;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();
        private final Map<String, Object> tags = new java.util.LinkedHashMap<>();

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder type(AVAssetType type) { this.type = type; return this; }
        public Builder format(AVAssetFormat format) { this.format = format; return this; }
        public Builder sourceUri(String sourceUri) { this.sourceUri = sourceUri; return this; }
        public Builder storageUri(String storageUri) { this.storageUri = storageUri; return this; }
        public Builder sizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder transcript(AVTranscript transcript) { this.transcript = transcript; return this; }
        public Builder frameIndex(AVFrameIndex frameIndex) { this.frameIndex = frameIndex; return this; }
        public Builder metadata(AVMetadata metadata) { this.metadata = metadata; return this; }
        public Builder consent(AVConsent consent) { this.consent = consent; return this; }
        public Builder retention(AVRetention retention) { this.retention = retention; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder ownerId(String ownerId) { this.ownerId = ownerId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder tag(String key, Object value) { this.tags.put(key, value); return this; }
        public Builder tags(Map<String, Object> tags) { this.tags.putAll(tags); return this; }

        public AVAsset build() {
            if (tenantId == null) throw new IllegalStateException("tenantId is required");
            if (type == null) throw new IllegalStateException("type is required");
            if (format == null) throw new IllegalStateException("format is required");
            if (consent == null) throw new IllegalStateException("consent is required");
            if (retention == null) throw new IllegalStateException("retention is required");
            return new AVAsset(this);
        }
    }

    /**
     * AV asset type.
     */
    public enum AVAssetType {
        AUDIO,
        VIDEO,
        AUDIO_VIDEO
    }

    /**
     * AV asset format.
     */
    public enum AVAssetFormat {
        MP3, WAV, AAC, FLAC, OGG,
        MP4, AVI, MOV, MKV, WEBM,
        HLS, DASH
    }

    /**
     * Transcript data for audio/video content.
     *
     * @param id transcript ID
     * @param language language code (e.g., "en-US")
     * @param segments transcript segments with timestamps
     * @param confidence overall confidence score
     * @param provider transcription provider
     * @param createdAt when transcript was created
     */
    public record AVTranscript(
            String id,
            String language,
            List<TranscriptSegment> segments,
            double confidence,
            String provider,
            Instant createdAt) {

        public AVTranscript(String id, String language, List<TranscriptSegment> segments, String provider) {
            this(id, language, segments, 0.0, provider, Instant.now());
        }
    }

    /**
     * Transcript segment with timestamp.
     *
     * @param startTimeMs start time in milliseconds
     * @param endTimeMs end time in milliseconds
     * @param text transcript text
     * @param confidence segment confidence
     */
    public record TranscriptSegment(
            long startTimeMs,
            long endTimeMs,
            String text,
            double confidence) {}

    /**
     * Frame index for video content.
     *
     * @param id frame index ID
     * @param frameCount total number of frames
     * @param frameRate frames per second
     * @param keyFrames key frame timestamps
     * @param createdAt when index was created
     */
    public record AVFrameIndex(
            String id,
            int frameCount,
            double frameRate,
            List<Long> keyFrames,
            Instant createdAt) {

        public AVFrameIndex(String id, int frameCount, double frameRate, List<Long> keyFrames) {
            this(id, frameCount, frameRate, keyFrames, Instant.now());
        }
    }

    /**
     * AV metadata.
     *
     * @param title asset title
     * @param description asset description
     * @param creator content creator
     * @param recordingDate when content was recorded
     * @param location recording location
     * @param customFields custom metadata fields
     */
    public record AVMetadata(
            String title,
            String description,
            String creator,
            Instant recordingDate,
            String location,
            Map<String, String> customFields) {}

    /**
     * Consent information for AV content.
     *
     * @param consented whether consent was obtained
     * @param consentType type of consent (explicit, implicit, opt-out)
     * @param consentDate when consent was obtained
     * @param consentSource source of consent
     * @param legalHold whether legal hold is applied
     * @param legalHoldReason reason for legal hold
     */
    public record AVConsent(
            boolean consented,
            ConsentType consentType,
            Instant consentDate,
            String consentSource,
            boolean legalHold,
            String legalHoldReason) {

        public enum ConsentType {
            EXPLICIT,
            IMPLICIT,
            OPT_OUT
        }
    }

    /**
     * Retention policy for AV content.
     *
     * @param retentionPeriod retention period in days
     * @param deleteAfter delete after retention period
     * @param archiveAfter archive after (days)
     * @param redactionRequired whether redaction is required
     * @param redactionRules redaction rule IDs
     */
    public record AVRetention(
            int retentionPeriod,
            boolean deleteAfter,
            Integer archiveAfter,
            boolean redactionRequired,
            List<String> redactionRules) {}
}
