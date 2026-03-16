package com.ghatana.stt.core.grpc;

import com.ghatana.audio.video.common.platform.FeatureStoreClient;
import com.ghatana.audio.video.common.security.JwtServerInterceptor;
import com.ghatana.stt.core.api.AdaptationResult;
import com.ghatana.stt.core.api.AdaptiveSTTEngine;
import com.ghatana.stt.core.api.AudioData;
import com.ghatana.stt.core.api.EngineStatus;
import com.ghatana.stt.core.api.TranscriptionResult;
import com.ghatana.stt.core.api.UserProfile;
import com.ghatana.stt.core.grpc.proto.AdaptRequest;
import com.ghatana.stt.core.grpc.proto.AdaptResponse;
import com.ghatana.stt.core.grpc.proto.AdaptationMode;
import com.ghatana.stt.core.grpc.proto.AdaptationStats;
import com.ghatana.stt.core.grpc.proto.AudioChunk;
import com.ghatana.stt.core.grpc.proto.CreateProfileRequest;
import com.ghatana.stt.core.grpc.proto.GetProfileRequest;
import com.ghatana.stt.core.grpc.proto.PrivacyLevel;
import com.ghatana.stt.core.grpc.proto.ProfileResponse;
import com.ghatana.stt.core.grpc.proto.ProfileSettings;
import com.ghatana.stt.core.grpc.proto.ProfileStats;
import com.ghatana.stt.core.grpc.proto.StatusRequest;
import com.ghatana.stt.core.grpc.proto.StatusResponse;
import com.ghatana.stt.core.grpc.proto.STTServiceGrpc;
import com.ghatana.stt.core.grpc.proto.TranscribeRequest;
import com.ghatana.stt.core.grpc.proto.TranscribeResponse;
import com.ghatana.stt.core.grpc.proto.Transcription;
import com.ghatana.stt.core.grpc.proto.UpdateProfileRequest;
import com.ghatana.stt.core.grpc.proto.WordTiming;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * gRPC service implementation that exposes {@link AdaptiveSTTEngine} via the
 * {@link com.ghatana.stt.core.grpc.proto.STTServiceGrpc} contract.
 *
 * <p>This class is intentionally thin and focuses on mapping between the
 * generated protobuf types and the internal Java API types.</p>
 *
 * @doc.type class
 * @doc.purpose gRPC adapter for AdaptiveSTTEngine
 * @doc.layer transport
 */
public class SttGrpcService extends STTServiceGrpc.STTServiceImplBase {

    private final AdaptiveSTTEngine engine;

    public SttGrpcService(AdaptiveSTTEngine engine) {
        this.engine = engine;
    }

    // ========================================================================
    // Unary Transcription
    // ========================================================================

