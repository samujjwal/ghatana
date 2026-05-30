package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a transcript generated from audio/video media artifacts.
 *
 * <p><b>Purpose</b><br>
 * Stores speech-to-text transcription results with comprehensive metadata
 * including timestamps, confidence scores, speaker identification, and
 * language detection. Provides structured access to transcribed content
 * for search, analysis, and processing workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Transcript transcript = Transcript.builder()
 *     .mediaArtifact(artifact)
 *     .languageCode("en-US")
 *     .confidenceScore(0.95)
 *     .fullText("Hello world, this is a test.")
 *     .segments(List.of(
 *         new TranscriptSegment(0, 2000, "Hello world", 0.98),
 *         new TranscriptSegment(2000, 4000, "this is a test", 0.92)
 *     ))
 *     .build();
 * 
 * // Search within transcript
 * List<TranscriptSegment> matches = transcript.searchForText("test");
 * 
 * // Get high-confidence segments
 * List<TranscriptSegment> highConfidence = transcript.getHighConfidenceSegments(0.9);
 * }</pre>
 *
 * @see MediaArtifact
 * @see MediaProcessingJob
 * @doc.type class
 * @doc.purpose Speech-to-text transcript with timestamps and confidence
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_transcripts", indexes = {
    @Index(name = "idx_transcript_tenant", columnList = "tenant_id"),
    @Index(name = "idx_transcript_artifact", columnList = "media_artifact_id"),
    @Index(name = "idx_transcript_job", columnList = "processing_job_id"),
    @Index(name = "idx_transcript_language", columnList = "language_code"),
    @Index(name = "idx_transcript_confidence", columnList = "confidence_score"),
    @Index(name = "idx_transcript_created", columnList = "created_at")
})
public class Transcript {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "transcript_id", nullable = false, unique = true, length = 255)
    private String transcriptId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "media_artifact_id", nullable = false)
    private UUID mediaArtifactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_artifact_id", insertable = false, updatable = false)
    private MediaArtifact mediaArtifact;

    @Column(name = "processing_job_id")
    private UUID processingJobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_job_id", insertable = false, updatable = false)
    private MediaProcessingJob processingJob;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "detected_language", length = 10)
    private String detectedLanguage;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "speaker_count")
    private Integer speakerCount;

    @Column(name = "full_text", columnDefinition = "text")
    private String fullText;

    /**
     * Segmented transcript with timestamps and confidence scores.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments", columnDefinition = "jsonb")
    private List<TranscriptSegment> segments = new ArrayList<>();

    /**
     * Speaker identification information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "speakers", columnDefinition = "jsonb")
    private Map<String, SpeakerInfo> speakers = new HashMap<>();

    /**
     * Alternative language hypotheses.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alternatives", columnDefinition = "jsonb")
    private List<LanguageAlternative> alternatives = new ArrayList<>();

    /**
     * Processing metadata and quality metrics.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processing_metadata", columnDefinition = "jsonb")
    private Map<String, Object> processingMetadata = new HashMap<>();

    /**
     * Search index for efficient text search.
     */
    @Column(name = "search_vector", columnDefinition = "tsvector")
    private String searchVector;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Represents a segment of the transcript with timing information.
     */
    public record TranscriptSegment(
        Long startTimeMs,
        Long endTimeMs,
        String text,
        Double confidence,
        String speakerId,
        Map<String, Object> metadata
    ) {
        public TranscriptSegment {
            if (startTimeMs == null) startTimeMs = 0L;
            if (endTimeMs == null) endTimeMs = startTimeMs;
            if (confidence == null) confidence = 0.0;
            if (speakerId == null) speakerId = "unknown";
            if (metadata == null) metadata = Map.of();
        }

        public Long getDurationMs() {
            return endTimeMs - startTimeMs;
        }

        public boolean isHighConfidence(double threshold) {
            return confidence >= threshold;
        }
    }

    /**
     * Speaker information and characteristics.
     */
    public record SpeakerInfo(
        String speakerId,
        String displayName,
        Double confidence,
        Map<String, Object> characteristics,
        List<String> segments
    ) {
        public SpeakerInfo {
            if (displayName == null) displayName = "Speaker " + speakerId;
            if (confidence == null) confidence = 0.0;
            if (characteristics == null) characteristics = Map.of();
            if (segments == null) segments = List.of();
        }
    }

    /**
     * Alternative language hypothesis.
     */
    public record LanguageAlternative(
        String languageCode,
        Double confidence,
        String translatedText,
        Map<String, Object> metadata
    ) {
        public LanguageAlternative {
            if (confidence == null) confidence = 0.0;
            if (translatedText == null) translatedText = "";
            if (metadata == null) metadata = Map.of();
        }
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTranscriptId() {
        return transcriptId;
    }

    public void setTranscriptId(String transcriptId) {
        this.transcriptId = transcriptId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getMediaArtifactId() {
        return mediaArtifactId;
    }

    public void setMediaArtifactId(UUID mediaArtifactId) {
        this.mediaArtifactId = mediaArtifactId;
    }

    public MediaArtifact getMediaArtifact() {
        return mediaArtifact;
    }

    public void setMediaArtifact(MediaArtifact mediaArtifact) {
        this.mediaArtifact = mediaArtifact;
        if (mediaArtifact != null) {
            this.mediaArtifactId = mediaArtifact.getId();
        }
    }

    public UUID getProcessingJobId() {
        return processingJobId;
    }

    public void setProcessingJobId(UUID processingJobId) {
        this.processingJobId = processingJobId;
    }

    public MediaProcessingJob getProcessingJob() {
        return processingJob;
    }

    public void setProcessingJob(MediaProcessingJob processingJob) {
        this.processingJob = processingJob;
        if (processingJob != null) {
            this.processingJobId = processingJob.getId();
        }
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public Integer getSpeakerCount() {
        return speakerCount;
    }

    public void setSpeakerCount(Integer speakerCount) {
        this.speakerCount = speakerCount;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public List<TranscriptSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<TranscriptSegment> segments) {
        this.segments = segments;
    }

    public Map<String, SpeakerInfo> getSpeakers() {
        return speakers;
    }

    public void setSpeakers(Map<String, SpeakerInfo> speakers) {
        this.speakers = speakers;
    }

    public List<LanguageAlternative> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<LanguageAlternative> alternatives) {
        this.alternatives = alternatives;
    }

    public Map<String, Object> getProcessingMetadata() {
        return processingMetadata;
    }

    public void setProcessingMetadata(Map<String, Object> processingMetadata) {
        this.processingMetadata = processingMetadata;
    }

    public String getSearchVector() {
        return searchVector;
    }

    public void setSearchVector(String searchVector) {
        this.searchVector = searchVector;
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
     * Gets the total duration of the transcript in milliseconds.
     */
    public Long getTotalDurationMs() {
        if (segments.isEmpty()) return 0L;
        return segments.get(segments.size() - 1).endTimeMs();
    }

    /**
     * Gets the duration in human-readable format.
     */
    public String getHumanReadableDuration() {
        long duration = getTotalDurationMs();
        long seconds = duration / 1000;
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return String.format("%d:%02d", seconds / 60, seconds % 60);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds % 60);
    }

    /**
     * Searches for text within the transcript segments.
     */
    public List<TranscriptSegment> searchForText(String searchText) {
        if (searchText == null || searchText.isBlank()) return List.of();
        
        String lowerSearch = searchText.toLowerCase();
        return segments.stream()
            .filter(segment -> segment.text().toLowerCase().contains(lowerSearch))
            .toList();
    }

    /**
     * Gets segments with confidence above the specified threshold.
     */
    public List<TranscriptSegment> getHighConfidenceSegments(double threshold) {
        return segments.stream()
            .filter(segment -> segment.confidence() >= threshold)
            .toList();
    }

    /**
     * Gets segments spoken by a specific speaker.
     */
    public List<TranscriptSegment> getSegmentsBySpeaker(String speakerId) {
        return segments.stream()
            .filter(segment -> speakerId.equals(segment.speakerId()))
            .toList();
    }

    /**
     * Gets segments within the specified time range.
     */
    public List<TranscriptSegment> getSegmentsInRange(Long startTimeMs, Long endTimeMs) {
        return segments.stream()
            .filter(segment -> 
                segment.startTimeMs() >= startTimeMs && 
                segment.endTimeMs() <= endTimeMs)
            .toList();
    }

    /**
     * Gets the average confidence score across all segments.
     */
    public Double getAverageConfidence() {
        if (segments.isEmpty()) return 0.0;
        return segments.stream()
            .mapToDouble(TranscriptSegment::confidence)
            .average()
            .orElse(0.0);
    }

    /**
     * Gets segments with speaker changes (dialogue boundaries).
     */
    public List<TranscriptSegment> getSpeakerChangeSegments() {
        List<TranscriptSegment> changes = new ArrayList<>();
        String lastSpeaker = null;
        
        for (TranscriptSegment segment : segments) {
            if (!segment.speakerId().equals(lastSpeaker)) {
                changes.add(segment);
                lastSpeaker = segment.speakerId();
            }
        }
        
        return changes;
    }

    /**
     * Calculates speaking time distribution by speaker.
     */
    public Map<String, Long> getSpeakingTimeBySpeaker() {
        Map<String, Long> speakingTime = new HashMap<>();
        
        for (TranscriptSegment segment : segments) {
            speakingTime.merge(
                segment.speakerId(), 
                segment.getDurationMs(), 
                Long::sum
            );
        }
        
        return speakingTime;
    }

    /**
     * Gets the most active speaker (by speaking time).
     */
    public String getMostActiveSpeaker() {
        return getSpeakingTimeBySpeaker().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");
    }

    /**
     * Checks if the transcript has high overall quality.
     */
    public boolean isHighQuality() {
        return confidenceScore != null && 
               confidenceScore >= 0.9 && 
               !segments.isEmpty() &&
               getAverageConfidence() >= 0.85;
    }

    /**
     * Adds a new segment to the transcript.
     */
    public void addSegment(TranscriptSegment segment) {
        segments.add(segment);
        updateDerivedFields();
    }

    /**
     * Updates derived fields like word count and full text.
     */
    public void updateDerivedFields() {
        // Update full text
        this.fullText = segments.stream()
            .map(TranscriptSegment::text)
            .reduce("", (a, b) -> a + " " + b)
            .trim();
        
        // Update word count
        if (fullText != null && !fullText.isBlank()) {
            this.wordCount = fullText.split("\\s+").length;
        } else {
            this.wordCount = 0;
        }
        
        // Update speaker count
        Set<String> uniqueSpeakers = segments.stream()
            .map(TranscriptSegment::speakerId)
            .collect(java.util.stream.Collectors.toSet());
        this.speakerCount = uniqueSpeakers.size();
        
        // Update average confidence if not set
        if (confidenceScore == null) {
            this.confidenceScore = getAverageConfidence();
        }
    }

    /**
     * Gets a summary of the transcript.
     */
    public String getSummary() {
        if (fullText == null || fullText.isBlank()) {
            return "No transcript content available";
        }
        
        String[] sentences = fullText.split("[.!?]+");
        if (sentences.length <= 3) {
            return fullText;
        }
        
        // Return first 3 sentences as summary
        return Arrays.stream(sentences)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .limit(3)
            .reduce("", (a, b) -> a + b + ". ") + "...";
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Generate transcript ID if not provided
        if (transcriptId == null) {
            transcriptId = "transcript-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Update derived fields
        updateDerivedFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        updateDerivedFields();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String transcriptId;
        private String tenantId;
        private UUID mediaArtifactId;
        private MediaArtifact mediaArtifact;
        private UUID processingJobId;
        private MediaProcessingJob processingJob;
        private String languageCode;
        private String detectedLanguage;
        private Double confidenceScore;
        private Integer wordCount;
        private Integer speakerCount;
        private String fullText;
        private List<TranscriptSegment> segments = new ArrayList<>();
        private Map<String, SpeakerInfo> speakers = new HashMap<>();
        private List<LanguageAlternative> alternatives = new ArrayList<>();
        private Map<String, Object> processingMetadata = new HashMap<>();
        private String searchVector;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder transcriptId(String transcriptId) {
            this.transcriptId = transcriptId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder mediaArtifactId(UUID mediaArtifactId) {
            this.mediaArtifactId = mediaArtifactId;
            return this;
        }

        public Builder mediaArtifact(MediaArtifact mediaArtifact) {
            this.mediaArtifact = mediaArtifact;
            if (mediaArtifact != null) {
                this.mediaArtifactId = mediaArtifact.getId();
            }
            return this;
        }

        public Builder processingJobId(UUID processingJobId) {
            this.processingJobId = processingJobId;
            return this;
        }

        public Builder processingJob(MediaProcessingJob processingJob) {
            this.processingJob = processingJob;
            if (processingJob != null) {
                this.processingJobId = processingJob.getId();
            }
            return this;
        }

        public Builder languageCode(String languageCode) {
            this.languageCode = languageCode;
            return this;
        }

        public Builder detectedLanguage(String detectedLanguage) {
            this.detectedLanguage = detectedLanguage;
            return this;
        }

        public Builder confidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder wordCount(Integer wordCount) {
            this.wordCount = wordCount;
            return this;
        }

        public Builder speakerCount(Integer speakerCount) {
            this.speakerCount = speakerCount;
            return this;
        }

        public Builder fullText(String fullText) {
            this.fullText = fullText;
            return this;
        }

        public Builder segments(List<TranscriptSegment> segments) {
            this.segments = segments;
            return this;
        }

        public Builder speakers(Map<String, SpeakerInfo> speakers) {
            this.speakers = speakers;
            return this;
        }

        public Builder alternatives(List<LanguageAlternative> alternatives) {
            this.alternatives = alternatives;
            return this;
        }

        public Builder processingMetadata(Map<String, Object> processingMetadata) {
            this.processingMetadata = processingMetadata;
            return this;
        }

        public Builder searchVector(String searchVector) {
            this.searchVector = searchVector;
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

        public Transcript build() {
            Transcript transcript = new Transcript();
            transcript.id = this.id;
            transcript.transcriptId = this.transcriptId;
            transcript.tenantId = this.tenantId;
            transcript.mediaArtifactId = this.mediaArtifactId;
            transcript.mediaArtifact = this.mediaArtifact;
            transcript.processingJobId = this.processingJobId;
            transcript.processingJob = this.processingJob;
            transcript.languageCode = this.languageCode;
            transcript.detectedLanguage = this.detectedLanguage;
            transcript.confidenceScore = this.confidenceScore;
            transcript.wordCount = this.wordCount;
            transcript.speakerCount = this.speakerCount;
            transcript.fullText = this.fullText;
            transcript.segments = this.segments;
            transcript.speakers = this.speakers;
            transcript.alternatives = this.alternatives;
            transcript.processingMetadata = this.processingMetadata;
            transcript.searchVector = this.searchVector;
            transcript.createdAt = this.createdAt;
            transcript.updatedAt = this.updatedAt;
            return transcript;
        }
    }

    @Override
    public String toString() {
        return "Transcript{" +
                "transcriptId='" + transcriptId + '\'' +
                ", languageCode='" + languageCode + '\'' +
                ", confidence=" + confidenceScore +
                ", wordCount=" + wordCount +
                ", speakerCount=" + speakerCount +
                '}';
    }
}
