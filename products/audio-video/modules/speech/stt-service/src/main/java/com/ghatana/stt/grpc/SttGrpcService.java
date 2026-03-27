package com.ghatana.stt.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.stt.core.grpc.proto.*;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

/**
 * STT gRPC Service — bridges the proto STTService API to the platform SttEngine.
 *
 * @doc.type class
 * @doc.purpose gRPC facade for platform SttEngine
 * @doc.layer product
 * @doc.pattern Service
 */
public class SttGrpcService extends STTServiceGrpc.STTServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(SttGrpcService.class);

    private final AudioVideoLibrary library;
    private final Timer transcribeTimer;

    /**
     * Package-private constructor for unit testing — supply a pre-configured library.
     * Avoids native-library / file-system dependencies during tests.
     */
    SttGrpcService(AudioVideoLibrary library, MeterRegistry metrics) {
        this.library = library;
        this.transcribeTimer = Timer.builder("stt.transcribe")
            .description("Transcription latency")
            .register(metrics);
    }

    public SttGrpcService(MeterRegistry metrics) {
        SttConfig config = SttConfig.builder()
            .modelPath(Paths.get(System.getenv().getOrDefault("STT_MODEL_PATH", "/models/whisper-base.onnx")))
            .modelId(System.getenv().getOrDefault("STT_MODEL_ID", "whisper-base"))
            .useGpu(Boolean.parseBoolean(System.getenv().getOrDefault("STT_USE_GPU", "true")))
            .maxConcurrentRequests(Integer.parseInt(System.getenv().getOrDefault("STT_MAX_CONCURRENT", "10")))
            .build();

        this.library = AudioVideoLibrary.builder().withSttConfig(config).build();
        this.transcribeTimer = Timer.builder("stt.transcribe")
            .description("Transcription latency")
            .register(metrics);

        LOG.info("STT Service initialized with model: {}", config.modelId());
    }

    @Override
    public void transcribe(TranscribeRequest request, StreamObserver<TranscribeResponse> responseObserver) {
        String cid = cid();
        transcribeTimer.record(() -> {
            try {
                byte[] audioBytes = request.getAudioData().toByteArray();
                if (audioBytes.length == 0) {
                    responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                        .withDescription("Audio data cannot be empty")
                        .asRuntimeException());
                    return;
                }

                int sampleRate = request.getSampleRate() > 0 ? request.getSampleRate() : 16000;
                AudioData audio = new AudioData(audioBytes, sampleRate, 1, 16, Duration.ZERO, AudioFormat.PCM);

                TranscriptionResult result;
                try (SttEngine stt = library.getSttEngine()) {
                    result = stt.transcribe(audio, TranscriptionOptions.defaults());
                }

                LOG.info("[{}] Transcribed: length={}, confidence={}, elapsed={}ms",
                    cid, result.text().length(), result.confidence(), result.processingTime().toMillis());

                responseObserver.onNext(TranscribeResponse.newBuilder()
                    .setText(result.text())
                    .setConfidence((float) result.confidence())
                    .setProcessingTimeMs((int) result.processingTime().toMillis())
                    .setModelUsed(result.modelId() != null ? result.modelId() : "")
                    .build());
                responseObserver.onCompleted();
            } catch (com.ghatana.media.common.ValidationError e) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            } catch (com.ghatana.media.common.InferenceError e) {
                io.grpc.Status status = e.isRetryable() ? io.grpc.Status.UNAVAILABLE : io.grpc.Status.INTERNAL;
                responseObserver.onError(status.withDescription(e.getMessage()).asRuntimeException());
            } catch (Exception e) {
                LOG.error("[{}] Unexpected error: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Internal error").asRuntimeException());
            }
        });
    }

    @Override
    public StreamObserver<AudioChunk> streamTranscribe(StreamObserver<Transcription> responseObserver) {
        String sessionId = cid();
        
        // Backpressure configuration
        final int MAX_BUFFER_CHUNKS = 100; // ~10 seconds of audio at 100ms chunks
        final int HIGH_WATER_MARK = 80;  // Pause processing when buffer reaches this
        final int LOW_WATER_MARK = 20;   // Resume processing when buffer drops to this
        
        SttEngine engine = library.getSttEngine();
        com.ghatana.media.stt.api.StreamingSession session = engine.createStreamingSession();
        
        // Backpressure state
        final java.util.concurrent.atomic.AtomicBoolean isPaused = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.BlockingQueue<com.ghatana.media.common.AudioChunk> buffer = 
            new java.util.concurrent.LinkedBlockingQueue<>(MAX_BUFFER_CHUNKS);
        final java.util.concurrent.atomic.AtomicInteger chunkCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean isCompleted = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Start buffer processor thread
        java.util.concurrent.ExecutorService processor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stt-stream-processor-" + sessionId);
            t.setDaemon(true);
            return t;
        });
        
        processor.submit(() -> {
            while (!isCompleted.get() || !buffer.isEmpty()) {
                try {
                    com.ghatana.media.common.AudioChunk chunk = buffer.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (chunk != null) {
                        session.feedAudio(chunk);
                        
                        // Check if we should resume (low water mark)
                        int currentSize = buffer.size();
                        if (isPaused.get() && currentSize <= LOW_WATER_MARK) {
                            isPaused.set(false);
                            LOG.debug("[{}] Resuming - buffer size: {}", sessionId, currentSize);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.error("[{}] Error processing chunk: {}", sessionId, e.getMessage());
                }
            }
        });
        
        session.onTranscription(transcript -> {
            if (!isCompleted.get()) {
                responseObserver.onNext(Transcription.newBuilder()
                    .setText(transcript.text())
                    .setIsFinal(transcript.isFinal())
                    .setConfidence((float) transcript.confidence())
                    .build());
            }
        });

        session.onError(error -> {
            if (!isCompleted.get()) {
                responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(error.getMessage())
                    .asRuntimeException());
            }
        });

        return new StreamObserver<>() {
            @Override
            public void onNext(AudioChunk chunk) {
                if (isCompleted.get()) return;
                
                int currentCount = chunkCount.incrementAndGet();
                
                // Check backpressure - reject if buffer full
                if (buffer.size() >= MAX_BUFFER_CHUNKS) {
                    LOG.warn("[{}] Buffer full ({} chunks), dropping chunk {}", 
                        sessionId, buffer.size(), currentCount);
                    // Signal backpressure to client via error
                    responseObserver.onError(io.grpc.Status.RESOURCE_EXHAUSTED
                        .withDescription("Server buffer full - reduce sending rate")
                        .asRuntimeException());
                    isCompleted.set(true);
                    return;
                }
                
                // Add to buffer
                boolean added = buffer.offer(new com.ghatana.media.common.AudioChunk(
                    chunk.getAudioData().toByteArray(), currentCount, false, chunk.getTimestampMs()));
                
                if (!added) {
                    LOG.warn("[{}] Failed to add chunk {} to buffer", sessionId, currentCount);
                }
                
                // Check high water mark
                if (!isPaused.get() && buffer.size() >= HIGH_WATER_MARK) {
                    isPaused.set(true);
                    LOG.debug("[{}] Pausing - buffer size: {}", sessionId, buffer.size());
                }
                
                // Log periodically
                if (currentCount % 100 == 0) {
                    LOG.info("[{}] Received {} chunks, buffer size: {}", 
                        sessionId, currentCount, buffer.size());
                }
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("[{}] Client stream error: {}", sessionId, t.getMessage());
                isCompleted.set(true);
                cleanup();
            }

            @Override
            public void onCompleted() {
                LOG.info("[{}] Client stream completed: {} chunks total", sessionId, chunkCount.get());
                isCompleted.set(true);
                cleanup();
                
                // Wait for buffer to drain
                try {
                    while (!buffer.isEmpty()) {
                        Thread.sleep(50);
                    }
                    Thread.sleep(200); // Allow final processing
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                session.endStream();
                responseObserver.onCompleted();
            }
            
            private void cleanup() {
                processor.shutdown();
                try {
                    if (!processor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        processor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    processor.shutdownNow();
                }
                
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        };
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        try (SttEngine stt = library.getSttEngine()) {
            com.ghatana.media.common.EngineStatus status = stt.getStatus();
            responseObserver.onNext(StatusResponse.newBuilder()
                .setActiveModel(status.modelId() != null ? status.modelId() : "")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Status unavailable").asRuntimeException());
        }
    }

    @Override
    public void healthCheck(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        try (SttEngine stt = library.getSttEngine()) {
            com.ghatana.media.common.EngineStatus status = stt.getStatus();
            boolean healthy = status.state() == com.ghatana.media.common.EngineStatus.State.READY
                || status.state() == com.ghatana.media.common.EngineStatus.State.BUSY;
            responseObserver.onNext(HealthCheckResponse.newBuilder()
                .setStatus(healthy ? HealthStatus.HEALTH_STATUS_HEALTHY : HealthStatus.HEALTH_STATUS_UNHEALTHY)
                .setUptimeMs(status.uptimeMs())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Health check failed").asRuntimeException());
        }
    }

    @Override
    public void loadModel(LoadModelRequest request, StreamObserver<LoadModelResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("loadModel not yet implemented").asRuntimeException());
    }

    @Override
    public void unloadModel(UnloadModelRequest request, StreamObserver<UnloadModelResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("unloadModel not yet implemented").asRuntimeException());
    }

    @Override
    public void listModels(ListModelsRequest request, StreamObserver<ListModelsResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("listModels not yet implemented").asRuntimeException());
    }

    @Override
    public void adaptModel(AdaptRequest request, StreamObserver<AdaptResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED.withDescription("adaptModel not yet implemented").asRuntimeException());
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
    public void submitCorrection(CorrectionRequest request, StreamObserver<CorrectionResponse> responseObserver) {
        responseObserver.onNext(CorrectionResponse.newBuilder()
            .setAccepted(true)
            .setMessage("Correction accepted")
            .build());
        responseObserver.onCompleted();
    }

    private static String cid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