    @Override
    public void transcribe(TranscribeRequest request, StreamObserver<TranscribeResponse> responseObserver) {
        try {
            AudioData audio = AudioData.fromPcm(request.getAudioData().toByteArray(), request.getSampleRate());
            com.ghatana.stt.core.api.TranscriptionOptions options =
                fromProtoOptions(request.hasOptions() ? request.getOptions() : null, request.getLanguage(), request.getProfileId());

            // Capture gRPC context values before crossing thread boundary
            final String subject = JwtServerInterceptor.CTX_SUBJECT.get();
            final String profileId = request.getProfileId();
            final String language = request.getLanguage();

            CompletableFuture
                .supplyAsync(() -> engine.transcribe(audio, options))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        responseObserver.onError(error);
                        return;
                    }
                    TranscribeResponse response = toProtoResponse(result);
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();

                    // Record inference features for ML pipeline (fire-and-forget)
                    if (result != null && subject != null) {
                        int bytesPerSample = audio.bitsPerSample() / 8;
                        long audioLengthMs = audio.pcmData().length * 1000L
                                / (audio.sampleRate() * audio.channels() * bytesPerSample);
                        java.util.Map<String, Object> features = new java.util.HashMap<>();
                        features.put("language", language != null ? language : "unknown");
                        features.put("profileId", profileId != null ? profileId : "none");
                        features.put("audioLengthMs", audioLengthMs);
                        features.put("transcriptLength", result.text() != null ? result.text().length() : 0);
                        features.put("timestampMs", System.currentTimeMillis());
                        FeatureStoreClient.getInstance().ingestAsync("stt", subject, features);
                    }
                });
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // ========================================================================
    // Streaming Transcription (stubbed – will be wired to StreamingSession later)
    // ========================================================================

    @Override
    public StreamObserver<AudioChunk> streamTranscribe(StreamObserver<Transcription> responseObserver) {
        return new StreamObserver<>() {
            private com.ghatana.stt.core.api.StreamingSession session;
            private UserProfile profile;

            @Override
            public void onNext(AudioChunk chunk) {
                try {
                    // Initialize session on first chunk
                    if (session == null) {
                        // Create streaming session
                        session = engine.createStreamingSession(profile);

                        // Register transcription callback
                        session.onTranscription(result -> {
                            Transcription transcription = Transcription.newBuilder()
                                .setText(result.text())
                                .setIsFinal(result.isFinal())
                                .setConfidence(result.confidence())
                                .setTimestampMs(System.currentTimeMillis())
                                .addAllWordTimings(toProtoWordTimings(result.wordTimings()))
                                .build();

                            responseObserver.onNext(transcription);
                        });

                        // Start the session
                        session.start();
                    }

                    // Feed audio chunk to session
                    com.ghatana.stt.core.api.StreamingSession.AudioChunk audioChunk =
                        com.ghatana.stt.core.api.StreamingSession.AudioChunk.of(
                            chunk.getAudioData().toByteArray(),
                            chunk.getSampleRate()
                        );

                    session.feedAudio(audioChunk);

                } catch (Exception e) {
                    responseObserver.onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (session != null) {
                    try {
                        session.stop();
                        session.close();
                    } catch (Exception e) {
                        // Log but don't propagate
                    }
                }
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                try {
                    if (session != null) {
                        // Stop session and wait for final transcription
                        session.stop();

                        // Get session statistics
                        var stats = session.getStats();

                        // Send final transcription with stats as metadata
                        Transcription finalTranscription = Transcription.newBuilder()
                            .setText("[Session completed: " + stats.chunksProcessed() + " chunks processed]")
                            .setIsFinal(true)
                            .setConfidence(1.0f)
                            .setTimestampMs(System.currentTimeMillis())
                            .build();

                        responseObserver.onNext(finalTranscription);

                        // Clean up
                        session.close();
                    }

                    responseObserver.onCompleted();

                } catch (Exception e) {
                    responseObserver.onError(e);
                }
            }
        };
    }

    // ========================================================================
    // Adaptation
    // ========================================================================

    @Override
    public void adaptModel(AdaptRequest request, StreamObserver<AdaptResponse> responseObserver) {
        try {
            List<com.ghatana.stt.core.api.Interaction> interactions = new ArrayList<>();
            for (com.ghatana.stt.core.grpc.proto.Interaction protoInteraction : request.getInteractionsList()) {
                com.ghatana.stt.core.api.Interaction interaction = new com.ghatana.stt.core.api.Interaction(
                    protoInteraction.getOriginalTranscript(),
                    protoInteraction.getCorrectedTranscript(),
                    null, // audio features not wired yet
                    protoInteraction.getContextId(),
                    Instant.ofEpochMilli(protoInteraction.getTimestampMs()),
                    0
                );
                interactions.add(interaction);
            }

            AdaptationResult result = engine.adaptFromInteractions(interactions);

            AdaptationStats stats = AdaptationStats.newBuilder()
                .setInteractionsProcessed(result.interactionsProcessed())
                .setWerBefore(result.werBefore() != null ? result.werBefore() : 0.0f)
                .setWerAfter(result.werAfter() != null ? result.werAfter() : 0.0f)
                .setNewVocabularyTerms(result.newVocabularyTerms())
                .build();

            AdaptResponse response = AdaptResponse.newBuilder()
                .setSuccess(result.success())
                .setMessage(result.message())
                .setStats(stats)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // ========================================================================
    // Status & Metrics
    // ========================================================================

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try {
            EngineStatus status = engine.getStatus();
            com.ghatana.stt.core.api.EngineMetrics metrics = request.getIncludeMetrics() ? engine.getMetrics() : null;
            List<com.ghatana.stt.core.api.ModelInfo> models = request.getIncludeModelInfo() ? engine.getAvailableModels() : List.of();

            StatusResponse.Builder builder = StatusResponse.newBuilder()
                .setState(toProtoState(status.state()))
                .setActiveModel(status.activeModelId() == null ? "" : status.activeModelId());

            if (metrics != null) {
                builder.setMetrics(toProtoMetrics(metrics));
            }

            for (com.ghatana.stt.core.api.ModelInfo model : models) {
                builder.addAvailableModels(toProtoModel(model));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // ========================================================================
    // Profile Management
    // ========================================================================

    @Override
    public void createProfile(CreateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            // Enrollment audio is currently ignored; engine stub creates a generic profile
            UserProfile profile = engine.createUserProfile(List.of());
            if (!request.getDisplayName().isBlank()) {
                profile.setDisplayName(request.getDisplayName());
            }
            if (request.hasSettings()) {
                profile.setSettings(fromProtoSettings(request.getSettings()));
            }
            engine.saveUserProfile(profile);

            ProfileResponse response = toProtoProfile(profile);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            UserProfile profile = engine.loadUserProfile(request.getProfileId());
            if (profile == null) {
                responseObserver.onError(new IllegalArgumentException("Profile not found: " + request.getProfileId()));
                return;
            }
            responseObserver.onNext(toProtoProfile(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        try {
            UserProfile profile = engine.loadUserProfile(request.getProfileId());
            if (profile == null) {
                responseObserver.onError(new IllegalArgumentException("Profile not found: " + request.getProfileId()));
                return;
            }

            if (request.hasSettings()) {
                profile.setSettings(fromProtoSettings(request.getSettings()));
            }

            engine.saveUserProfile(profile);
            responseObserver.onNext(toProtoProfile(profile));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // ========================================================================
    // Mapping Helpers
    // ========================================================================

    private static com.ghatana.stt.core.api.TranscriptionOptions fromProtoOptions(
        com.ghatana.stt.core.grpc.proto.TranscriptionOptions proto,
        String language,
        String profileId
    ) {
        com.ghatana.stt.core.api.TranscriptionOptions.Builder builder =
            com.ghatana.stt.core.api.TranscriptionOptions.builder();

        if (language != null && !language.isBlank()) {
            builder.language(language);
        }
        if (proto != null) {
            builder
                .enablePunctuation(proto.getEnablePunctuation())
                .enableWordTimings(proto.getEnableWordTimings())
                .contextId(proto.getContextId())
                .maxAlternatives(proto.getMaxAlternatives())
                .confidenceThreshold(proto.getConfidenceThreshold());
        }
        if (profileId != null && !profileId.isBlank()) {
            builder.profileId(profileId);
        }
        return builder.build();
    }

    private static TranscribeResponse toProtoResponse(TranscriptionResult result) {
        TranscribeResponse.Builder builder = TranscribeResponse.newBuilder()
            .setText(result.text())
            .setConfidence(result.confidence())
            .setProcessingTimeMs(result.processingTimeMs());

        if (result.modelUsed() != null) {
            builder.setModelUsed(result.modelUsed());
        }
        for (TranscriptionResult.WordTiming wt : result.wordTimings()) {
            builder.addWordTimings(WordTiming.newBuilder()
                .setWord(wt.word())
                .setStartMs(wt.startMs())
                .setEndMs(wt.endMs())
                .setConfidence(wt.confidence())
                .build());
        }
        return builder.build();
    }

    private static com.ghatana.stt.core.grpc.proto.EngineState toProtoState(EngineStatus.State state) {
        return switch (state) {
            case INITIALIZING -> com.ghatana.stt.core.grpc.proto.EngineState.ENGINE_STATE_INITIALIZING;
            case READY -> com.ghatana.stt.core.grpc.proto.EngineState.ENGINE_STATE_READY;
            case BUSY -> com.ghatana.stt.core.grpc.proto.EngineState.ENGINE_STATE_BUSY;
            case ERROR -> com.ghatana.stt.core.grpc.proto.EngineState.ENGINE_STATE_ERROR;
            default -> com.ghatana.stt.core.grpc.proto.EngineState.ENGINE_STATE_UNSPECIFIED;
        };
    }

    private static com.ghatana.stt.core.grpc.proto.EngineMetrics toProtoMetrics(com.ghatana.stt.core.api.EngineMetrics metrics) {
        return com.ghatana.stt.core.grpc.proto.EngineMetrics.newBuilder()
            .setRealTimeFactor(metrics.realTimeFactor())
            .setMemoryUsageBytes(metrics.memoryUsageBytes())
            .setActiveSessions(metrics.activeSessions())
            .setTotalTranscriptions(metrics.totalTranscriptions())
            .setAverageConfidence(metrics.averageConfidence())
            .build();
    }

    private static com.ghatana.stt.core.grpc.proto.ModelInfo toProtoModel(com.ghatana.stt.core.api.ModelInfo model) {
        return com.ghatana.stt.core.grpc.proto.ModelInfo.newBuilder()
            .setModelId(model.modelId())
            .setName(model.name())
            .setVersion(model.version())
            .addAllLanguages(model.languages())
            .setSizeBytes(model.sizeBytes())
            .setIsLoaded(model.isLoaded())
            .build();
    }

    private static UserProfile.ProfileSettings fromProtoSettings(ProfileSettings proto) {
        UserProfile.AdaptationMode mode = switch (proto.getAdaptationMode()) {
            case ADAPTATION_MODE_CONSERVATIVE -> UserProfile.AdaptationMode.CONSERVATIVE;
            case ADAPTATION_MODE_AGGRESSIVE -> UserProfile.AdaptationMode.AGGRESSIVE;
            case ADAPTATION_MODE_BALANCED, ADAPTATION_MODE_UNSPECIFIED, UNRECOGNIZED ->
                UserProfile.AdaptationMode.BALANCED;
        };

        UserProfile.PrivacyLevel privacy = switch (proto.getPrivacyLevel()) {
            case PRIVACY_LEVEL_HIGH -> UserProfile.PrivacyLevel.HIGH;
            case PRIVACY_LEVEL_MEDIUM -> UserProfile.PrivacyLevel.MEDIUM;
            case PRIVACY_LEVEL_LOW -> UserProfile.PrivacyLevel.LOW;
            case PRIVACY_LEVEL_UNSPECIFIED, UNRECOGNIZED -> UserProfile.PrivacyLevel.HIGH;
        };

        boolean storeAudio = privacy != UserProfile.PrivacyLevel.HIGH;
        boolean storeTranscripts = true;

        return new UserProfile.ProfileSettings(
            proto.getPreferredLanguage().isBlank() ? "en-US" : proto.getPreferredLanguage(),
            mode,
            privacy,
            storeAudio,
            storeTranscripts
        );
    }

    private static ProfileResponse toProtoProfile(UserProfile profile) {
        UserProfile.ProfileSettings s = profile.getSettings();

        ProfileSettings settings = ProfileSettings.newBuilder()
            .setPreferredLanguage(s.preferredLanguage())
            .setAdaptationMode(switch (s.adaptationMode()) {
                case CONSERVATIVE -> AdaptationMode.ADAPTATION_MODE_CONSERVATIVE;
                case AGGRESSIVE -> AdaptationMode.ADAPTATION_MODE_AGGRESSIVE;
                case BALANCED -> AdaptationMode.ADAPTATION_MODE_BALANCED;
            })
            .setPrivacyLevel(switch (s.privacyLevel()) {
                case HIGH -> PrivacyLevel.PRIVACY_LEVEL_HIGH;
                case MEDIUM -> PrivacyLevel.PRIVACY_LEVEL_MEDIUM;
                case LOW -> PrivacyLevel.PRIVACY_LEVEL_LOW;
            })
            .build();

        UserProfile.AdaptationStatistics stats = profile.getStats();
        ProfileStats protoStats = ProfileStats.newBuilder()
            .setTotalTranscriptionTimeMs(stats.getTotalTranscriptionTimeMs())
            .setVocabularySize(profile.getPersonalVocabulary().size())
            .setEstimatedWer(stats.getEstimatedWer())
            .setCreatedAtMs(profile.getCreatedAt().toEpochMilli())
            .setLastUsedAtMs(profile.getLastUpdatedAt().toEpochMilli())
            .build();

        return ProfileResponse.newBuilder()
            .setProfileId(profile.getProfileId())
            .setDisplayName(profile.getDisplayName())
            .setStats(protoStats)
            .setSettings(settings)
            .build();
    }

    /**
     * Convert internal word timings to proto word timings.
     */
    private static List<WordTiming> toProtoWordTimings(List<TranscriptionResult.WordTiming> wordTimings) {
        List<WordTiming> protoTimings = new ArrayList<>();
        for (TranscriptionResult.WordTiming wt : wordTimings) {
            protoTimings.add(WordTiming.newBuilder()
                .setWord(wt.word())
                .setStartMs(wt.startMs())
                .setEndMs(wt.endMs())
                .setConfidence(wt.confidence())
                .build());
        }
        return protoTimings;
    }
}
