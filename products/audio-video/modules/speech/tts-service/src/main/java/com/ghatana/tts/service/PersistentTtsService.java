package com.ghatana.tts.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.tts.api.SynthesisOptions;
import com.ghatana.media.tts.api.TtsEngine;
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
 * @doc.purpose TTS service with persistence integration.
 *              Wraps the platform TtsEngine and persists generated audio files.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentTtsService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentTtsService.class);

    private final AudioVideoLibrary library;
    private final AudioFileService audioFileService;
    private final Timer synthesizeTimer;

    public PersistentTtsService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            MeterRegistry meterRegistry) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.synthesizeTimer = Timer.builder("tts.persistent.synthesize")
            .description("Persistent synthesis latency")
            .register(meterRegistry);
    }

    /**
     * Synthesize text to speech and persist the audio file.
     *
     * @param tenantId   the tenant ID
     * @param userId     the user ID
     * @param text       the text to synthesize
     * @param voiceId    the voice ID (optional)
     * @param speed      the speed factor (1.0 = normal)
     * @param pitch      the pitch factor (1.0 = normal)
     * @param language   the language code
     * @return Promise containing the synthesized audio data with persisted file ID
     */
    public Promise<SynthesisResult> synthesizeAndPersist(
            String tenantId,
            UUID userId,
            String text,
            Optional<String> voiceId,
            float speed,
            float pitch,
            String language) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(text, "text cannot be null");

        long startTime = System.currentTimeMillis();

        return Promise.ofCallable(() -> {
            // Step 1: Perform synthesis
            try (TtsEngine tts = library.getTtsEngine()) {
                SynthesisOptions options = SynthesisOptions.builder()
                    .voiceId(voiceId.orElse(null))
                    .speed(speed)
                    .pitch(pitch)
                    .language(language != null ? java.util.Locale.forLanguageTag(language) : null)
                    .build();

                AudioData audioData = tts.synthesize(text, options);
                return audioData;
            }
        }).then(audioData -> {
            // Step 2: Persist audio file metadata
            String fileName = generateFileName(text, voiceId.orElse("default"));
            return persistAudioFile(tenantId, userId, fileName, audioData.data().length, "wav", audioData.sampleRate())
                .map(audioFile -> {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    synthesizeTimer.record(Duration.ofMillis(elapsedMs));

                    LOG.info("[tenant={}] TTS synthesis completed: audioId={}, textLength={}, elapsedMs={}",
                        tenantId, audioFile.getId(), text.length(), elapsedMs);

                    return new SynthesisResult(
                        audioFile.getId(),
                        audioData.data(),
                        audioData.sampleRate(),
                        elapsedMs
                    );
                });
        }).whenException(e -> {
            LOG.error("[tenant={}] TTS synthesis failed: {}", tenantId, e.getMessage(), e);
        });
    }

    private Promise<com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity> persistAudioFile(
            String tenantId,
            UUID userId,
            String fileName,
            int fileSize,
            String format,
            int sampleRate) {

        com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity entity =
            new com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity(
                UUID.randomUUID(),
                tenantId,
                userId,
                fileName,
                "/storage/tts/" + tenantId + "/" + UUID.randomUUID() + "." + format,
                format
            );
        entity.setFileSizeBytes((long) fileSize);
        entity.setSampleRate(sampleRate);
        entity.setDurationSeconds((int) (fileSize / (sampleRate * 2.0))); // Approximate
        entity.setStatus(com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity.ProcessingStatus.COMPLETED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    private String generateFileName(String text, String voiceId) {
        String baseName = text.length() > 30 ? text.substring(0, 30).replaceAll("\\W+", "_") : text.replaceAll("\\W+", "_");
        return "tts_" + voiceId + "_" + baseName + "_" + System.currentTimeMillis() + ".wav";
    }

    /**
     * Result of TTS synthesis with persisted audio file ID.
     */
    public record SynthesisResult(
        UUID audioFileId,
        byte[] audioData,
        int sampleRate,
        long processingTimeMs
    ) {}
}
