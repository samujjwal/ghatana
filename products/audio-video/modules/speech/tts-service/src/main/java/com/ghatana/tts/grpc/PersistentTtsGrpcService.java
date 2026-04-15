package com.ghatana.tts.grpc;

import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.tts.core.grpc.proto.*;
import com.ghatana.tts.service.PersistentTtsService;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose gRPC service for TTS with persistence integration.
 *              Extends base TTS functionality with database persistence.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentTtsGrpcService extends TTSServiceGrpc.TTSServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentTtsGrpcService.class);

    private final PersistentTtsService persistentTtsService;

    public PersistentTtsGrpcService(
            AudioVideoLibrary library,
            AudioFileService audioFileService,
            MeterRegistry metrics) {
        this.persistentTtsService = new PersistentTtsService(library, audioFileService, metrics);
    }

    @Override
    public void synthesize(SynthesizeRequest request, StreamObserver<SynthesizeResponse> responseObserver) {
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

            String text = request.getText();
            if (text == null || text.isEmpty()) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Text cannot be empty")
                    .asRuntimeException());
                return;
            }

            if (text.length() > 5000) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Text exceeds 5000 characters")
                    .asRuntimeException());
                return;
            }

            Optional<String> voiceId = request.getVoiceId().isEmpty()
                ? Optional.empty()
                : Optional.of(request.getVoiceId());

            float speed = request.getOptions().getSpeed() > 0 ? request.getOptions().getSpeed() : 1.0f;
            float pitch = request.getOptions().getPitch() > 0 ? request.getOptions().getPitch() : 1.0f;
            String language = request.getOptions().getLanguage().isEmpty()
                ? null
                : request.getOptions().getLanguage();

            // Perform synthesis with persistence
            persistentTtsService.synthesizeAndPersist(
                tenantId, userId, text, voiceId, speed, pitch, language
            ).whenResult(result -> {
                responseObserver.onNext(SynthesizeResponse.newBuilder()
                    .setAudioData(com.google.protobuf.ByteString.copyFrom(result.audioData()))
                    .setSampleRate(result.sampleRate())
                    .setDurationMs(0L)
                    .setProcessingTimeMs(result.processingTimeMs())
                    .setVoiceUsed(voiceId.orElse(""))
                    .build());
                responseObserver.onCompleted();
            }).whenException(e -> {
                LOG.error("[{}] TTS synthesis failed: {}", cid, e.getMessage(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("TTS synthesis failed: " + e.getMessage())
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
    public void streamSynthesize(SynthesizeRequest request, StreamObserver<AudioChunk> responseObserver) {
        // For streaming, we might want to store the final audio after streaming completes
        // For now, delegate to base implementation without persistence
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
            .withDescription("Streaming with persistence not yet implemented")
            .asRuntimeException());
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
