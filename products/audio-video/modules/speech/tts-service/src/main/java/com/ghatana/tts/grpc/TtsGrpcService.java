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
import java.util.UUID;

/**
 * TTS gRPC Service — bridges the proto {@code TTSService} API to the
 * platform {@link com.ghatana.media.tts.api.TtsEngine}.
 *
 * <p>Synthesize, StreamSynthesize, GetVoices, LoadVoice, GetStatus, and
 * GetMetrics are fully implemented. Profile and voice-cloning RPCs return
 * {@code UNIMPLEMENTED} and are planned for a future sprint.
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
     * Constructs the service, wiring the platform {@link AudioVideoLibrary}
     * from environment configuration.
     *
     * @param metrics Micrometer registry for latency timers
     */
    public TtsGrpcService(MeterRegistry metrics) {
        TtsConfig config = TtsConfig.builder()
                .voiceModelPath(Paths.get(System.getenv().getOrDefault("TTS_MODEL_PATH", "/models/piper-en.onnx")))
                .defaultVoiceId(System.getenv().getOrDefault("TTS_DEFAULT_VOICE", "piper-en"))
                .sampleRate(22050)
                .maxConcurrentRequests(Integer.parseInt(
                        System.getenv().getOrDefault("TTS_MAX_CONCURRENT", "10")))
                .enableProsody(true)
                .build();

        this.library = AudioVideoLibrary.builder()
                .withTtsConfig(config)
                .build();

        this.synthesizeTimer = Timer.builder("tts.synthesize")
                .description("Synthesis latency")
                .register(metrics);
        this.streamingTimer = Timer.builder("tts.synthesize.streaming")
                .description("Streaming synthesis latency")
                .register(metrics);

        LOG.info("TTS Service initialized with voice: {}", config.defaultVoiceId());
    }

    // -------------------------------------------------------------------------
    // Synthesize (blocking)
    // -------------------------------------------------------------------------

    @Override
    public void synthesize(SynthesizeRequest request, StreamObserver<SynthesizeResponse> responseObserver) {
        String cid = cid();
        long start = System.currentTimeMillis();
        synthesizeTimer.record(() -> {
            try {
                String text = request.getText();
                if (text == null || text.isEmpty()) {
                    responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                            .withDescription("Text cannot be empty").asRuntimeException());
                    return;
                }
                if (text.length() > 5000) {
                    responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                            .withDescription("Text exceeds 5000 characters").asRuntimeException());
                    return;
                }

                SynthesisOptions options = SynthesisOptions.builder()
                        .voiceId(request.getOptions().getVoiceId())
                        .speed(request.getOptions().getSpeed())
                        .pitch(request.getOptions().getPitch())
                        .volume(request.getOptions().getVolume())
                        .build();

                com.ghatana.media.common.AudioData audio;
                try (TtsEngine tts = library.getTtsEngine()) {
                    audio = tts.synthesize(text, options);
                }

                long elapsed = System.currentTimeMillis() - start;
                double durationMs = (audio.data().length / 2.0 / audio.sampleRate()) * 1000;
                double rtf = elapsed / durationMs;
                LOG.info("[{}] synthesize: elapsed={}ms, rtf={}", cid, elapsed, String.format("%.2f", rtf));
                if (rtf > 1.0) {
                    LOG.warn("[{}] RTF={} — synthesis slower than real-time",
                            cid, String.format("%.2f", rtf));
                }

                SynthesizeResponse response = SynthesizeResponse.newBuilder()
                        .setAudioData(com.google.protobuf.ByteString.copyFrom(audio.data()))
                        .setSampleRate(audio.sampleRate())
                        .setDurationMs((int) durationMs)
                        .setProcessingTimeMs((int) elapsed)
                        .setVoiceUsed(options.voiceId() != null ? options.voiceId() : "")
                        .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();

            } catch (com.ghatana.media.common.ValidationError e) {
                LOG.warn("[{}] Validation: {}", cid, e.getMessage());
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage()).asRuntimeException());
            } catch (Exception e) {
                LOG.error("[{}] Synthesis failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Synthesis failed").asRuntimeException());
            }
        });
    }

    // -------------------------------------------------------------------------
    // StreamSynthesize (server-side streaming)
    // -------------------------------------------------------------------------

    @Override
    public void streamSynthesize(SynthesizeRequest request, StreamObserver<AudioChunk> responseObserver) {
        String cid = cid();
        streamingTimer.record(() -> {
            try {
                SynthesisOptions options = SynthesisOptions.builder()
                        .voiceId(request.getOptions().getVoiceId())
                        .speed(request.getOptions().getSpeed())
                        .build();

                try (TtsEngine tts = library.getTtsEngine()) {
                    // The lambda chunk type is inferred as com.ghatana.media.common.AudioChunk
                    tts.synthesizeStreaming(request.getText(), options, chunk -> {
                        AudioChunk protoChunk = AudioChunk.newBuilder()
                                .setAudioData(com.google.protobuf.ByteString.copyFrom(chunk.data()))
                                .setSampleRate(22050)
                                .setIsFinal(chunk.isLast())
                                .build();
                        responseObserver.onNext(protoChunk);
                        if (chunk.isLast()) {
                            LOG.info("[{}] streamSynthesize complete: seq={}", cid, chunk.sequence());
                            responseObserver.onCompleted();
                        }
                    });
                }
            } catch (Exception e) {
                LOG.error("[{}] Stream synthesis failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Streaming synthesis failed").asRuntimeException());
            }
        });
    }

    // -------------------------------------------------------------------------
    // GetVoices
    // -------------------------------------------------------------------------

    @Override
    public void getVoices(GetVoicesRequest request, StreamObserver<GetVoicesResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            List<com.ghatana.media.tts.api.VoiceInfo> voices = request.getLanguage().isEmpty()
                    ? tts.getAvailableVoices()
                    : tts.getAvailableVoices(new java.util.Locale(request.getLanguage()));

            GetVoicesResponse.Builder builder = GetVoicesResponse.newBuilder();
            for (com.ghatana.media.tts.api.VoiceInfo v : voices) {
                builder.addVoices(VoiceInfo.newBuilder()
                        .setVoiceId(v.voiceId())
                        .setName(v.name())
                        .addLanguages(v.language().toLanguageTag())
                        .setIsCloned(v.isCloned())
                        .setSizeBytes(v.modelSizeBytes())
                        .build());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOG.error("getVoices failed: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to list voices").asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // GetStatus
    // -------------------------------------------------------------------------

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            com.ghatana.media.common.EngineStatus status = tts.getStatus();
            StatusResponse response = StatusResponse.newBuilder()
                    .setActiveVoice(status.modelId() != null ? status.modelId() : "")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Status unavailable").asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // GetMetrics
    // -------------------------------------------------------------------------

    @Override
    public void getMetrics(MetricsRequest request, StreamObserver<MetricsResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            com.ghatana.media.common.EngineMetrics m = tts.getMetrics();
            MetricsResponse response = MetricsResponse.newBuilder()
                    .setTotalSyntheses((int) m.requestCount())
                    .setAverageLatencyMs((float) m.avgLatencyMs())
                    .setMemoryUsageBytes(m.memoryUsageBytes())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Metrics unavailable").asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // LoadVoice
    // -------------------------------------------------------------------------

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
            LOG.error("loadVoice failed: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to load voice: " + e.getMessage()).asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // Unimplemented — profile management and voice cloning (future sprint)
    // -------------------------------------------------------------------------

    @Override
    public void createProfile(CreateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("createProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("getProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("updateProfile not yet implemented").asRuntimeException());
    }

    @Override
    public void cloneVoice(CloneVoiceRequest request, StreamObserver<CloneVoiceResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("cloneVoice not yet implemented").asRuntimeException());
    }

    @Override
    public void submitFeedback(FeedbackRequest request, StreamObserver<FeedbackResponse> responseObserver) {
        LOG.info("submitFeedback: type={}, voiceId={}", request.getFeedbackType(), request.getVoiceId());
        responseObserver.onNext(FeedbackResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String cid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}


import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;

/**
 * TTS gRPC Service using Platform Library.
 * 
 * <p>Migrated from legacy implementation to use platform library:
 * <ul>
 *   <li>Real-time factor (RTF) monitoring</li>
 *   <li>Streaming synthesis with backpressure</li>
 *   <li>Voice management and caching</li>
 *   <li>Prosody control (speed, pitch, volume)</li>
 * </ul>
 */
public class TtsGrpcService extends TTSServiceGrpc.TTSServiceImplBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(TtsGrpcService.class);
    
    private final AudioVideoLibrary library;
    private final MeterRegistry metrics;
    
    private final Timer synthesizeTimer;
    private final Timer streamingTimer;
    
    public TtsGrpcService(MeterRegistry metrics) {
        this.metrics = metrics;
        
        TtsConfig config = TtsConfig.builder()
            .voiceModelPath(Paths.get(System.getenv().getOrDefault("TTS_MODEL_PATH", "/models/piper-en.onnx")))
            .defaultVoiceId(System.getenv().getOrDefault("TTS_DEFAULT_VOICE", "piper-en"))
            .sampleRate(22050)
            .maxConcurrentRequests(Integer.parseInt(System.getenv().getOrDefault("TTS_MAX_CONCURRENT", "10")))
            .enableProsody(true)
            .build();
        
        this.library = AudioVideoLibrary.builder()
            .withTtsConfig(config)
            .build();
        
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
        String correlationId = generateCorrelationId();
        long startTime = System.currentTimeMillis();
        
        synthesizeTimer.record(() -> {
            try {
                LOG.info("[{}] Synthesis request: text_length={}, voice={}",
                    correlationId,
                    request.getText().length(),
                    request.getOptions().getVoiceId()
                );
                
                // Validate
                if (request.getText().isEmpty()) {
                    responseObserver.onError(
                        io.grpc.Status.INVALID_ARGUMENT
                            .withDescription("Text cannot be empty")
                            .asRuntimeException()
                    );
                    return;
                }
                
                if (request.getText().length() > 5000) {
                    responseObserver.onError(
                        io.grpc.Status.INVALID_ARGUMENT
                            .withDescription("Text exceeds maximum length of 5000 characters")
                            .asRuntimeException()
                    );
                    return;
                }
                
                SynthesisOptions options = SynthesisOptions.builder()
                    .voiceId(request.getOptions().getVoiceId())
                    .speed(request.getOptions().getSpeed())
                    .pitch(request.getOptions().getPitch())
                    .volume(request.getOptions().getVolume())
                    .build();
                
                AudioData audio;
                try (TtsEngine tts = library.getTtsEngine()) {
                    audio = tts.synthesize(request.getText(), options);
                }
                
                long synthesisTime = System.currentTimeMillis() - startTime;
                double audioDurationMs = (audio.data().length / 2.0 / audio.sampleRate()) * 1000;
                double rtf = synthesisTime / audioDurationMs;
                
                LOG.info("[{}] Synthesis completed: latency={}ms, audio_duration={}ms, RTF={:.2f}",
                    correlationId,
                    synthesisTime,
                    (int) audioDurationMs,
                    rtf
                );
                
                // Alert if RTF > 1.0 (not real-time capable)
                if (rtf > 1.0) {
                    LOG.warn("[{}] RTF exceeds 1.0: {:.2f} - synthesis slower than real-time", 
                        correlationId, rtf);
                }
                
                SynthesizeResponse response = SynthesizeResponse.newBuilder()
                    .setAudio(AudioDataProto.newBuilder()
                        .setData(com.google.protobuf.ByteString.copyFrom(audio.data()))
                        .setSampleRate(audio.sampleRate())
                        .setChannels(audio.channels())
                        .setBitsPerSample(audio.bitsPerSample())
                        .build()
                    )
                    .setVoiceId(options.voiceId())
                    .setRtf(rtf)
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (ValidationError e) {
                LOG.warn("[{}] Validation error: {}", correlationId, e.getMessage());
                responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage())
                        .asRuntimeException()
                );
            } catch (Exception e) {
                LOG.error("[{}] Synthesis error: {}", correlationId, e.getMessage(), e);
                responseObserver.onError(
                    io.grpc.Status.INTERNAL
                        .withDescription("Synthesis failed")
                        .asRuntimeException()
                );
            }
        });
    }
    
    @Override
    public void synthesizeStreaming(SynthesizeRequest request, StreamObserver<StreamingSynthesizeResponse> responseObserver) {
        String correlationId = generateCorrelationId();
        
        streamingTimer.record(() -> {
            try {
                LOG.info("[{}] Streaming synthesis request: text_length={}",
                    correlationId,
                    request.getText().length()
                );
                
                SynthesisOptions options = SynthesisOptions.builder()
                    .voiceId(request.getOptions().getVoiceId())
                    .speed(request.getOptions().getSpeed())
                    .build();
                
                try (TtsEngine tts = library.getTtsEngine()) {
                    tts.synthesizeStreaming(request.getText(), options, chunk -> {
                        StreamingSynthesizeResponse response = StreamingSynthesizeResponse.newBuilder()
                            .setAudioChunk(AudioChunkProto.newBuilder()
                                .setData(com.google.protobuf.ByteString.copyFrom(chunk.data()))
                                .setSequence(chunk.sequence())
                                .setIsLast(chunk.isLast())
                                .build()
                            )
                            .build();
                        
                        responseObserver.onNext(response);
                        
                        if (chunk.isLast()) {
                            LOG.info("[{}] Streaming synthesis completed: {} chunks", 
                                correlationId, chunk.sequence() + 1);
                            responseObserver.onCompleted();
                        }
                    });
                }
                
            } catch (Exception e) {
                LOG.error("[{}] Streaming error: {}", correlationId, e.getMessage(), e);
                responseObserver.onError(
                    io.grpc.Status.INTERNAL
                        .withDescription("Streaming synthesis failed")
                        .asRuntimeException()
                );
            }
        });
    }
    
    @Override
    public void getVoices(GetVoicesRequest request, StreamObserver<GetVoicesResponse> responseObserver) {
        try (TtsEngine tts = library.getTtsEngine()) {
            List<VoiceInfo> voices = request.hasLanguage()
                ? tts.getAvailableVoices(new java.util.Locale(request.getLanguage()))
                : tts.getAvailableVoices();
            
            GetVoicesResponse.Builder response = GetVoicesResponse.newBuilder();
            for (VoiceInfo voice : voices) {
                response.addVoices(VoiceInfoProto.newBuilder()
                    .setVoiceId(voice.voiceId())
                    .setName(voice.name())
                    .setLanguage(voice.language().toLanguageTag())
                    .setSampleRate(voice.sampleRate())
                    .setIsCloned(voice.isCloned())
                    .build()
                );
            }
            
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            LOG.error("Error listing voices: {}", e.getMessage(), e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Failed to list voices")
                    .asRuntimeException()
            );
        }
    }
    
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
