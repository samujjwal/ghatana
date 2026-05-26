package com.ghatana.tts.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.tts.api.SynthesisOptions;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * @doc.type class
 * @doc.purpose TTS service with persistence integration.
 *              Wraps the platform TtsEngine and persists generated audio files.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentTtsService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentTtsService.class);

    // AV-P1-010: Validation constants
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 2.0f;
    private static final float MIN_PITCH = 0.5f;
    private static final float MAX_PITCH = 2.0f;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ne", "hi", "es", "fr", "de", "ja", "zh");
    
    // AV-P1-011: Timeout and degraded mode constants
    private static final long DEFAULT_SYNTHESIS_TIMEOUT_MS = 60000; // 1 minute
    private volatile boolean degraded = false;
    private volatile String degradationReason = null;

    private final AudioVideoLibrary library;
    private final AudioFileService audioFileService;
    private final Timer synthesizeTimer;
    private final Executor blockingExecutor;
    private final AuditService auditService;

    public PersistentTtsService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            MeterRegistry meterRegistry) {
        this(library, audioFileService, meterRegistry, null);
    }

    public PersistentTtsService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            MeterRegistry meterRegistry,
            @Nullable AuditService auditService) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.auditService = auditService;
        this.synthesizeTimer = Timer.builder("tts.persistent.synthesize")
            .description("Persistent synthesis latency")
            .register(meterRegistry);
        this.blockingExecutor = ForkJoinPool.commonPool();
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

        // AV-P1-010: Validate input before processing
        validateTtsInput(text, voiceId, speed, pitch, language);

        long startTime = System.currentTimeMillis();

        // AV-P1-011: Check degraded mode before processing
        if (degraded) {
            LOG.warn("TTS service is in degraded mode: {}", degradationReason);
            return Promise.ofException(new IllegalStateException(
                "TTS service is degraded: " + degradationReason));
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
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
            return persistAudioFile(tenantId, userId, fileName, audioData.data().length, "wav", audioData.sampleRate(), audioData)
                .map(audioFile -> {
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    synthesizeTimer.record(Duration.ofMillis(elapsedMs));

                    LOG.info("[tenant={}] TTS synthesis completed: audioId={}, textLength={}, elapsedMs={}",
                        tenantId, audioFile.getId(), text.length(), elapsedMs);

                    // AV-P1-011: Emit audit event for successful synthesis
                    emitTtsAudit(tenantId, userId.toString(), "tts.synthesize", "SUCCESS",
                        Map.of("audioFileId", audioFile.getId().toString(),
                               "textLength", String.valueOf(text.length()),
                               "voiceId", voiceId.orElse("default"),
                               "language", language != null ? language : "default",
                               "processingTimeMs", String.valueOf(elapsedMs),
                               "audioSizeBytes", String.valueOf(audioData.data().length)));

                    return new SynthesisResult(
                        audioFile.getId(),
                        audioData.data(),
                        audioData.sampleRate(),
                        elapsedMs
                    );
                });
        }).whenException(e -> {
            // AV-P1-009: Persist failed status for synthesis failures
            LOG.error("[tenant={}] TTS synthesis failed: {}", tenantId, e.getMessage(), e);
            
            // Persist a failed audio file record
            String fileName = generateFileName(text, voiceId.orElse("default"));
            persistFailedAudioFile(tenantId, userId, fileName, text.length(), e.getMessage());
            
            // AV-P1-011: Emit audit event for synthesis failure
            emitTtsAudit(tenantId, userId.toString(), "tts.synthesize", "FAILED",
                Map.of("error", e.getMessage(), "textLength", String.valueOf(text.length())));
        });
    }

    private Promise<com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity> persistAudioFile(
            String tenantId,
            UUID userId,
            String fileName,
            int fileSize,
            String format,
            int sampleRate,
            AudioData audioData) {

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
        // AV-P1-010: Calculate duration from AudioData duration if available, otherwise approximate
        if (audioData.duration() != null && !audioData.duration().isZero()) {
            entity.setDurationSeconds((int) audioData.duration().getSeconds());
        } else {
            // Fallback approximation: fileSize / (sampleRate * channels * bytesPerSample)
            entity.setDurationSeconds((int) (fileSize / (sampleRate * 2.0)));
        }
        entity.setStatus(com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity.ProcessingStatus.COMPLETED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    /**
     * AV-P1-009: Persist a failed TTS synthesis record.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param fileName the file name
     * @param textLength the text length
     * @param errorMessage the error message
     * @return promise completing when failed record is persisted
     */
    private Promise<Void> persistFailedAudioFile(String tenantId, UUID userId, String fileName,
                                                  int textLength, String errorMessage) {
        com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity entity =
            new com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity(
                UUID.randomUUID(),
                tenantId,
                userId,
                fileName,
                "/storage/tts/" + tenantId + "/" + UUID.randomUUID() + ".failed",
                "failed"
            );
        entity.setFileSizeBytes(0L);
        entity.setSampleRate(0);
        entity.setDurationSeconds(0);
        entity.setStatus(com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity.ProcessingStatus.FAILED);
        entity.setFailureReason(errorMessage);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity)
            .then(saved -> io.activej.promise.Promise.of((Void) null))
            .whenException(e -> LOG.error("Failed to persist failed TTS record: {}", e.getMessage()));
    }

    private String generateFileName(String text, String voiceId) {
        String baseName = text.length() > 30 ? text.substring(0, 30).replaceAll("\\W+", "_") : text.replaceAll("\\W+", "_");
        return "tts_" + voiceId + "_" + baseName + "_" + System.currentTimeMillis() + ".wav";
    }

    /**
     * AV-P1-010: Validate TTS input parameters.
     *
     * @param text the text to synthesize
     * @param voiceId the voice ID
     * @param speed the speed factor
     * @param pitch the pitch factor
     * @param language the language code
     * @throws IllegalArgumentException if validation fails
     */
    private void validateTtsInput(String text, Optional<String> voiceId, float speed, 
                                   float pitch, String language) {
        // Validate text length
        if (text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text exceeds maximum length of " + MAX_TEXT_LENGTH + " characters");
        }

        // Validate speed
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            throw new IllegalArgumentException("Speed must be between " + MIN_SPEED + " and " + MAX_SPEED + 
                ", got: " + speed);
        }

        // Validate pitch
        if (pitch < MIN_PITCH || pitch > MAX_PITCH) {
            throw new IllegalArgumentException("Pitch must be between " + MIN_PITCH + " and " + MAX_PITCH + 
                ", got: " + pitch);
        }

        // Validate language if provided
        if (language != null && !language.isBlank()) {
            String langCode = language.toLowerCase().split("-")[0]; // Handle "en-US" -> "en"
            if (!SUPPORTED_LANGUAGES.contains(langCode)) {
                throw new IllegalArgumentException("Unsupported language: " + language + 
                    ". Supported: " + SUPPORTED_LANGUAGES);
            }
        }
    }

    /**
     * AV-P1-011: Set degraded mode for the TTS service.
     *
     * @param degraded whether the service is degraded
     * @param reason the reason for degradation
     */
    public void setDegradedMode(boolean degraded, String reason) {
        this.degraded = degraded;
        this.degradationReason = reason;
        if (degraded) {
            LOG.warn("TTS service entering degraded mode: {}", reason);
        } else {
            LOG.info("TTS service exiting degraded mode");
        }
    }

    /**
     * AV-P1-011: Check if the service is in degraded mode.
     *
     * @return true if degraded
     */
    public boolean isDegraded() {
        return degraded;
    }

    /**
     * AV-P1-011: Get the degradation reason.
     *
     * @return the degradation reason, or null if not degraded
     */
    public String getDegradationReason() {
        return degradationReason;
    }

    /**
     * AV-P1-011: Emit audit event for TTS operations.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @param operation the operation type
     * @param status the operation status
     * @param details additional details
     */
    private void emitTtsAudit(String tenantId, String userId, String operation, 
                               String status, Map<String, String> details) {
        if (auditService == null) {
            return; // Audit service not configured
        }
        
        try {
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId != null ? tenantId : "unknown")
                .eventType(operation)
                .principal(userId != null ? userId : "system")
                .resourceType("tts")
                .resourceId("tts-operation")
                .success("SUCCESS".equals(status))
                .detail("status", status)
                .detail("operation", operation)
                .details(Map.copyOf(details))
                .build();
            
            auditService.record(event);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit event for TTS operation {}: {}", operation, e.getMessage());
        }
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
