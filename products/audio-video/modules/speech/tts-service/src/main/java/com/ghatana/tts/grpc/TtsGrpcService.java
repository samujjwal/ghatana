package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.CloneOptions;
import com.ghatana.media.tts.api.ProfileSettings;
import com.ghatana.media.tts.api.SynthesisOptions;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.media.tts.api.TtsProfile;
import com.ghatana.media.tts.api.VoiceInfo;
import com.ghatana.tts.core.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * TTS gRPC Service — bridges the proto TTSService API to the platform TtsEngine.
 *
 * @doc.type class
 * @doc.purpose gRPC facade for platform TtsEngine
 * @doc.layer product
 * @doc.pattern Service
 */
public class TtsGrpcService extends TTSServiceGrpc.TTSServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TtsGrpcService.class);

    private final AudioVideoLibrary library;
    private final Timer synthesizeTimer;
    private final Timer streamingTimer;

    /**
     * Package-private constructor for unit testing — supply a pre-configured library.
     * Avoids native-library / file-system dependencies during tests.
     */
    TtsGrpcService(AudioVideoLibrary library, MeterRegistry metrics) {
        this.library = library;
        this.synthesizeTimer = Timer.builder("tts.synthesize")
            .description("Synthesis latency")
            .publishPercentiles(0.50, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(metrics);
        this.streamingTimer = Timer.builder("tts.synthesize.streaming")
            .description("Streaming synthesis latency")
            .publishPercentiles(0.50, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(metrics);
    }

    public TtsGrpcService(MeterRegistry metrics) {
        TtsConfig config = TtsConfig.builder()
            .voiceModelPath(Paths.get(System.getenv().getOrDefault("TTS_MODEL_PATH", "/models/piper-en.onnx")))
            .defaultVoiceId(System.getenv().getOrDefault("TTS_DEFAULT_VOICE", "piper-en"))
            .sampleRate(22050)
            .maxConcurrentRequests(Integer.parseInt(System.getenv().getOrDefault("TTS_MAX_CONCURRENT", "10")))
            .enableProsody(true)
            .build();

        this.library = AudioVideoLibrary.builder().withTtsConfig(config).build();
        this.synthesizeTimer = Timer.builder("tts.synthesize")
            .description("Synthesis latency")
            .publishPercentiles(0.50, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(metrics);
        this.streamingTimer = Timer.builder("tts.synthesize.streaming")
            .description("Streaming synthesis latency")
            .publishPercentiles(0.50, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(metrics);

        LOG.info("TTS Service initialized with voice: {}", config.defaultVoiceId());
    }

    @Override
    public void synthesize(SynthesizeRequest request, StreamObserver<SynthesizeResponse> responseObserver) {
        String cid = cid();
        long start = System.currentTimeMillis();
        synthesizeTimer.record(() -> {
            try {
                String text = request.getText();
                if (text == null || text.isEmpty()) {
                    responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT.withDescription("Text cannot be empty").asRuntimeException());
                    return;
                }
                if (text.length() > 5000) {
                    responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT.withDescription("Text exceeds 5000 characters").asRuntimeException());
                    return;
                }

                SynthesisOptions options = SynthesisOptions.builder()
                    .voiceId(request.getVoiceId().isEmpty() ? null : request.getVoiceId())
                    .speed(request.getOptions().getSpeed())
                    .pitch(request.getOptions().getPitch())
                    .volume(request.getOptions().getEnergy())
                    .language(request.getOptions().getLanguage().isEmpty()
                        ? null
                        : Locale.forLanguageTag(request.getOptions().getLanguage()))
                    .build();

                com.ghatana.media.common.AudioData audio;
                try (TtsEngine tts = library.getTtsEngine()) {
                    audio = tts.synthesize(text, options);
                }

                long elapsed = System.currentTimeMillis() - start;
                double durationMs = (audio.data().length / 2.0 / audio.sampleRate()) * 1000;
                LOG.info("[{}] synthesize: elapsed={}ms", cid, elapsed);

                responseObserver.onNext(SynthesizeResponse.newBuilder()
                    .setAudioData(com.google.protobuf.ByteString.copyFrom(audio.data()))
                    .setSampleRate(audio.sampleRate())
                    .setDurationMs((int) durationMs)
                    .setProcessingTimeMs((int) elapsed)
                    .setVoiceUsed(options.voiceId() != null ? options.voiceId() : "")
                    .build());
                responseObserver.onCompleted();
            } catch (com.ghatana.media.common.ValidationError e) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            } catch (Exception e) {
                LOG.error("[{}] Synthesis failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Synthesis failed").asRuntimeException());
            }
        });
    }

    @Override
    public void streamSynthesize(SynthesizeRequest request, StreamObserver<AudioChunk> responseObserver) {
        String cid = cid();
        streamingTimer.record(() -> {
            try {
                SynthesisOptions options = SynthesisOptions.builder()
                    .voiceId(request.getVoiceId().isEmpty() ? null : request.getVoiceId())
                    .speed(request.getOptions().getSpeed())
                    .pitch(request.getOptions().getPitch())
                    .volume(request.getOptions().getEnergy())
                    .language(request.getOptions().getLanguage().isEmpty()
                        ? null
                        : Locale.forLanguageTag(request.getOptions().getLanguage()))
                    .build();

                try (TtsEngine tts = library.getTtsEngine()) {
                    tts.synthesizeStreaming(request.getText(), options, chunk -> {
                        responseObserver.onNext(AudioChunk.newBuilder()
                            .setAudioData(com.google.protobuf.ByteString.copyFrom(chunk.data()))
                            .setSampleRate(22050)
                            .setIsFinal(chunk.isLast())
                            .build());
                        if (chunk.isLast()) {
                            LOG.info("[{}] streamSynthesize complete: seq={}", cid, chunk.sequenceNumber());
                            responseObserver.onCompleted();
                        }
                    });
                }
            } catch (Exception e) {
                LOG.error("[{}] Stream synthesis failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Streaming synthesis failed").asRuntimeException());
            }
        });
    }

    @Override
    public void getVoices(GetVoicesRequest request, StreamObserver<GetVoicesResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            List<com.ghatana.media.tts.api.VoiceInfo> voices = request.getLanguage().isEmpty()
                ? tts.getAvailableVoices()
                : tts.getAvailableVoices(Locale.forLanguageTag(request.getLanguage()));

            GetVoicesResponse.Builder builder = GetVoicesResponse.newBuilder();
            for (com.ghatana.media.tts.api.VoiceInfo voice : voices) {
                builder.addVoices(com.ghatana.tts.core.grpc.proto.VoiceInfo.newBuilder()
                    .setVoiceId(voice.voiceId())
                    .setName(voice.name())
                    .addLanguages(voice.language().toLanguageTag())
                    .setIsCloned(voice.isCloned())
                    .setSizeBytes(voice.modelSizeBytes())
                    .build());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Failed to list voices").asRuntimeException());
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            com.ghatana.media.common.EngineStatus status = tts.getStatus();
            responseObserver.onNext(StatusResponse.newBuilder()
                .setActiveVoice(status.modelId() != null ? status.modelId() : "")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Status unavailable").asRuntimeException());
        }
    }

    @Override
    public void getMetrics(MetricsRequest request, StreamObserver<MetricsResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            com.ghatana.media.common.EngineMetrics metrics = tts.getMetrics();
            responseObserver.onNext(MetricsResponse.newBuilder()
                .setTotalSyntheses((int) metrics.requestCount())
                .setAverageLatencyMs((float) metrics.avgLatencyMs())
                .setMemoryUsageBytes(metrics.memoryUsageBytes())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Metrics unavailable").asRuntimeException());
        }
    }

    @Override
    public void loadVoice(LoadVoiceRequest request, StreamObserver<LoadVoiceResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            tts.loadVoice(request.getVoiceId());
            responseObserver.onNext(LoadVoiceResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Voice loaded: " + request.getVoiceId())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Failed to load voice: " + e.getMessage()).asRuntimeException());
        }
    }

    // ── AV-002.4: createProfile ────────────────────────────────────────────

    @Override
    public void createProfile(CreateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        String displayName = request.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("displayName must not be blank")
                .asRuntimeException());
            return;
        }
        try (TtsEngine tts = library.getTtsEngine()) {
            String profileId = UUID.randomUUID().toString();

            // Build platform ProfileSettings from proto settings
            ProfileSettings settings = ProfileSettings.builder()
                .defaultSpeed(request.getSettings().getDefaultOptions().getSpeed() > 0
                    ? request.getSettings().getDefaultOptions().getSpeed() : 1.0)
                .defaultPitch(request.getSettings().getDefaultOptions().getPitch() > 0
                    ? request.getSettings().getDefaultOptions().getPitch() : 1.0)
                .defaultVolume(request.getSettings().getDefaultOptions().getEnergy() > 0
                    ? request.getSettings().getDefaultOptions().getEnergy() : 1.0)
                .build();

            TtsProfile profile = tts.createProfile(profileId, displayName, settings);

            LOG.info("TTS profile created: id={}, name={}", profileId, displayName);
            responseObserver.onNext(ProfileResponse.newBuilder()
                .setProfileId(profile.profileId())
                .setDisplayName(profile.displayName())
                .setSettings(request.getSettings())
                .setStats(ProfileStats.newBuilder()
                    .setCreatedAtMs(System.currentTimeMillis())
                    .setLastUsedAtMs(System.currentTimeMillis())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to create TTS profile '{}': {}", displayName, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Profile creation failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    // ── AV-002 getProfile ──────────────────────────────────────────────────

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        String profileId = request.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("profileId must not be blank")
                .asRuntimeException());
            return;
        }
        try (TtsEngine tts = library.getTtsEngine()) {
            Optional<TtsProfile> profileOpt = tts.loadProfile(profileId);
            if (profileOpt.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Profile not found: " + profileId)
                    .asRuntimeException());
                return;
            }
            TtsProfile profile = profileOpt.get();
            LOG.debug("TTS profile retrieved: id={}", profileId);
            responseObserver.onNext(ProfileResponse.newBuilder()
                .setProfileId(profile.profileId())
                .setDisplayName(profile.displayName())
                .setStats(ProfileStats.newBuilder()
                    .setTotalCharactersSynthesized(
                        profile.recentSyntheses().stream()
                            .mapToInt(String::length).sum())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to get TTS profile {}: {}", profileId, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Profile retrieval failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    // ── AV-002 updateProfile ───────────────────────────────────────────────

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        String profileId = request.getProfileId();
        if (profileId == null || profileId.isBlank()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("profileId must not be blank")
                .asRuntimeException());
            return;
        }
        try (TtsEngine tts = library.getTtsEngine()) {
            Optional<TtsProfile> profileOpt = tts.loadProfile(profileId);
            if (profileOpt.isEmpty()) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Profile not found: " + profileId)
                    .asRuntimeException());
                return;
            }
            TtsProfile existing = profileOpt.get();

            // Apply settings: preferred voice
            String newVoiceId = existing.preferredVoiceId();
            if (!request.getSettings().getDefaultVoiceId().isBlank()) {
                newVoiceId = request.getSettings().getDefaultVoiceId();
            }

            // Build updated platform ProfileSettings
            ProfileSettings updatedSettings = ProfileSettings.builder()
                .defaultSpeed(request.getSettings().getDefaultOptions().getSpeed() > 0
                    ? request.getSettings().getDefaultOptions().getSpeed()
                    : existing.settings().defaultSpeed())
                .defaultPitch(request.getSettings().getDefaultOptions().getPitch() > 0
                    ? request.getSettings().getDefaultOptions().getPitch()
                    : existing.settings().defaultPitch())
                .defaultVolume(request.getSettings().getDefaultOptions().getEnergy() > 0
                    ? request.getSettings().getDefaultOptions().getEnergy()
                    : existing.settings().defaultVolume())
                .build();

            TtsProfile updated = new TtsProfile(
                existing.profileId(), existing.displayName(), newVoiceId,
                updatedSettings, existing.recentSyntheses()
            );
            tts.saveProfile(updated);

            LOG.info("TTS profile updated: id={}", profileId);
            responseObserver.onNext(ProfileResponse.newBuilder()
                .setProfileId(updated.profileId())
                .setDisplayName(updated.displayName())
                .setSettings(request.getSettings())
                .setStats(ProfileStats.newBuilder()
                    .setLastUsedAtMs(System.currentTimeMillis())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to update TTS profile {}: {}", profileId, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Profile update failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    // ── AV-002.1: cloneVoice ───────────────────────────────────────────────

    @Override
    public void cloneVoice(CloneVoiceRequest request, StreamObserver<CloneVoiceResponse> responseObserver) {
        String voiceName = request.getVoiceName();
        if (voiceName == null || voiceName.isBlank()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("voiceName must not be blank")
                .asRuntimeException());
            return;
        }
        if (request.getAudioSamplesList().isEmpty()) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("At least one audio sample is required for voice cloning")
                .asRuntimeException());
            return;
        }
        try (TtsEngine tts = library.getTtsEngine()) {
            // Convert audio sample bytes → AudioData
            List<AudioData> samples = new ArrayList<>();
            for (com.google.protobuf.ByteString sample : request.getAudioSamplesList()) {
                if (!sample.isEmpty()) {
                    samples.add(new AudioData(sample.toByteArray(), 22050, 1, 16));
                }
            }

            CloneOptions cloneOptions = new CloneOptions(
                request.getOptions().getFineTuneEpochs() > 0
                    ? request.getOptions().getFineTuneEpochs() : 100,
                request.getOptions().getLearningRate() > 0
                    ? request.getOptions().getLearningRate() : 0.001f,
                3,
                Duration.ofSeconds(5)
            );

            VoiceInfo cloned = tts.cloneVoice(voiceName, samples, cloneOptions);

            LOG.info("Voice cloned: name={}, voiceId={}", voiceName, cloned.voiceId());
            responseObserver.onNext(CloneVoiceResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Voice cloned successfully: " + voiceName)
                .setVoiceId(cloned.voiceId())
                .setSimilarityScore(cloned.similarityScore())
                .setVoice(com.ghatana.tts.core.grpc.proto.VoiceInfo.newBuilder()
                    .setVoiceId(cloned.voiceId())
                    .setName(cloned.name())
                    .setIsCloned(true)
                    .setSizeBytes(cloned.modelSizeBytes())
                    .build())
                .build());
            responseObserver.onCompleted();
        } catch (com.ghatana.media.common.ValidationError e) {
            LOG.warn("Voice cloning validation failed: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Voice cloning failed for '{}': {}", voiceName, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Voice cloning failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void submitFeedback(FeedbackRequest request, StreamObserver<FeedbackResponse> responseObserver) {
        responseObserver.onNext(FeedbackResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    private static String cid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
