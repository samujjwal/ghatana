package com.ghatana.audio.video.multimodal.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Multimodal service with persistence integration.
 *              Combines audio transcription with visual analysis and persists results.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentMultimodalService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentMultimodalService.class);

    private final AudioFileService audioFileService;
    private final TranscriptionService transcriptionService;
    private final Timer fuseTimer;

    public PersistentMultimodalService(
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry meterRegistry) {
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService cannot be null");
        this.fuseTimer = Timer.builder("multimodal.persistent.fuse")
            .description("Multimodal fusion latency with persistence")
            .register(meterRegistry);
    }

    /**
     * Process audio-visual content and persist results.
     *
     * @param tenantId    the tenant ID
     * @param userId      the user ID
     * @param audioData   the audio data
     * @param visualData  the visual data (video frames or image)
     * @param fileName    the original file name
     * @return Promise containing fused multimodal analysis
     */
    public Promise<MultimodalResult> processAndPersist(
            String tenantId,
            UUID userId,
            byte[] audioData,
            byte[] visualData,
            String fileName) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");

        long startTime = System.currentTimeMillis();

        // Step 1: Persist the source media file
        return persistMediaFile(tenantId, userId, fileName,
            (audioData != null ? audioData.length : 0) +
            (visualData != null ? visualData.length : 0))
            .then(mediaFile -> {
                LOG.info("[tenant={}] Media file persisted: id={}, file={}",
                    tenantId, mediaFile.getId(), fileName);

                // Step 2: Process audio (transcription)
                Promise<Optional<TranscriptionEntity>> audioPromise =
                    processAudio(tenantId, userId, mediaFile.getId(), audioData);

                // Step 3: Process visual (scene understanding)
                Promise<VisualAnalysis> visualPromise =
                    processVisual(tenantId, mediaFile.getId(), visualData);

                // Step 4: Fuse results
                return audioPromise.combine(visualPromise, (transcription, visualAnalysis) -> {

                        long elapsedMs = System.currentTimeMillis() - startTime;
                        fuseTimer.record(Duration.ofMillis(elapsedMs));

                        LOG.info("[tenant={}] Multimodal fusion completed: mediaId={}, " +
                            "hasAudio={}, hasVisual={}, elapsedMs={}",
                            tenantId, mediaFile.getId(),
                            transcription.isPresent(), visualAnalysis.hasDetections(), elapsedMs);

                        return new MultimodalResult(
                            mediaFile.getId(),
                            transcription.map(TranscriptionEntity::getText).orElse(null),
                            transcription.map(TranscriptionEntity::getConfidence).orElse(0f),
                            visualAnalysis,
                            elapsedMs
                        );
                    });
            })
            .whenException(e -> {
                LOG.error("[tenant={}] Multimodal processing failed: {}", tenantId, e.getMessage(), e);
            });
    }

    /**
     * Search across all persisted multimodal content.
     */
    public Promise<java.util.List<MultimodalSearchResult>> search(String tenantId, String query) {
        // Search transcriptions
        return transcriptionService.findByTenantId(tenantId)
            .map(transcriptions -> {
                return transcriptions.stream()
                    .filter(t -> t.getText().toLowerCase().contains(query.toLowerCase()))
                    .map(t -> new MultimodalSearchResult(
                        t.getAudioFileId(),
                        "transcription",
                        t.getText(),
                        t.getConfidence(),
                        t.getCreatedAt()
                    ))
                    .toList();
            });
    }

    private Promise<AudioFileEntity> persistMediaFile(
            String tenantId,
            UUID userId,
            String fileName,
            int fileSize) {

        String extension = getExtension(fileName);

        AudioFileEntity entity = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            userId,
            fileName,
            "/storage/multimodal/" + tenantId + "/" + UUID.randomUUID() + "." + extension,
            extension
        );
        entity.setFileSizeBytes((long) fileSize);
        entity.setStatus(AudioFileEntity.ProcessingStatus.PROCESSING);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    private Promise<Optional<TranscriptionEntity>> processAudio(
            String tenantId,
            UUID userId,
            UUID audioFileId,
            byte[] audioData) {

        if (audioData == null || audioData.length == 0) {
            return Promise.of(Optional.empty());
        }

        // In production, this would call the STT service
        // For now, create a placeholder transcription
        TranscriptionEntity entity = new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            audioFileId,
            userId,
            "[Audio transcription placeholder]",
            "en"
        );
        entity.setConfidence(0.85f);
        entity.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return transcriptionService.save(tenantId, entity)
            .map(Optional::of);
    }

    private Promise<VisualAnalysis> processVisual(String tenantId, UUID mediaFileId, byte[] visualData) {
        if (visualData == null || visualData.length == 0) {
            return Promise.of(new VisualAnalysis(false, 0, java.util.List.of()));
        }

        // In production, this would call the Vision service
        // For now, return placeholder analysis
        return Promise.of(new VisualAnalysis(
            true,
            3, // placeholder detection count
            java.util.List.of("person", "object", "scene")
        ));
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "mp4";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Result of multimodal fusion.
     */
    public record MultimodalResult(
        UUID mediaFileId,
        String transcription,
        float transcriptionConfidence,
        VisualAnalysis visualAnalysis,
        long processingTimeMs
    ) {}

    /**
     * Visual analysis result.
     */
    public record VisualAnalysis(
        boolean hasDetections,
        int detectionCount,
        java.util.List<String> detectedObjects
    ) {}

    /**
     * Search result.
     */
    public record MultimodalSearchResult(
        UUID mediaFileId,
        String resultType,
        String content,
        float relevance,
        Instant createdAt
    ) {}
}
