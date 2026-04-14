package com.ghatana.stt.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import io.activej.eventloop.Eventloop;
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
 * @doc.purpose STT service with persistence integration.
 *              Wraps the platform SttEngine and persists audio files and transcriptions.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentSttService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentSttService.class);

    private final AudioVideoLibrary library;
    private final AudioFileService audioFileService;
    private final TranscriptionService transcriptionService;
    private final Timer transcribeTimer;

    public PersistentSttService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry meterRegistry) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService cannot be null");
        this.transcribeTimer = Timer.builder("stt.persistent.transcribe")
            .description("Persistent transcription latency")
            .register(meterRegistry);
    }

    /**
     * Transcribe audio and persist the results.
     *
     * @param tenantId    the tenant ID
     * @param userId      the user ID
     * @param audioBytes  the audio data
     * @param fileName    the original file name
     * @param format      the audio format
     * @param language    the language hint (optional)
     * @param sampleRate  the sample rate
     * @return Promise containing the transcription result with persisted IDs
     */
    public Promise<TranscriptionResult> transcribeAndPersist(
            String tenantId,
            UUID userId,
            byte[] audioBytes,
            String fileName,
            AudioFormat format,
            String language,
            int sampleRate) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(audioBytes, "audioBytes cannot be null");

        long startTime = System.currentTimeMillis();

        // Step 1: Persist audio file metadata
        return persistAudioFile(tenantId, userId, fileName, audioBytes.length, format, sampleRate)
            .then(audioFile -> {
                LOG.info("[tenant={}] Audio file persisted: id={}, file={}",
                    tenantId, audioFile.getId(), fileName);

                // Step 2: Perform transcription
                return performTranscription(audioBytes, sampleRate, language)
                    .then(transcriptionResult -> {
                        // Step 3: Persist transcription result
                        return persistTranscription(tenantId, audioFile.getId(), transcriptionResult)
                            .map(transcription -> {
                                long elapsedMs = System.currentTimeMillis() - startTime;
                                transcribeTimer.record(Duration.ofMillis(elapsedMs));

                                LOG.info("[tenant={}] Transcription completed: audioId={}, transcriptionId={}, " +
                                    "confidence={}, elapsedMs={}",
                                    tenantId, audioFile.getId(), transcription.getId(),
                                    transcriptionResult.confidence(), elapsedMs);

                                return transcriptionResult;
                            });
                    })
                    .whenException(e -> {
                        // Update audio file status to FAILED on transcription error
                        LOG.error("[tenant={}] Transcription failed for audioId={}: {}",
                            tenantId, audioFile.getId(), e.getMessage(), e);
                        updateAudioFileStatus(tenantId, audioFile.getId(),
                            AudioFileEntity.ProcessingStatus.FAILED);
                    });
            });
    }

    /**
     * Get transcription by audio file ID.
     */
    public Promise<Optional<TranscriptionEntity>> getTranscription(String tenantId, UUID audioFileId) {
        return transcriptionService.findByAudioFileId(tenantId, audioFileId);
    }

    /**
     * Get all transcriptions for a tenant.
     */
    public Promise<java.util.List<TranscriptionEntity>> getTranscriptions(String tenantId) {
        return transcriptionService.findByTenantId(tenantId);
    }

    /**
     * Delete transcription (soft delete).
     */
    public Promise<Boolean> deleteTranscription(String tenantId, UUID transcriptionId) {
        return transcriptionService.softDelete(tenantId, transcriptionId);
    }

    private Promise<AudioFileEntity> persistAudioFile(
            String tenantId,
            UUID userId,
            String fileName,
            int fileSize,
            AudioFormat format,
            int sampleRate) {

        String extension = getExtension(fileName);

        AudioFileEntity entity = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            userId,
            fileName,
            "/storage/audio/" + tenantId + "/" + UUID.randomUUID() + "." + extension,
            extension
        );
        entity.setFileSizeBytes((long) fileSize);
        entity.setSampleRate(sampleRate);
        entity.setStatus(AudioFileEntity.ProcessingStatus.PROCESSING);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    private Promise<TranscriptionResult> performTranscription(
            byte[] audioBytes,
            int sampleRate,
            String language) {

        return Promise.ofCallable(() -> {
            AudioData audio = new AudioData(audioBytes, sampleRate, 1, 16, Duration.ZERO, AudioFormat.PCM);

            try (SttEngine stt = library.getSttEngine()) {
                TranscriptionOptions options = TranscriptionOptions.builder()
                    .languageHint(Optional.ofNullable(language))
                    .build();
                return stt.transcribe(audio, options);
            }
        });
    }

    private Promise<TranscriptionEntity> persistTranscription(
            String tenantId,
            UUID audioFileId,
            TranscriptionResult result) {

        TranscriptionEntity entity = new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            audioFileId,
            result.text(),
            result.detectedLanguage() != null ? result.detectedLanguage() : "unknown"
        );
        entity.setConfidence((float) result.confidence());
        entity.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED);
        entity.setWordCount(result.text().split("\\s+").length);
        entity.setMetadata("{\"model\":\"" + result.modelId() + "\",\"processingTimeMs\":" + result.processingTime().toMillis() + "}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return transcriptionService.save(tenantId, entity)
            .then(transcription -> {
                // Update audio file status to COMPLETED
                return updateAudioFileStatus(tenantId, audioFileId,
                        AudioFileEntity.ProcessingStatus.COMPLETED)
                    .map(__ -> transcription);
            });
    }

    private Promise<Boolean> updateAudioFileStatus(
            String tenantId,
            UUID audioFileId,
            AudioFileEntity.ProcessingStatus status) {

        // Note: This would require a status update method in the service
        // For now, we'll just return a completed promise
        // In production, implement AudioFileService.updateStatus()
        return Promise.of(true);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "audio";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
