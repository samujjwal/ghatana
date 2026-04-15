package com.ghatana.stt.grpc;

import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.stt.core.grpc.proto.*;
import com.ghatana.stt.service.PersistentSttService;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose gRPC service for STT with persistence integration.
 *              Extends base STT functionality with database persistence.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentSttGrpcService extends STTServiceGrpc.STTServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentSttGrpcService.class);

    private final PersistentSttService persistentSttService;

    public PersistentSttGrpcService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry metrics) {
        this.persistentSttService = new PersistentSttService(
            library, audioFileService, transcriptionService, metrics);
    }

    @Override
    public void transcribe(TranscribeRequest request, StreamObserver<TranscribeResponse> responseObserver) {
        String cid = cid();
        String tenantId = getTenantId();
        String userIdStr = getUserId();

        if (tenantId == null || tenantId.isEmpty()) {
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                .withDescription("Missing tenant ID")
                .asRuntimeException());
            return;
        }

        UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : UUID.randomUUID();

        try {
            MDC.put("tenantId", tenantId);
            MDC.put("userId", userIdStr);
            MDC.put("cid", cid);

            byte[] audioBytes = request.getAudioData().toByteArray();
            if (audioBytes.length == 0) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Audio data cannot be empty")
                    .asRuntimeException());
                return;
            }

            int sampleRate = request.getSampleRate() > 0 ? request.getSampleRate() : 16000;
            String fileName = "audio.pcm";
            String language = request.getLanguage();

            // Perform transcription with persistence
            persistentSttService.transcribeAndPersist(
                tenantId, userId, audioBytes, fileName,
                AudioFormat.PCM, language, sampleRate
            ).whenResult(result -> {
                responseObserver.onNext(TranscribeResponse.newBuilder()
                    .setText(result.text())
                    .setConfidence((float) result.confidence())
                    .setProcessingTimeMs(result.processingTime().toMillis())
                    .setModelUsed(result.modelId() != null ? result.modelId() : "")
                    .build());
                responseObserver.onCompleted();
            }).whenException(e -> {
                LOG.error("[{}] Transcription failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Transcription failed: " + e.getMessage())
                    .asRuntimeException());
            });

        } catch (Exception e) {
            LOG.error("[{}] Unexpected error: {}", cid, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Internal error")
                .asRuntimeException());
        } finally {
            MDC.clear();
        }
    }

    private String getTenantId() {
        String tenantId = MDC.get("tenantId");
        return tenantId != null && !tenantId.isBlank() ? tenantId : "default";
    }

    private String getUserId() {
        return MDC.get("userId");
    }

    private String cid() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

}
