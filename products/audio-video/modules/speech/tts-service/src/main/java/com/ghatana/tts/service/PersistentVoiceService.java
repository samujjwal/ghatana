package com.ghatana.tts.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose AI Voice service with persistence integration.
 *              Handles voice cloning, style transfer, and voice enhancement with persistence.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentVoiceService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentVoiceService.class);

    private final AudioFileService audioFileService;
    private final Timer cloneTimer;
    private final Timer enhanceTimer;

    public PersistentVoiceService(
            AudioFileService audioFileService,
            MeterRegistry meterRegistry) {
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.cloneTimer = Timer.builder("voice.persistent.clone")
            .description("Voice cloning latency with persistence")
            .register(meterRegistry);
        this.enhanceTimer = Timer.builder("voice.persistent.enhance")
            .description("Voice enhancement latency with persistence")
            .register(meterRegistry);
    }

    /**
     * Clone a voice from sample audio and persist the voice profile.
     *
     * @param tenantId      the tenant ID
     * @param userId        the user ID
     * @param voiceName     name for the cloned voice
     * @param sampleData    sample audio data for voice cloning
     * @param description   optional description
     * @return Promise containing the cloned voice profile with persisted file ID
     */
    public Promise<VoiceProfile> cloneVoiceAndPersist(
            String tenantId,
            UUID userId,
            String voiceName,
            byte[] sampleData,
            String description) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(voiceName, "voiceName cannot be null");
        Objects.requireNonNull(sampleData, "sampleData cannot be null");

        long startTime = System.currentTimeMillis();

        // Step 1: Persist sample audio file
        return persistVoiceSample(tenantId, userId, voiceName + "_sample.wav", sampleData.length)
            .then(sampleFile -> {
                LOG.info("[tenant={}] Voice sample persisted: id={}, voice={}",
                    tenantId, sampleFile.getId(), voiceName);

                // Step 2: Perform voice cloning (placeholder for actual ML processing)
                return performVoiceCloning(sampleData, voiceName)
                    .then(clonedVoice -> {
                        // Step 3: Persist cloned voice output
                        return persistClonedVoice(tenantId, userId, voiceName, clonedVoice.length)
                            .map(voiceFile -> {
                                long elapsedMs = System.currentTimeMillis() - startTime;
                                cloneTimer.record(Duration.ofMillis(elapsedMs));

                                LOG.info("[tenant={}] Voice cloning completed: voiceId={}, " +
                                    "voiceName={}, elapsedMs={}",
                                    tenantId, voiceFile.getId(), voiceName, elapsedMs);

                                return new VoiceProfile(
                                    voiceFile.getId(),
                                    voiceName,
                                    description,
                                    sampleFile.getId(),
                                    clonedVoice,
                                    elapsedMs
                                );
                            });
                    });
            })
            .whenException(e -> {
                LOG.error("[tenant={}] Voice cloning failed: {}", tenantId, e.getMessage(), e);
            });
    }

    /**
     * Enhance audio quality and persist result.
     *
     * @param tenantId    the tenant ID
     * @param userId      the user ID
     * @param audioData   the audio to enhance
     * @param fileName    original file name
     * @param options     enhancement options
     * @return Promise containing enhanced audio with persisted file ID
     */
    public Promise<EnhancedAudio> enhanceAndPersist(
            String tenantId,
            UUID userId,
            byte[] audioData,
            String fileName,
            EnhancementOptions options) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(audioData, "audioData cannot be null");

        long startTime = System.currentTimeMillis();

        return persistSourceAudio(tenantId, userId, fileName, audioData.length)
            .then(sourceFile -> {
                LOG.info("[tenant={}] Source audio persisted: id={}, file={}",
                    tenantId, sourceFile.getId(), fileName);

                return performEnhancement(audioData, options)
                    .then(enhancedData -> {
                        return persistEnhancedAudio(tenantId, userId, "enhanced_" + fileName, enhancedData.length)
                            .map(enhancedFile -> {
                                long elapsedMs = System.currentTimeMillis() - startTime;
                                enhanceTimer.record(Duration.ofMillis(elapsedMs));

                                LOG.info("[tenant={}] Audio enhancement completed: enhancedId={}, " +
                                    "noiseReduction={}, clarityScore={}, elapsedMs={}",
                                    tenantId, enhancedFile.getId(),
                                    options.noiseReduction(), options.clarityBoost(), elapsedMs);

                                return new EnhancedAudio(
                                    sourceFile.getId(),
                                    enhancedFile.getId(),
                                    enhancedData,
                                    options,
                                    elapsedMs
                                );
                            });
                    });
            })
            .whenException(e -> {
                LOG.error("[tenant={}] Audio enhancement failed: {}", tenantId, e.getMessage(), e);
            });
    }

    private Promise<AudioFileEntity> persistVoiceSample(
            String tenantId, UUID userId, String fileName, int fileSize) {
        return persistAudioFile(tenantId, userId, fileName, fileSize, "/storage/voice/samples/");
    }

    private Promise<AudioFileEntity> persistClonedVoice(
            String tenantId, UUID userId, String voiceName, int fileSize) {
        return persistAudioFile(tenantId, userId, voiceName + ".wav", fileSize,
            "/storage/voice/cloned/");
    }

    private Promise<AudioFileEntity> persistSourceAudio(
            String tenantId, UUID userId, String fileName, int fileSize) {
        return persistAudioFile(tenantId, userId, fileName, fileSize, "/storage/voice/source/");
    }

    private Promise<AudioFileEntity> persistEnhancedAudio(
            String tenantId, UUID userId, String fileName, int fileSize) {
        return persistAudioFile(tenantId, userId, fileName, fileSize, "/storage/voice/enhanced/");
    }

    private Promise<AudioFileEntity> persistAudioFile(
            String tenantId, UUID userId, String fileName, int fileSize, String storagePath) {

        String extension = getExtension(fileName);

        AudioFileEntity entity = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            userId,
            fileName,
            storagePath + tenantId + "/" + UUID.randomUUID() + "." + extension,
            extension
        );
        entity.setFileSizeBytes((long) fileSize);
        entity.setStatus(AudioFileEntity.ProcessingStatus.COMPLETED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    private Promise<byte[]> performVoiceCloning(byte[] sampleData, String voiceName) {
        return Promise.ofCallable(() -> {
            // Placeholder for actual voice cloning ML model
            // In production, this calls the voice cloning engine
            LOG.debug("Cloning voice '{}' from {} bytes of sample data", voiceName, sampleData.length);
            Thread.sleep(100); // Simulate processing
            return new byte[sampleData.length]; // Placeholder output
        });
    }

    private Promise<byte[]> performEnhancement(byte[] audioData, EnhancementOptions options) {
        return Promise.ofCallable(() -> {
            // Placeholder for actual audio enhancement ML model
            LOG.debug("Enhancing audio with noiseReduction={}, clarityBoost={}",
                options.noiseReduction(), options.clarityBoost());
            Thread.sleep(50); // Simulate processing
            return audioData; // Placeholder - return same data
        });
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "wav";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Voice profile result.
     */
    public record VoiceProfile(
        UUID voiceId,
        String voiceName,
        String description,
        UUID sampleAudioId,
        byte[] clonedVoiceData,
        long processingTimeMs
    ) {}

    /**
     * Enhanced audio result.
     */
    public record EnhancedAudio(
        UUID sourceAudioId,
        UUID enhancedAudioId,
        byte[] enhancedData,
        EnhancementOptions options,
        long processingTimeMs
    ) {}

    /**
     * Audio enhancement options.
     */
    public record EnhancementOptions(
        boolean noiseReduction,
        boolean clarityBoost,
        boolean normalizeVolume,
        boolean removeBackgroundNoise,
        float targetLufs // Loudness units relative to full scale
    ) {
        public static EnhancementOptions defaults() {
            return new EnhancementOptions(true, true, true, true, -14.0f);
        }
    }
}
