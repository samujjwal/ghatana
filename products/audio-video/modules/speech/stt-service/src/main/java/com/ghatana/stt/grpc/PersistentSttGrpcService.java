package com.ghatana.stt.grpc;

import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.stt.core.grpc.proto.*;
import com.ghatana.stt.service.PersistentSttService;
import io.grpc.Metadata;
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

    private static final Metadata.Key<String> TENANT_ID_KEY =
        Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID_KEY =
        Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private final PersistentSttService persistentSttService;
    private final TranscriptionService transcriptionService;

    public PersistentSttGrpcService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            TranscriptionService transcriptionService,
            MeterRegistry metrics) {
        this.persistentSttService = new PersistentSttService(
            library, audioFileService, transcriptionService, metrics);
        this.transcriptionService = transcriptionService;
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
            String fileName = request.hasFileName() ? request.getFileName() : "audio.pcm";
            String language = request.getLanguage();

            // Perform transcription with persistence
            persistentSttService.transcribeAndPersist(
                tenantId, userId, audioBytes, fileName,
                AudioFormat.PCM, language, sampleRate
            ).whenResult(result -> {
                responseObserver.onNext(TranscribeResponse.newBuilder()
                    .setText(result.text())
                    .setConfidence((float) result.confidence())
                    .setProcessingTimeMs((int) result.processingTime().toMillis())
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

    @Override
    public void getTranscription(GetTranscriptionRequest request, StreamObserver<GetTranscriptionResponse> responseObserver) {
        String tenantId = getTenantId();

        if (tenantId == null || tenantId.isEmpty()) {
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                .withDescription("Missing tenant ID")
                .asRuntimeException());
            return;
        }

        try {
            UUID audioFileId = UUID.fromString(request.getAudioFileId());

            transcriptionService.findByAudioFileId(tenantId, audioFileId)
                .whenResult(optTranscription -> {
                    if (optTranscription.isPresent()) {
                        TranscriptionEntity entity = optTranscription.get();
                        responseObserver.onNext(GetTranscriptionResponse.newBuilder()
                            .setTranscriptionId(entity.getId().toString())
                            .setAudioFileId(entity.getAudioFileId().toString())
                            .setText(entity.getTranscriptionText())
                            .setLanguage(entity.getLanguage())
                            .setConfidence(entity.getConfidence())
                            .setStatus(mapStatus(entity.getStatus()))
                            .setCreatedAt(entity.getCreatedAt().toEpochMilli())
                            .build());
                        responseObserver.onCompleted();
                    } else {
                        responseObserver.onError(io.grpc.Status.NOT_FOUND
                            .withDescription("Transcription not found")
                            .asRuntimeException());
                    }
                }).whenException(e -> {
                    LOG.error("Failed to get transcription: {}", e.getMessage(), e);
                    responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Failed to retrieve transcription")
                        .asRuntimeException());
                });

        } catch (IllegalArgumentException e) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription("Invalid audio file ID")
                .asRuntimeException());
        }
    }

    @Override
    public void listTranscriptions(ListTranscriptionsRequest request, StreamObserver<ListTranscriptionsResponse> responseObserver) {
        String tenantId = getTenantId();

        if (tenantId == null || tenantId.isEmpty()) {
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                .withDescription("Missing tenant ID")
                .asRuntimeException());
            return;
        }

        transcriptionService.findByTenantId(tenantId)
            .whenResult(transcriptions -> {
                ListTranscriptionsResponse.Builder response = ListTranscriptionsResponse.newBuilder();

                for (TranscriptionEntity entity : transcriptions) {
                    response.addTranscriptions(TranscriptionSummary.newBuilder()
                        .setTranscriptionId(entity.getId().toString())
                        .setAudioFileId(entity.getAudioFileId().toString())
                        .setText(truncateText(entity.getTranscriptionText(), 100))
                        .setLanguage(entity.getLanguage())
                        .setStatus(mapStatus(entity.getStatus()))
                        .setCreatedAt(entity.getCreatedAt().toEpochMilli())
                        .build());
                }

                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
            }).whenException(e -> {
                LOG.error("Failed to list transcriptions: {}", e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to list transcriptions")
                    .asRuntimeException());
            });
    }

    private String getTenantId() {
        // In production, extract from gRPC context or authentication context
        // This is a simplified version - in real implementation,
        // use the AuthenticationInterceptor to set tenant context
        return io.grpc.Context.current()
            .getValue(io.grpc.Metadata.Key.of("tenant-id", Metadata.ASCII_STRING_MARSHALLER));
    }

    private String getUserId() {
        return io.grpc.Context.current()
            .getValue(io.grpc.Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER));
    }

    private String cid() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private TranscriptionStatus mapStatus(TranscriptionEntity.TranscriptionStatus status) {
        return switch (status) {
            case PENDING -> TranscriptionStatus.PENDING;
            case PROCESSING -> TranscriptionStatus.PROCESSING;
            case COMPLETED -> TranscriptionStatus.COMPLETED;
            case FAILED -> TranscriptionStatus.FAILED;
        };
    }
}
