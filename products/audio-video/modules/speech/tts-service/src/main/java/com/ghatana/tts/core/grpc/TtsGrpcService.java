package com.ghatana.tts.core.grpc;

import com.ghatana.tts.core.grpc.proto.TTSServiceGrpc;
import com.ghatana.tts.core.grpc.proto.SynthesizeRequest;
import com.ghatana.tts.core.grpc.proto.SynthesizeResponse;
import com.ghatana.tts.core.grpc.proto.AudioChunk;
import com.ghatana.tts.core.grpc.proto.StatusRequest;
import com.ghatana.tts.core.grpc.proto.StatusResponse;
import com.ghatana.tts.core.grpc.proto.MetricsRequest;
import com.ghatana.tts.core.grpc.proto.MetricsResponse;
import com.ghatana.tts.core.grpc.proto.GetVoicesRequest;
import com.ghatana.tts.core.grpc.proto.GetVoicesResponse;
import com.ghatana.tts.core.grpc.proto.LoadVoiceRequest;
import com.ghatana.tts.core.grpc.proto.LoadVoiceResponse;
import com.ghatana.tts.core.grpc.proto.CreateProfileRequest;
import com.ghatana.tts.core.grpc.proto.GetProfileRequest;
import com.ghatana.tts.core.grpc.proto.UpdateProfileRequest;
import com.ghatana.tts.core.grpc.proto.ProfileResponse;
import com.ghatana.tts.core.grpc.proto.CloneVoiceRequest;
import com.ghatana.tts.core.grpc.proto.CloneVoiceResponse;
import com.ghatana.tts.core.grpc.proto.FeedbackRequest;
import com.ghatana.tts.core.grpc.proto.FeedbackResponse;
import com.ghatana.tts.core.grpc.proto.EngineState;
import com.ghatana.tts.core.grpc.proto.VoiceInfo;
import com.ghatana.tts.core.grpc.proto.ProfileSettings;
import com.ghatana.tts.core.grpc.proto.ProfileStats;
import com.ghatana.tts.core.api.TtsEngine;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * gRPC service implementation for TTS.
 * 
 * @doc.type class
 * @doc.purpose TTS gRPC service adapter
 * @doc.layer product
 * @doc.pattern Service
 */
public class TtsGrpcService extends TTSServiceGrpc.TTSServiceImplBase {

    private static final Logger LOG = LogManager.getLogger(TtsGrpcService.class);

    private final TtsEngine engine;

    public TtsGrpcService(TtsEngine engine) {
        this.engine = engine;
    }

