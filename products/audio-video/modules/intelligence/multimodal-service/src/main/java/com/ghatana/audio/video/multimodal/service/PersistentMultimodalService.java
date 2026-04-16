package com.ghatana.audio.video.multimodal.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.common.ImageData;
import com.ghatana.media.common.ImageFormat;
import com.ghatana.media.common.ColorSpace;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.media.vision.api.VisionEngine;
import com.ghatana.media.vision.api.DetectionResult;
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

    /** Default sample rate assumed for raw PCM audio data passed to this service. */
    private static final int DEFAULT_SAMPLE_RATE_HZ = 16_000;

    private final AudioFileService audioFileService;
    private final TranscriptionService transcriptionService;
    private final SttEngine sttEngine;
    private final VisionEngine visionEngine;
    private final Timer fuseTimer;

    /**
     * Convenience constructor — engines not yet wired; audio and visual processing will
     * throw {@link UnsupportedOperationException} until real engines are supplied.
     */
    public PersistentMultimodalService(
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry meterRegistry) {
        this(audioFileService, transcriptionService, null, null, meterRegistry);
    }

    public PersistentMultimodalService(
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            SttEngine sttEngine,
            VisionEngine visionEngine,
            MeterRegistry meterRegistry) {
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService cannot be null");
        this.sttEngine = sttEngine;   // nullable — checked at call time
        this.visionEngine = visionEngine; // nullable — checked at call time
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

        if (sttEngine == null) {
            return Promise.ofException(new UnsupportedOperationException(
                "SttEngine is not configured. Inject a real SttEngine implementation " +
                "(e.g. WhisperTranscriptionEngine) to enable audio transcription."));
        }

        AudioData audio = new AudioData(audioData, DEFAULT_SAMPLE_RATE_HZ, 1, 16);
        TranscriptionResult result;
        try {
            result = sttEngine.transcribe(audio);
        } catch (Exception e) {
            LOG.error("[tenant={}] STT transcription failed for audioFileId={}: {}", tenantId, audioFileId, e.getMessage(), e);
            return Promise.ofException(new RuntimeException("Transcription failed: " + e.getMessage(), e));
        }

        TranscriptionEntity entity = new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            audioFileId,
            userId,
            result.text(),
            result.language() != null ? result.language() : "en"
        );
        entity.setConfidence((float) result.confidence());
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

        if (visionEngine == null) {
            return Promise.ofException(new UnsupportedOperationException(
                "VisionEngine is not configured. Inject a real VisionEngine implementation " +
                "to enable visual analysis."));
        }

        // Image dimensions must be known for correct inference; if the caller supplies
        // raw encoded bytes (JPEG/PNG) the engine is expected to handle them via the
        // declared ImageFormat.  We use a nominal sentinel (width=0, height=0 signals
        // "unknown") which requires the engine to decode dimensions from the header.
        // Use the overload processVisual(tenantId, mediaFileId, visualData, width, height)
        // when the caller can supply explicit dimensions for higher accuracy.
        ImageData image = ImageData.builder()
            .data(visualData)
            .width(1)   // minimal valid value; engine decodes actual dimensions from header
            .height(1)  // minimal valid value
            .format(ImageFormat.JPEG)
            .colorSpace(ColorSpace.RGB)
            .build();

        return visionEngine.detectAsync(image, com.ghatana.media.vision.api.DetectionOptions.defaults())
            .map(detection -> {
                java.util.List<String> labels = detection.objects().stream()
                    .map(com.ghatana.media.vision.api.DetectedObject::className)
                    .toList();
                LOG.debug("[tenant={}] Visual detection for mediaId={}: {} objects detected",
                    tenantId, mediaFileId, detection.count());
                return new VisualAnalysis(detection.count() > 0, detection.count(), labels);
            })
            .whenException(e ->
                LOG.error("[tenant={}] Vision detection failed for mediaId={}: {}",
                    tenantId, mediaFileId, e.getMessage(), e)
            );
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
