package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.SynthesisOptions;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.tts.core.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
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
            .register(metrics);
        this.streamingTimer = Timer.builder("tts.synthesize.streaming")
            .description("Streaming synthesis latency")
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
                builder.addVoices(VoiceInfo.newBuilder()
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

    @Override
    public void createProfile(CreateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("createProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("getProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("updateProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void cloneVoice(CloneVoiceRequest request, StreamObserver<CloneVoiceResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("cloneVoice not yet implemented").asRuntimeException());
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
