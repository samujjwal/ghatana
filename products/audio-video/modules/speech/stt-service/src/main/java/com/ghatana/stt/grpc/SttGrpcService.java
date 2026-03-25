package com.ghatana.stt.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.stt.api.*;
import com.ghatana.stt.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * STT gRPC Service using Platform Library.
 * 
 * <p>Migrated from legacy implementation to use platform library:
 * <ul>
 *   <li>Proper error handling with typed exceptions</li>
 *   <li>Metrics collection for observability</li>
 *   <li>Structured logging with correlation IDs</li>
 *   <li>Graceful degradation with cloud fallback</li>
 *   <li>Input validation and sanitization</li>
 * </ul>
 * 
 * <p><b>Pattern for other products to follow.</b>
 */
public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
    
    private static final Logger LOG = LoggerFactory.getLogger(SttGrpcService.class);
    
    private final AudioVideoLibrary library;
    private final MeterRegistry metrics;
    private final CloudSttFallback cloudFallback;
    
    // Metrics
    private final Timer transcribeTimer;
    private final Timer streamingTimer;
    
    public SttGrpcService(MeterRegistry metrics, CloudSttFallback cloudFallback) {
        this.metrics = metrics;
        this.cloudFallback = cloudFallback;
        
        // Initialize library with configuration
        SttConfig config = SttConfig.builder()
            .modelPath(Paths.get(System.getenv().getOrDefault("STT_MODEL_PATH", "/models/whisper-base.onnx")))
            .modelId(System.getenv().getOrDefault("STT_MODEL_ID", "whisper-base"))
            .useGpu(Boolean.parseBoolean(System.getenv().getOrDefault("STT_USE_GPU", "true")))
            .maxConcurrentRequests(Integer.parseInt(System.getenv().getOrDefault("STT_MAX_CONCURRENT", "10")))
            .build();
        
        this.library = AudioVideoLibrary.builder()
            .withSttConfig(config)
            .build();
        
        // Initialize metrics
        this.transcribeTimer = Timer.builder("stt.transcribe")
            .description("Transcription latency")
            .register(metrics);
        
        this.streamingTimer = Timer.builder("stt.streaming")
            .description("Streaming transcription latency")
            .register(metrics);
        
        LOG.info("STT Service initialized with model: {}", config.modelId());
    }
    
    @Override
    public void transcribe(TranscribeRequest request, StreamObserver<TranscribeResponse> responseObserver) {
        String correlationId = generateCorrelationId();
        
        transcribeTimer.record(() -> {
            try {
                LOG.info("[{}] Transcription request received: sampleRate={}, channels={}, duration={}ms",
                    correlationId,
                    request.getAudio().getSampleRate(),
                    request.getAudio().getChannels(),
                    request.getAudio().getDurationMs()
                );
                
                // Input validation
                ValidationResult validation = validateRequest(request);
                if (!validation.valid()) {
                    LOG.warn("[{}] Validation failed: {}", correlationId, validation.error());
                    responseObserver.onError(
                        io.grpc.Status.INVALID_ARGUMENT
                            .withDescription(validation.error())
                            .asRuntimeException()
                    );
                    return;
                }
                
                // Convert protobuf to library types
                AudioData audio = convertAudio(request.getAudio());
                TranscriptionOptions options = convertOptions(request.getOptions());
                
                // Perform transcription
                TranscriptionResult result;
                try (SttEngine stt = library.getSttEngine()) {
                    result = stt.transcribe(audio, options);
                } catch (InferenceError e) {
                    if (e.isRetryable() && cloudFallback != null && cloudFallback.isAvailable()) {
                        LOG.info("[{}] Falling back to cloud STT", correlationId);
                        result = cloudFallback.transcribe(audio, options);
                    } else {
                        throw e;
                    }
                }
                
                // Build response
                TranscribeResponse response = convertResponse(result);
                
                LOG.info("[{}] Transcription completed: text_length={}, confidence={}, latency={}ms",
                    correlationId,
                    result.getText().length(),
                    result.confidence(),
                    result.latency().toMillis()
                );
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (ValidationError e) {
                LOG.warn("[{}] Validation error: {}", correlationId, e.getMessage());
                responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT
                        .withDescription(e.getMessage())
                        .asRuntimeException()
                );
            } catch (InferenceError e) {
                LOG.error("[{}] Inference error (retryable={}): {}", 
                    correlationId, e.isRetryable(), e.getMessage(), e);
                
                io.grpc.Status status = e.isRetryable() 
                    ? io.grpc.Status.UNAVAILABLE 
                    : io.grpc.Status.INTERNAL;
                
                responseObserver.onError(
                    status.withDescription(e.getMessage()).asRuntimeException()
                );
            } catch (Exception e) {
                LOG.error("[{}] Unexpected error: {}", correlationId, e.getMessage(), e);
                responseObserver.onError(
                    io.grpc.Status.INTERNAL
                        .withDescription("Internal error occurred")
                        .asRuntimeException()
                );
            }
        });
    }
    
    @Override
    public StreamObserver<StreamingTranscribeRequest> streamingTranscribe(
        StreamObserver<StreamingTranscribeResponse> responseObserver) {
        
        String sessionId = generateCorrelationId();
        LOG.info("[{}] Streaming transcription session started", sessionId);
        
        streamingTimer.record(() -> {
            SttEngine engine = library.getSttEngine();
            
            StreamingSession session = engine.createStreamingSession();
            
            session.onTranscription(transcript -> {
                StreamingTranscribeResponse response = StreamingTranscribeResponse.newBuilder()
                    .setText(transcript.text())
                    .setIsFinal(transcript.isFinal())
                    .setConfidence(transcript.confidence())
                    .build();
                
                responseObserver.onNext(response);
                
                if (transcript.isFinal()) {
                    LOG.info("[{}] Final transcript received: {}", sessionId, transcript.text());
                }
            });
            
            session.onError(error -> {
                LOG.error("[{}] Streaming error: {}", sessionId, error.getMessage());
                responseObserver.onError(
                    io.grpc.Status.INTERNAL
                        .withDescription(error.getMessage())
                        .asRuntimeException()
                );
            });
            
            return new StreamObserver<>() {
                private int chunkCount = 0;
                
                @Override
                public void onNext(StreamingTranscribeRequest request) {
                    try {
                        AudioChunk chunk = AudioChunk.builder()
                            .data(request.getAudioChunk().toByteArray())
                            .sequence(request.getSequence())
                            .isLast(request.getIsLast())
                            .timestamp(System.currentTimeMillis())
                            .build();
                        
                        session.feedAudio(chunk);
                        chunkCount++;
                        
                        if (request.getIsLast()) {
                            LOG.info("[{}] Received {} chunks, ending stream", sessionId, chunkCount);
                            session.endStream();
                        }
                    } catch (Exception e) {
                        LOG.error("[{}] Error processing chunk {}: {}", sessionId, chunkCount, e.getMessage());
                        responseObserver.onError(
                            io.grpc.Status.INTERNAL
                                .withDescription("Error processing audio chunk")
                                .asRuntimeException()
                        );
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    LOG.error("[{}] Client error: {}", sessionId, t.getMessage());
                    session.close();
                    responseObserver.onError(t);
                }
                
                @Override
                public void onCompleted() {
                    LOG.info("[{}] Client completed stream", sessionId);
                    session.endStream();
                    responseObserver.onCompleted();
                }
            };
        });
    }
    
    @Override
    public void getAvailableModels(GetModelsRequest request, StreamObserver<GetModelsResponse> responseObserver) {
        try (SttEngine stt = library.getSttEngine()) {
            List<ModelInfo> models = stt.getAvailableModels();
            
            GetModelsResponse.Builder response = GetModelsResponse.newBuilder();
            for (ModelInfo model : models) {
                response.addModels(ModelInfoProto.newBuilder()
                    .setModelId(model.modelId())
                    .setName(model.name())
                    .setVersion(model.version())
                    .setSizeBytes(model.sizeBytes())
                    .setSupportsGpu(model.supportsGpu())
                    .build()
                );
            }
            
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Error listing models: {}", e.getMessage(), e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Failed to list models")
                    .asRuntimeException()
            );
        }
    }
    
    // Helper methods
    
    private ValidationResult validateRequest(TranscribeRequest request) {
        if (request.getAudio().getData().isEmpty()) {
            return ValidationResult.fail("Audio data is empty");
        }
        
        if (request.getAudio().getSampleRate() < 8000 || request.getAudio().getSampleRate() > 192000) {
            return ValidationResult.fail("Invalid sample rate: " + request.getAudio().getSampleRate());
        }
        
        // Max 100 MB
        long maxSize = 100 * 1024 * 1024;
        if (request.getAudio().getData().size() > maxSize) {
            return ValidationResult.fail("Audio data exceeds maximum size of 100 MB");
        }
        
        return ValidationResult.ok();
    }
    
    private AudioData convertAudio(AudioDataProto proto) {
        return AudioData.builder()
            .data(proto.getData().toByteArray())
            .sampleRate(proto.getSampleRate())
            .channels(proto.getChannels())
            .bitsPerSample(proto.getBitsPerSample())
            .duration(java.time.Duration.ofMillis(proto.getDurationMs()))
            .format(AudioFormat.valueOf(proto.getFormat().name()))
            .build();
    }
    
    private TranscriptionOptions convertOptions(TranscriptionOptionsProto proto) {
        return TranscriptionOptions.builder()
            .language(new java.util.Locale(proto.getLanguage()))
            .enableTimestamps(proto.getEnableTimestamps())
            .enablePunctuation(proto.getEnablePunctuation())
            .build();
    }
    
    private TranscribeResponse convertResponse(TranscriptionResult result) {
        TranscribeResponse.Builder builder = TranscribeResponse.newBuilder()
            .setText(result.getText())
            .setConfidence(result.confidence())
            .setLanguage(result.language())
            .setModelId(result.modelId())
            .setLatencyMs(result.latency().toMillis());
        
        for (WordTiming word : result.words()) {
            builder.addWords(WordTimingProto.newBuilder()
                .setWord(word.word())
                .setStartMs(word.startMs())
                .setEndMs(word.endMs())
                .setConfidence(word.confidence())
                .build()
            );
        }
        
        return builder.build();
    }
    
    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
    
    // Inner classes
    
    private record ValidationResult(boolean valid, String error) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult fail(String error) {
            return new ValidationResult(false, error);
        }
    }
    
    /**
     * Cloud STT fallback interface.
     */
    public interface CloudSttFallback {
        boolean isAvailable();
        TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options);
    }
}
