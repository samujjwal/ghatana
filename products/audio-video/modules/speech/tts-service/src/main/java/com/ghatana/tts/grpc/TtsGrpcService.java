package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.*;
import com.ghatana.tts.grpc.proto.*;

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
public class TtsGrpcService extends TtsServiceGrpc.TtsServiceImplBase {
    
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
