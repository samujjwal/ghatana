package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents frame index data extracted from video media artifacts.
 *
 * <p><b>Purpose</b><br>
 * Stores structured frame extraction results including timestamps, visual
 * features, object detection results, and content analysis. Enables efficient
 * video content search, scene detection, and visual analysis workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FrameIndex frameIndex = FrameIndex.builder()
 *     .mediaArtifact(videoArtifact)
 *     .extractionMethod("keyframe")
 *     .frameIntervalMs(5000)
 *     .totalFrames(120)
 *     .frames(List.of(
 *         new FrameData(0, "frame_000.jpg", List.of("person", "car")),
 *         new FrameData(5000, "frame_001.jpg", List.of("building"))
 *     ))
 *     .build();
 * 
 * // Search for frames containing objects
 * List<FrameData> carFrames = frameIndex.searchByObjects("car");
 * 
 * // Get frames at specific time
 * FrameData frame = frameIndex.getFrameAtTime(10000);
 * }</pre>
 *
 * @see MediaArtifact
 * @see MediaProcessingJob
 * @doc.type class
 * @doc.purpose Video frame index with visual features and timestamps
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_frame_indices", indexes = {
    @Index(name = "idx_frame_index_tenant", columnList = "tenant_id"),
    @Index(name = "idx_frame_index_artifact", columnList = "media_artifact_id"),
    @Index(name = "idx_frame_index_job", columnList = "processing_job_id"),
    @Index(name = "idx_frame_index_method", columnList = "extraction_method"),
    @Index(name = "idx_frame_index_created", columnList = "created_at")
})
public class FrameIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "frame_index_id", nullable = false, unique = true, length = 255)
    private String frameIndexId;

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

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "extraction_method", nullable = false, length = 50)
    private ExtractionMethod extractionMethod;

    @Column(name = "frame_interval_ms")
    private Long frameIntervalMs;

    @Column(name = "total_frames")
    private Integer totalFrames;

    @Column(name = "resolution_width")
    private Integer resolutionWidth;

    @Column(name = "resolution_height")
    private Integer resolutionHeight;

    @Column(name = "quality_score")
    private Double qualityScore;

    /**
     * Frame data with timestamps and visual features.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frames", columnDefinition = "jsonb")
    private List<FrameData> frames = new ArrayList<>();

    /**
     * Scene boundaries and shot changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenes", columnDefinition = "jsonb")
    private List<SceneBoundary> scenes = new ArrayList<>();

    /**
     * Object detection results across all frames.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_objects", columnDefinition = "jsonb")
    private Map<String, List<ObjectDetection>> detectedObjects = new HashMap<>();

    /**
     * Visual features and embeddings.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visual_features", columnDefinition = "jsonb")
    private Map<String, Object> visualFeatures = new HashMap<>();

    /**
     * Processing metadata and performance metrics.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processing_metadata", columnDefinition = "jsonb")
    private Map<String, Object> processingMetadata = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Frame extraction methods.
     */
    public enum ExtractionMethod {
        UNIFORM,              // Fixed interval extraction
        KEYFRAME,            // Keyframe-based extraction
        SCENE_CHANGE,        // Scene change detection
        MOTION_BASED,        // Motion-based extraction
        CONTENT_AWARE,       // Content-aware extraction
        CUSTOM               // Custom extraction logic
    }

    /**
     * Individual frame data with visual analysis.
     */
    public record FrameData(
        Long timestampMs,
        String frameUri,
        Integer frameNumber,
        Double confidence,
        List<String> detectedObjects,
        List<String> detectedFaces,
        Map<String, Object> features,
        Map<String, Object> metadata
    ) {
        public FrameData {
            if (timestampMs == null) timestampMs = 0L;
            if (frameNumber == null) frameNumber = 0;
            if (confidence == null) confidence = 0.0;
            if (detectedObjects == null) detectedObjects = List.of();
            if (detectedFaces == null) detectedFaces = List.of();
            if (features == null) features = Map.of();
            if (metadata == null) metadata = Map.of();
        }
    }

    /**
     * Scene boundary information.
     */
    public record SceneBoundary(
        Long startTimeMs,
        Long endTimeMs,
        String sceneType,
        Double confidence,
        List<String> keyFrames,
        Map<String, Object> characteristics
    ) {
        public SceneBoundary {
            if (sceneType == null) sceneType = "unknown";
            if (confidence == null) confidence = 0.0;
            if (keyFrames == null) keyFrames = List.of();
            if (characteristics == null) characteristics = Map.of();
        }

        public Long getDurationMs() {
            return endTimeMs - startTimeMs;
        }
    }

    /**
     * Object detection result.
     */
    public record ObjectDetection(
        String objectId,
        String objectClass,
        Double confidence,
        String boundingBox,
        Long timestampMs,
        Map<String, Object> attributes
    ) {
        public ObjectDetection {
            if (confidence == null) confidence = 0.0;
            if (boundingBox == null) boundingBox = "";
            if (timestampMs == null) timestampMs = 0L;
            if (attributes == null) attributes = Map.of();
        }
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFrameIndexId() {
        return frameIndexId;
    }

    public void setFrameIndexId(String frameIndexId) {
        this.frameIndexId = frameIndexId;
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

    public ExtractionMethod getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(ExtractionMethod extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public Long getFrameIntervalMs() {
        return frameIntervalMs;
    }

    public void setFrameIntervalMs(Long frameIntervalMs) {
        this.frameIntervalMs = frameIntervalMs;
    }

    public Integer getTotalFrames() {
        return totalFrames;
    }

    public void setTotalFrames(Integer totalFrames) {
        this.totalFrames = totalFrames;
    }

    public Integer getResolutionWidth() {
        return resolutionWidth;
    }

    public void setResolutionWidth(Integer resolutionWidth) {
        this.resolutionWidth = resolutionWidth;
    }

    public Integer getResolutionHeight() {
        return resolutionHeight;
    }

    public void setResolutionHeight(Integer resolutionHeight) {
        this.resolutionHeight = resolutionHeight;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public List<FrameData> getFrames() {
        return frames;
    }

    public void setFrames(List<FrameData> frames) {
        this.frames = frames;
    }

    public List<SceneBoundary> getScenes() {
        return scenes;
    }

    public void setScenes(List<SceneBoundary> scenes) {
        this.scenes = scenes;
    }

    public Map<String, List<ObjectDetection>> getDetectedObjects() {
        return detectedObjects;
    }

    public void setDetectedObjects(Map<String, List<ObjectDetection>> detectedObjects) {
        this.detectedObjects = detectedObjects;
    }

    public Map<String, Object> getVisualFeatures() {
        return visualFeatures;
    }

    public void setVisualFeatures(Map<String, Object> visualFeatures) {
        this.visualFeatures = visualFeatures;
    }

    public Map<String, Object> getProcessingMetadata() {
        return processingMetadata;
    }

    public void setProcessingMetadata(Map<String, Object> processingMetadata) {
        this.processingMetadata = processingMetadata;
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
     * Gets the total duration covered by the frame index.
     */
    public Long getTotalDurationMs() {
        if (frames.isEmpty()) return 0L;
        return frames.get(frames.size() - 1).timestampMs();
    }

    /**
     * Gets the frame at the specified timestamp.
     */
    public FrameData getFrameAtTime(Long timestampMs) {
        return frames.stream()
            .filter(frame -> frame.timestampMs().equals(timestampMs))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the closest frame to the specified timestamp.
     */
    public FrameData getClosestFrame(Long timestampMs) {
        return frames.stream()
            .min(Comparator.comparing(frame -> 
                Math.abs(frame.timestampMs() - timestampMs)))
            .orElse(null);
    }

    /**
     * Gets frames within the specified time range.
     */
    public List<FrameData> getFramesInRange(Long startTimeMs, Long endTimeMs) {
        return frames.stream()
            .filter(frame -> 
                frame.timestampMs() >= startTimeMs && 
                frame.timestampMs() <= endTimeMs)
            .toList();
    }

    /**
     * Searches for frames containing specific objects.
     */
    public List<FrameData> searchByObjects(String objectClass) {
        return frames.stream()
            .filter(frame -> frame.detectedObjects().contains(objectClass))
            .toList();
    }

    /**
     * Searches for frames containing faces.
     */
    public List<FrameData> getFramesWithFaces() {
        return frames.stream()
            .filter(frame -> !frame.detectedFaces().isEmpty())
            .toList();
    }

    /**
     * Gets high-quality frames above the specified confidence threshold.
     */
    public List<FrameData> getHighQualityFrames(double threshold) {
        return frames.stream()
            .filter(frame -> frame.confidence() >= threshold)
            .toList();
    }

    /**
     * Gets all unique object classes detected across all frames.
     */
    public Set<String> getAllDetectedObjects() {
        return frames.stream()
            .flatMap(frame -> frame.detectedObjects().stream())
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Gets object detection results for a specific class.
     */
    public List<ObjectDetection> getDetectionsForClass(String objectClass) {
        return detectedObjects.getOrDefault(objectClass, List.of());
    }

    /**
     * Gets the scene containing the specified timestamp.
     */
    public SceneBoundary getSceneAtTime(Long timestampMs) {
        return scenes.stream()
            .filter(scene -> 
                timestampMs >= scene.startTimeMs() && 
                timestampMs <= scene.endTimeMs())
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets scene changes (boundaries) in chronological order.
     */
    public List<Long> getSceneChangeTimestamps() {
        return scenes.stream()
            .map(SceneBoundary::startTimeMs)
            .sorted()
            .toList();
    }

    /**
     * Calculates frame density (frames per second).
     */
    public Double getFrameDensity() {
        if (frames.size() < 2 || frameIntervalMs == null) return 0.0;
        return 1000.0 / frameIntervalMs;
    }

    /**
     * Gets the average confidence score across all frames.
     */
    public Double getAverageConfidence() {
        if (frames.isEmpty()) return 0.0;
        return frames.stream()
            .mapToDouble(FrameData::confidence)
            .average()
            .orElse(0.0);
    }

    /**
     * Gets the most frequently detected object class.
     */
    public String getMostFrequentObject() {
        return getAllDetectedObjects().stream()
            .max(Comparator.comparing(objClass -> 
                frames.stream()
                    .filter(frame -> frame.detectedObjects().contains(objClass))
                    .count()))
            .orElse("none");
    }

    /**
     * Checks if the frame index has good coverage of the video.
     */
    public boolean hasGoodCoverage() {
        if (frames.isEmpty()) return false;
        
        // Check if we have reasonable frame density
        double density = getFrameDensity();
        if (density < 0.1) return false; // Less than 1 frame per 10 seconds
        
        // Check if confidence is reasonable
        double avgConfidence = getAverageConfidence();
        if (avgConfidence < 0.7) return false;
        
        return true;
    }

    /**
     * Adds a new frame to the index.
     */
    public void addFrame(FrameData frame) {
        frames.add(frame);
        Collections.sort(frames, Comparator.comparing(FrameData::timestampMs));
        updateDerivedFields();
    }

    /**
     * Adds object detection results.
     */
    public void addObjectDetection(String objectClass, ObjectDetection detection) {
        detectedObjects.computeIfAbsent(objectClass, k -> new ArrayList<>()).add(detection);
    }

    /**
     * Adds a scene boundary.
     */
    public void addSceneBoundary(SceneBoundary scene) {
        scenes.add(scene);
        Collections.sort(scenes, Comparator.comparing(SceneBoundary::startTimeMs));
    }

    /**
     * Updates derived fields like total frames and quality score.
     */
    public void updateDerivedFields() {
        this.totalFrames = frames.size();
        
        // Update quality score if not set
        if (qualityScore == null) {
            this.qualityScore = getAverageConfidence();
        }
        
        // Update frame interval if not set and we have multiple frames
        if (frameIntervalMs == null && frames.size() > 1) {
            this.frameIntervalMs = frames.get(1).timestampMs() - frames.get(0).timestampMs();
        }
    }

    /**
     * Gets a summary of the frame index.
     */
    public String getSummary() {
        return String.format(
            "FrameIndex: %d frames, %d scenes, %d object types, %.2f fps, %.1f%% confidence",
            totalFrames,
            scenes.size(),
            getAllDetectedObjects().size(),
            getFrameDensity(),
            getAverageConfidence() * 100
        );
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Generate frame index ID if not provided
        if (frameIndexId == null) {
            frameIndexId = "frame-index-" + UUID.randomUUID().toString().substring(0, 8);
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
        private String frameIndexId;
        private String tenantId;
        private UUID mediaArtifactId;
        private MediaArtifact mediaArtifact;
        private UUID processingJobId;
        private MediaProcessingJob processingJob;
        private ExtractionMethod extractionMethod;
        private Long frameIntervalMs;
        private Integer totalFrames;
        private Integer resolutionWidth;
        private Integer resolutionHeight;
        private Double qualityScore;
        private List<FrameData> frames = new ArrayList<>();
        private List<SceneBoundary> scenes = new ArrayList<>();
        private Map<String, List<ObjectDetection>> detectedObjects = new HashMap<>();
        private Map<String, Object> visualFeatures = new HashMap<>();
        private Map<String, Object> processingMetadata = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder frameIndexId(String frameIndexId) {
            this.frameIndexId = frameIndexId;
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

        public Builder extractionMethod(ExtractionMethod extractionMethod) {
            this.extractionMethod = extractionMethod;
            return this;
        }

        public Builder frameIntervalMs(Long frameIntervalMs) {
            this.frameIntervalMs = frameIntervalMs;
            return this;
        }

        public Builder totalFrames(Integer totalFrames) {
            this.totalFrames = totalFrames;
            return this;
        }

        public Builder resolutionWidth(Integer resolutionWidth) {
            this.resolutionWidth = resolutionWidth;
            return this;
        }

        public Builder resolutionHeight(Integer resolutionHeight) {
            this.resolutionHeight = resolutionHeight;
            return this;
        }

        public Builder qualityScore(Double qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public Builder frames(List<FrameData> frames) {
            this.frames = frames;
            return this;
        }

        public Builder scenes(List<SceneBoundary> scenes) {
            this.scenes = scenes;
            return this;
        }

        public Builder detectedObjects(Map<String, List<ObjectDetection>> detectedObjects) {
            this.detectedObjects = detectedObjects;
            return this;
        }

        public Builder visualFeatures(Map<String, Object> visualFeatures) {
            this.visualFeatures = visualFeatures;
            return this;
        }

        public Builder processingMetadata(Map<String, Object> processingMetadata) {
            this.processingMetadata = processingMetadata;
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

        public FrameIndex build() {
            FrameIndex frameIndex = new FrameIndex();
            frameIndex.id = this.id;
            frameIndex.frameIndexId = this.frameIndexId;
            frameIndex.tenantId = this.tenantId;
            frameIndex.mediaArtifactId = this.mediaArtifactId;
            frameIndex.mediaArtifact = this.mediaArtifact;
            frameIndex.processingJobId = this.processingJobId;
            frameIndex.processingJob = this.processingJob;
            frameIndex.extractionMethod = this.extractionMethod;
            frameIndex.frameIntervalMs = this.frameIntervalMs;
            frameIndex.totalFrames = this.totalFrames;
            frameIndex.resolutionWidth = this.resolutionWidth;
            frameIndex.resolutionHeight = this.resolutionHeight;
            frameIndex.qualityScore = this.qualityScore;
            frameIndex.frames = this.frames;
            frameIndex.scenes = this.scenes;
            frameIndex.detectedObjects = this.detectedObjects;
            frameIndex.visualFeatures = this.visualFeatures;
            frameIndex.processingMetadata = this.processingMetadata;
            frameIndex.createdAt = this.createdAt;
            frameIndex.updatedAt = this.updatedAt;
            return frameIndex;
        }
    }

    @Override
    public String toString() {
        return "FrameIndex{" +
                "frameIndexId='" + frameIndexId + '\'' +
                ", extractionMethod=" + extractionMethod +
                ", totalFrames=" + totalFrames +
                ", qualityScore=" + qualityScore +
                '}';
    }
}