    @Override
    public void synthesize(SynthesizeRequest request, StreamObserver<SynthesizeResponse> responseObserver) {
        LOG.debug("Synthesize request: text={}, voiceId={}", 
            request.getText().substring(0, Math.min(50, request.getText().length())),
            request.getVoiceId());

        try {
            long startTime = System.currentTimeMillis();

            com.ghatana.tts.core.api.SynthesisOptions options = mapOptions(request);
            com.ghatana.tts.core.api.SynthesisResult result = engine.synthesize(request.getText(), options);

            long processingTime = System.currentTimeMillis() - startTime;

            SynthesizeResponse response = SynthesizeResponse.newBuilder()
                .setAudioData(ByteString.copyFrom(result.getAudioData()))
                .setSampleRate(result.getSampleRate())
                .setDurationMs(result.getDurationMs())
                .setProcessingTimeMs(processingTime)
                .setVoiceUsed(result.getVoiceUsed())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("Synthesis failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Synthesis failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void streamSynthesize(SynthesizeRequest request, StreamObserver<AudioChunk> responseObserver) {
        LOG.debug("Stream synthesize request: text={}", 
            request.getText().substring(0, Math.min(50, request.getText().length())));

        try {
            com.ghatana.tts.core.api.SynthesisOptions options = mapOptions(request);
            
            engine.synthesizeStreaming(request.getText(), options, chunk -> {
                AudioChunk protoChunk = AudioChunk.newBuilder()
                    .setAudioData(ByteString.copyFrom(chunk.getAudioData()))
                    .setSampleRate(chunk.getSampleRate())
                    .setTimestampMs(chunk.getTimestampMs())
                    .setIsFinal(chunk.isFinal())
                    .build();
                responseObserver.onNext(protoChunk);
            });

            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("Streaming synthesis failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Streaming synthesis failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try {
            com.ghatana.tts.core.api.EngineStatus status = engine.getStatus();

            StatusResponse response = StatusResponse.newBuilder()
                .setState(mapEngineState(status.getState()))
                .setActiveVoice(status.getActiveVoice() != null ? status.getActiveVoice() : "")
                .setErrorMessage(status.getErrorMessage() != null ? status.getErrorMessage() : "")
                .setActiveSessions(status.getActiveSessions())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("GetStatus failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("GetStatus failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getMetrics(MetricsRequest request, StreamObserver<MetricsResponse> responseObserver) {
        try {
            com.ghatana.tts.core.api.EngineMetrics metrics = engine.getMetrics();

            MetricsResponse response = MetricsResponse.newBuilder()
                .setRealTimeFactor(metrics.getRealTimeFactor())
                .setMemoryUsageBytes(metrics.getMemoryUsageBytes())
                .setActiveSessions(metrics.getActiveSessions())
                .setTotalSyntheses(metrics.getTotalSyntheses())
                .setAverageLatencyMs(metrics.getAverageLatencyMs())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("GetMetrics failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("GetMetrics failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getVoices(GetVoicesRequest request, StreamObserver<GetVoicesResponse> responseObserver) {
        try {
            List<com.ghatana.tts.core.api.VoiceInfo> voices = engine.getAvailableVoices(
                request.getLanguage().isEmpty() ? null : request.getLanguage()
            );

            GetVoicesResponse.Builder builder = GetVoicesResponse.newBuilder();
            for (com.ghatana.tts.core.api.VoiceInfo voice : voices) {
                builder.addVoices(mapVoiceInfo(voice));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("GetVoices failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("GetVoices failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void loadVoice(LoadVoiceRequest request, StreamObserver<LoadVoiceResponse> responseObserver) {
        try {
            com.ghatana.tts.core.api.VoiceInfo voice = engine.loadVoice(request.getVoiceId());

            LoadVoiceResponse response = LoadVoiceResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Voice loaded successfully")
                .setVoice(mapVoiceInfo(voice))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("LoadVoice failed", e);
            responseObserver.onNext(LoadVoiceResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to load voice: " + e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void createProfile(CreateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            String profileId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();

            com.ghatana.tts.core.api.UserProfile profile = engine.createProfile(
                profileId,
                request.getDisplayName(),
                mapProfileSettings(request.getSettings())
            );

            responseObserver.onNext(mapProfileResponse(profile));
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("CreateProfile failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("CreateProfile failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            com.ghatana.tts.core.api.UserProfile profile = engine.getProfile(request.getProfileId());

            if (profile == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Profile not found: " + request.getProfileId())
                    .asRuntimeException());
                return;
            }

            responseObserver.onNext(mapProfileResponse(profile));
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("GetProfile failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("GetProfile failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            com.ghatana.tts.core.api.UserProfile profile = engine.updateProfile(
                request.getProfileId(),
                mapProfileSettings(request.getSettings())
            );

            responseObserver.onNext(mapProfileResponse(profile));
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("UpdateProfile failed", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("UpdateProfile failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void cloneVoice(CloneVoiceRequest request, StreamObserver<CloneVoiceResponse> responseObserver) {
        try {
            List<byte[]> audioSamples = request.getAudioSamplesList().stream()
                .map(ByteString::toByteArray)
                .toList();

            com.ghatana.tts.core.api.CloneResult result = engine.cloneVoice(
                request.getVoiceName(),
                audioSamples,
                request.getOptions().getFineTuneEpochs(),
                request.getOptions().getLearningRate()
            );

            CloneVoiceResponse response = CloneVoiceResponse.newBuilder()
                .setSuccess(result.isSuccess())
                .setMessage(result.getMessage())
                .setVoiceId(result.getVoiceId())
                .setSimilarityScore(result.getSimilarityScore())
                .setVoice(mapVoiceInfo(result.getVoice()))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("CloneVoice failed", e);
            responseObserver.onNext(CloneVoiceResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Voice cloning failed: " + e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void submitFeedback(FeedbackRequest request, StreamObserver<FeedbackResponse> responseObserver) {
        try {
            engine.submitFeedback(
                request.getProfileId(),
                request.getSynthesisId(),
                request.getFeedbackType().name(),
                request.getComment()
            );

            responseObserver.onNext(FeedbackResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Feedback submitted successfully")
                .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("SubmitFeedback failed", e);
            responseObserver.onNext(FeedbackResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to submit feedback: " + e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    // Helper methods

    private com.ghatana.tts.core.api.SynthesisOptions mapOptions(SynthesizeRequest request) {
        com.ghatana.tts.core.api.SynthesisOptions.Builder builder = 
            com.ghatana.tts.core.api.SynthesisOptions.builder();

        if (!request.getVoiceId().isEmpty()) {
            builder.voiceId(request.getVoiceId());
        }
        if (!request.getProfileId().isEmpty()) {
            builder.profileId(request.getProfileId());
        }
        if (request.hasOptions()) {
            var opts = request.getOptions();
            if (opts.getSpeed() > 0) builder.speed(opts.getSpeed());
            if (opts.getPitch() != 0) builder.pitch(opts.getPitch());
            if (opts.getEnergy() > 0) builder.energy(opts.getEnergy());
            if (!opts.getEmotion().isEmpty()) builder.emotion(opts.getEmotion());
            if (!opts.getLanguage().isEmpty()) builder.language(opts.getLanguage());
        }

        return builder.build();
    }

    private EngineState mapEngineState(com.ghatana.tts.core.api.EngineState state) {
        return switch (state) {
            case INITIALIZING -> EngineState.ENGINE_STATE_INITIALIZING;
            case READY -> EngineState.ENGINE_STATE_READY;
            case BUSY -> EngineState.ENGINE_STATE_BUSY;
            case ERROR -> EngineState.ENGINE_STATE_ERROR;
            case SHUTDOWN -> EngineState.ENGINE_STATE_SHUTDOWN;
        };
    }

    private VoiceInfo mapVoiceInfo(com.ghatana.tts.core.api.VoiceInfo voice) {
        return VoiceInfo.newBuilder()
            .setVoiceId(voice.getVoiceId())
            .setName(voice.getName())
            .setDescription(voice.getDescription() != null ? voice.getDescription() : "")
            .addAllLanguages(voice.getLanguages())
            .setGender(voice.getGender() != null ? voice.getGender() : "")
            .setSizeBytes(voice.getSizeBytes())
            .setIsLoaded(voice.isLoaded())
            .setIsCloned(voice.isCloned())
            .build();
    }

    private com.ghatana.tts.core.api.ProfileSettings mapProfileSettings(ProfileSettings settings) {
        return com.ghatana.tts.core.api.ProfileSettings.builder()
            .preferredLanguage(settings.getPreferredLanguage())
            .defaultVoiceId(settings.getDefaultVoiceId())
            .adaptationMode(mapAdaptationMode(settings.getAdaptationMode()))
            .privacyLevel(mapPrivacyLevel(settings.getPrivacyLevel()))
            .build();
    }

    private com.ghatana.tts.core.api.AdaptationMode mapAdaptationMode(
            com.ghatana.tts.core.grpc.proto.AdaptationMode mode) {
        return switch (mode) {
            case ADAPTATION_MODE_CONSERVATIVE -> com.ghatana.tts.core.api.AdaptationMode.CONSERVATIVE;
            case ADAPTATION_MODE_BALANCED -> com.ghatana.tts.core.api.AdaptationMode.BALANCED;
            case ADAPTATION_MODE_AGGRESSIVE -> com.ghatana.tts.core.api.AdaptationMode.AGGRESSIVE;
            default -> com.ghatana.tts.core.api.AdaptationMode.BALANCED;
        };
    }

    private com.ghatana.tts.core.api.PrivacyLevel mapPrivacyLevel(
            com.ghatana.tts.core.grpc.proto.PrivacyLevel level) {
        return switch (level) {
            case PRIVACY_LEVEL_HIGH -> com.ghatana.tts.core.api.PrivacyLevel.HIGH;
            case PRIVACY_LEVEL_MEDIUM -> com.ghatana.tts.core.api.PrivacyLevel.MEDIUM;
            case PRIVACY_LEVEL_LOW -> com.ghatana.tts.core.api.PrivacyLevel.LOW;
            default -> com.ghatana.tts.core.api.PrivacyLevel.HIGH;
        };
    }

    private ProfileResponse mapProfileResponse(com.ghatana.tts.core.api.UserProfile profile) {
        return ProfileResponse.newBuilder()
            .setProfileId(profile.getProfileId())
            .setDisplayName(profile.getDisplayName())
            .setSettings(ProfileSettings.newBuilder()
                .setPreferredLanguage(profile.getSettings().getPreferredLanguage())
                .setDefaultVoiceId(profile.getSettings().getDefaultVoiceId() != null 
                    ? profile.getSettings().getDefaultVoiceId() : "")
                .setAdaptationMode(mapAdaptationModeToProto(profile.getSettings().getAdaptationMode()))
                .setPrivacyLevel(mapPrivacyLevelToProto(profile.getSettings().getPrivacyLevel()))
                .build())
            .setStats(ProfileStats.newBuilder()
                .setTotalSynthesisTimeMs(profile.getStats().getTotalSynthesisTimeMs())
                .setTotalCharactersSynthesized(profile.getStats().getTotalCharactersSynthesized())
                .setCreatedAtMs(profile.getStats().getCreatedAtMs())
                .setLastUsedAtMs(profile.getStats().getLastUsedAtMs())
                .build())
            .build();
    }

    private com.ghatana.tts.core.grpc.proto.AdaptationMode mapAdaptationModeToProto(
            com.ghatana.tts.core.api.AdaptationMode mode) {
        return switch (mode) {
            case CONSERVATIVE -> com.ghatana.tts.core.grpc.proto.AdaptationMode.ADAPTATION_MODE_CONSERVATIVE;
            case BALANCED -> com.ghatana.tts.core.grpc.proto.AdaptationMode.ADAPTATION_MODE_BALANCED;
            case AGGRESSIVE -> com.ghatana.tts.core.grpc.proto.AdaptationMode.ADAPTATION_MODE_AGGRESSIVE;
        };
    }

    private com.ghatana.tts.core.grpc.proto.PrivacyLevel mapPrivacyLevelToProto(
            com.ghatana.tts.core.api.PrivacyLevel level) {
        return switch (level) {
            case HIGH -> com.ghatana.tts.core.grpc.proto.PrivacyLevel.PRIVACY_LEVEL_HIGH;
            case MEDIUM -> com.ghatana.tts.core.grpc.proto.PrivacyLevel.PRIVACY_LEVEL_MEDIUM;
            case LOW -> com.ghatana.tts.core.grpc.proto.PrivacyLevel.PRIVACY_LEVEL_LOW;
        };
    }
}
