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
        SttEngine engine = library.getSttEngine();
        com.ghatana.media.stt.api.StreamingSession session = engine.createStreamingSession();

        session.onTranscription(transcript -> responseObserver.onNext(Transcription.newBuilder()
            .setText(transcript.text())
            .setIsFinal(transcript.isFinal())
            .setConfidence((float) transcript.confidence())
            .build()));

        session.onError(error -> responseObserver.onError(io.grpc.Status.INTERNAL
            .withDescription(error.getMessage())
            .asRuntimeException()));

        return new StreamObserver<>() {
            private int chunkCount = 0;

            @Override
            public void onNext(AudioChunk chunk) {
                session.feedAudio(new com.ghatana.media.common.AudioChunk(
                    chunk.getAudioData().toByteArray(), chunkCount++, false, chunk.getTimestampMs()));
            }

            @Override
            public void onError(Throwable t) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onCompleted() {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
                LOG.info("[{}] Client stream completed: {} chunks", sessionId, chunkCount);
                responseObserver.onCompleted();
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
