package com.ghatana.stt.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.audio.video.common.security.JwtServerInterceptor;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @doc.type class
 * @doc.purpose STT service with persistence integration.
 *              Wraps the platform SttEngine and persists audio files and transcriptions.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentSttService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentSttService.class);

    // AV-P1-006: Validation constants
    private static final long MAX_AUDIO_SIZE_BYTES = 100 * 1024 * 1024; // 100MB
    private static final int MIN_SAMPLE_RATE = 8000;
    private static final int MAX_SAMPLE_RATE = 48000;
    private static final Set<String> SUPPORTED_FORMATS = Set.of("wav", "mp3", "flac", "ogg", "m4a");
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "ne", "hi", "es", "fr", "de", "ja", "zh");
    
    // AV-P1-005: Timeout constants
    private static final long BATCH_TRANSCRIPTION_TIMEOUT_MS = 30_000; // 30 seconds
    private volatile boolean degraded = false;
    private volatile String degradationReason = null;

    private final AudioVideoLibrary library;
    private final AudioFileService audioFileService;
    private final TranscriptionService transcriptionService;
    private final Timer transcribeTimer;
    private final Executor blockingExecutor;
    private final AuditService auditService;

    public PersistentSttService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry meterRegistry) {
        this(library, audioFileService, transcriptionService, meterRegistry, null);
    }

    public PersistentSttService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry meterRegistry,
            @Nullable AuditService auditService) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService cannot be null");
        this.auditService = auditService;
        this.transcribeTimer = Timer.builder("stt.persistent.transcribe")
            .description("Persistent transcription latency")
            .register(meterRegistry);
        this.blockingExecutor = ForkJoinPool.commonPool();
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

        // AV-P1-004: Validate input before processing (validation failures are surfaced via exception)
        try {
            validateSttInput(audioBytes, fileName, format, sampleRate, language);
        } catch (IllegalArgumentException e) {
            emitSttAudit(tenantId, userId.toString(), "stt.transcribe", "VALIDATION_FAILURE",
                Map.of("error", e.getMessage(), "audioSizeBytes", String.valueOf(audioBytes.length)));
            throw e;
        }

        // AV-P1-002: Compute SHA-256 of audio bytes for integrity tracking
        String inputHash = sha256Hex(audioBytes);
        // AV-P1-002: Generate a per-request correlation ID for end-to-end tracing
        String requestId = UUID.randomUUID().toString();

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
                        return persistTranscription(tenantId, userId, audioFile.getId(), transcriptionResult, requestId, inputHash)
                            .map(transcription -> {
                                long elapsedMs = System.currentTimeMillis() - startTime;
                                transcribeTimer.record(Duration.ofMillis(elapsedMs));

                                LOG.info("[tenant={} requestId={}] Transcription completed: audioId={}, transcriptionId={}, " +
                                    "confidence={}, elapsedMs={}",
                                    tenantId, requestId, audioFile.getId(), transcription.getId(),
                                    transcriptionResult.confidence(), elapsedMs);

                                // AV-P1-003: Emit audit event for successful transcription
                                emitSttAudit(tenantId, userId.toString(), "stt.transcribe", "SUCCESS",
                                    Map.of("requestId", requestId,
                                           "audioFileId", audioFile.getId().toString(),
                                           "transcriptionId", transcription.getId().toString(),
                                           "confidence", String.valueOf(transcriptionResult.confidence()),
                                           "language", transcriptionResult.language() != null ? transcriptionResult.language() : "unknown",
                                           "processingTimeMs", String.valueOf(elapsedMs),
                                           "audioSizeBytes", String.valueOf(audioBytes.length),
                                           "inputHash", inputHash));

                                return transcriptionResult;
                            });
                    })
                    .whenException(e -> {
                        LOG.error("[tenant={} requestId={}] Transcription failed for audioId={}: {}",
                            tenantId, requestId, audioFile.getId(), e.getMessage(), e);
                        updateAudioFileStatus(tenantId, audioFile.getId(),
                            AudioFileEntity.ProcessingStatus.FAILED, e.getMessage());

                        // AV-P1-003: Granular audit for inference vs. timeout vs. degraded failures
                        String auditOutcome = resolveFailureOutcome(e);
                        emitSttAudit(tenantId, userId.toString(), "stt.transcribe", auditOutcome,
                            Map.of("requestId", requestId,
                                   "audioFileId", audioFile.getId().toString(),
                                   "error", e.getMessage(),
                                   "audioSizeBytes", String.valueOf(audioBytes.length)));
                    });
            })
            .whenException(e -> {
                LOG.error("[tenant={} requestId={}] STT request failed: {}", tenantId, requestId, e.getMessage(), e);
                emitSttAudit(tenantId, userId.toString(), "stt.transcribe", resolveFailureOutcome(e),
                    Map.of("requestId", requestId,
                           "error", e.getMessage(),
                           "audioSizeBytes", String.valueOf(audioBytes.length)));
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

    /**
     * AV-P1-008: Set degraded mode for the STT service.
     *
     * @param degraded whether the service is degraded
     * @param reason the reason for degradation
     */
    public void setDegradedMode(boolean degraded, String reason) {
        this.degraded = degraded;
        this.degradationReason = reason;
        if (degraded) {
            LOG.warn("STT service entering degraded mode: {}", reason);
        } else {
            LOG.info("STT service exiting degraded mode");
        }
    }

    /**
     * AV-P1-008: Check if the service is in degraded mode.
     *
     * @return true if degraded
     */
    public boolean isDegraded() {
        return degraded;
    }

    /**
     * AV-P1-008: Get the degradation reason.
     *
     * @return the degradation reason, or null if not degraded
     */
    public String getDegradationReason() {
        return degradationReason;
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

        // AV-P1-008: Check degraded mode before processing
        if (degraded) {
            LOG.warn("STT service is in degraded mode: {}", degradationReason);
            return Promise.ofException(new IllegalStateException(
                "STT service is degraded: " + degradationReason));
        }

        // AV-P1-005: Submit transcription to a bounded-time Future and enforce the timeout.
        // The future runs on blockingExecutor; if it exceeds BATCH_TRANSCRIPTION_TIMEOUT_MS the
        // Promise is completed with a TimeoutException so callers see DEADLINE_EXCEEDED.
        java.util.concurrent.ExecutorService singleUse =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stt-transcribe-timeout");
                t.setDaemon(true);
                return t;
            });
        Future<TranscriptionResult> future = singleUse.submit(() -> {
            AudioData audio = new AudioData(audioBytes, sampleRate, 1, 16, Duration.ZERO, AudioFormat.PCM);
            try (SttEngine stt = library.getSttEngine()) {
                TranscriptionOptions options = TranscriptionOptions.builder()
                    .language(language != null && !language.isBlank()
                        ? Locale.forLanguageTag(language)
                        : Locale.getDefault())
                    .build();
                return stt.transcribe(audio, options);
            }
        });
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                return future.get(BATCH_TRANSCRIPTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                singleUse.shutdownNow();
                throw new TimeoutException("STT transcription timed out after "
                    + BATCH_TRANSCRIPTION_TIMEOUT_MS + " ms");
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                throw new RuntimeException(cause);
            } finally {
                singleUse.shutdown();
            }
        });
    }

    private Promise<TranscriptionEntity> persistTranscription(
            String tenantId,
            UUID userId,
            UUID audioFileId,
            TranscriptionResult result,
            String requestId,
            String inputHashSha256) {

        TranscriptionEntity entity = new TranscriptionEntity(
            UUID.randomUUID(),
            tenantId,
            audioFileId,
            userId,
            result.text(),
            result.language() != null ? result.language() : "unknown"
        );
        entity.setConfidence((float) result.confidence());
        entity.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED);
        entity.setModelUsed(result.modelId());
        entity.setProcessingTimeMs(result.processingTime().toMillis());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // AV-P1-002: Persist requestId and input hash for traceability
        TranscriptionEntity.TranscriptionMetadata meta = new TranscriptionEntity.TranscriptionMetadata();
        meta.setRequestId(requestId);
        meta.setInputHashSha256(inputHashSha256);
        if (result.modelId() != null) {
            meta.setEngineVersion(result.modelId());
        }
        entity.setMetadata(meta);

        return transcriptionService.save(tenantId, entity)
            .then(transcription -> {
                // Update audio file status to COMPLETED
                return updateAudioFileStatus(tenantId, audioFileId,
                        AudioFileEntity.ProcessingStatus.COMPLETED, null)
                    .map(__ -> transcription);
            });
    }

    private Promise<Boolean> updateAudioFileStatus(
            String tenantId,
            UUID audioFileId,
            AudioFileEntity.ProcessingStatus status,
            String reason) {

        // AV-P1-004: Use the new updateStatus method with reason parameter
        return audioFileService.updateStatus(tenantId, audioFileId, status, reason);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "audio";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * AV-P1-006: Validate STT input parameters.
     *
     * @param audioBytes the audio data
     * @param fileName the file name
     * @param format the audio format
     * @param sampleRate the sample rate
     * @param language the language hint
     * @throws IllegalArgumentException if validation fails
     */
    private void validateSttInput(byte[] audioBytes, String fileName, AudioFormat format, 
                                   int sampleRate, String language) {
        // Validate audio size
        if (audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio data cannot be empty");
        }
        if (audioBytes.length > MAX_AUDIO_SIZE_BYTES) {
            throw new IllegalArgumentException("Audio data exceeds maximum size of " + 
                (MAX_AUDIO_SIZE_BYTES / 1024 / 1024) + "MB");
        }

        // Validate file format
        String extension = getExtension(fileName).toLowerCase();
        if (!SUPPORTED_FORMATS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported audio format: " + extension + 
                ". Supported: " + SUPPORTED_FORMATS);
        }

        // Validate sample rate
        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException("Sample rate must be between " + 
                MIN_SAMPLE_RATE + " and " + MAX_SAMPLE_RATE + " Hz, got: " + sampleRate);
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
     * AV-P1-003: Classify a failure exception into the canonical audit outcome label.
     */
    private static String resolveFailureOutcome(Throwable e) {
        if (e instanceof TimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().startsWith("STT service is degraded")) {
            return "DEGRADED_PROVIDER_FAILURE";
        }
        if (e instanceof com.ghatana.media.common.InferenceError) {
            return "INFERENCE_FAILURE";
        }
        return "FAILED";
    }

    /**
     * AV-P1-002: Compute SHA-256 hex digest of the given bytes.
     */
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec — this cannot happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * AV-P1-003: Emit audit event for STT operations.
     */
    private void emitSttAudit(String tenantId, String userId, String operation,
                               String status, Map<String, String> details) {
        if (auditService == null) {
            return;
        }
        try {
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId != null ? tenantId : "unknown")
                .eventType(operation)
                .principal(userId != null ? userId : "system")
                .resourceType("stt")
                .resourceId("stt-operation")
                .success("SUCCESS".equals(status))
                .detail("status", status)
                .detail("operation", operation)
                .details(Map.copyOf(details))
                .build();
            auditService.record(event);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit event for STT operation {}: {}", operation, e.getMessage());
        }
    }
}
