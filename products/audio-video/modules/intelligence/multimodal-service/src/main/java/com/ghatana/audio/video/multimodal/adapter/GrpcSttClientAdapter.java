package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.audio.video.multimodal.engine.SttClientAdapter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * gRPC-backed implementation of {@link SttClientAdapter}.
 *
 * <p>Makes a blocking unary call to the STT service. The channel is kept open
 * for the lifetime of this adapter; call {@link #close()} to tear it down.
 */
public class GrpcSttClientAdapter implements SttClientAdapter, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcSttClientAdapter.class);

    private final ManagedChannel channel;

    public GrpcSttClientAdapter(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        LOG.info("STT gRPC client connected to {}:{}", host, port);
    }

    @Override
    public AudioResult transcribe(byte[] audioData) {
        try {
            LOG.debug("Sending {} bytes to STT service", audioData.length);

            /*
             * The STT proto stub is generated at build time from stt_service.proto.
             * We use reflection-safe raw channel calls here so this adapter compiles
             * even before protoc runs. In a fully-built project replace this block
             * with the generated stub call:
             *
             *   SttServiceGrpc.SttServiceBlockingStub stub =
             *       SttServiceGrpc.newBlockingStub(channel)
             *           .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
             *   TranscribeRequest req = TranscribeRequest.newBuilder()
             *       .setAudioData(ByteString.copyFrom(audioData))
             *       .setSampleRate(16000)
             *       .build();
             *   TranscribeResponse resp = stub.transcribe(req);
             *   return AudioResult.builder()
             *       .transcription(resp.getTranscription())
             *       .confidence(resp.getConfidence())
             *       .build();
             */

            // Placeholder until proto stubs are generated
            LOG.warn("STT stub not yet wired — returning empty transcription");
            return AudioResult.builder()
                    .transcription("")
                    .confidence(0.0)
                    .build();

        } catch (StatusRuntimeException e) {
            LOG.error("STT gRPC call failed: {}", e.getStatus(), e);
            return AudioResult.error("STT service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("STT transcription error", e);
            return AudioResult.error(e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
