/**
 * @doc.type adapter
 * @doc.purpose gRPC service adapter for STT Engine
 * @doc.layer platform
 * @doc.pattern Adapter
 *
 * <p>Thin wrapper that adapts the library's SttEngine to gRPC service calls.
 * This allows the audio-video modules to use the shared library while maintaining
 * the existing gRPC API contract.
 */
package com.ghatana.media.service.grpc;

import com.ghatana.media.common.*;
import com.ghatana.media.stt.api.*;


/**
 * Adapter for exposing SttEngine via gRPC.
 *
 * <p>Usage in gRPC service:
 * <pre>{@code
 * public class SttGrpcService extends SttServiceGrpc.SttServiceImplBase {
 *     private final SttGrpcAdapter adapter;
 *
 *     public SttGrpcService(AudioVideoLibrary library) {
 *         this.adapter = new SttGrpcAdapter(library.getSttEngine());
 *     }
 *
 *     @Override
 *     public void transcribe(TranscribeRequest req, StreamObserver<TranscribeResponse> resp) {
 *         adapter.transcribe(req, resp);
 *     }
 * }
 * }</pre>
 */
public class SttGrpcAdapter {

    private final SttEngine engine;

    public SttGrpcAdapter(SttEngine engine) {
        this.engine = engine;
    }

    /**
     * Handle unary transcription request.
     *
     * @param request protobuf request
     * @param responseObserver gRPC response observer
     */
    public void transcribe(
        com.ghatana.stt.core.grpc.proto.TranscribeRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.stt.core.grpc.proto.TranscribeResponse> responseObserver) {

        try {
            // Convert protobuf to library types
            AudioData audio = convertAudio(request.getAudio());
            TranscriptionOptions options = convertOptions(request.getOptions());

            // Call engine
            TranscriptionResult result = engine.transcribe(audio, options);

            // Convert result back to protobuf
            com.ghatana.stt.core.grpc.proto.TranscribeResponse response = convertResult(result);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Transcription failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Handle streaming transcription.
     *
     * @param responseObserver gRPC stream observer
     * @return stream observer for client requests
     */
    public io.grpc.stub.StreamObserver<com.ghatana.stt.core.grpc.proto.StreamingTranscribeRequest> streamingTranscribe(
        io.grpc.stub.StreamObserver<com.ghatana.stt.core.grpc.proto.StreamingTranscribeResponse> responseObserver) {

        // Create streaming session
        StreamingSession session = engine.createStreamingSession();

        // Set up callbacks
        session.onTranscription(transcription -> {
            com.ghatana.stt.core.grpc.proto.StreamingTranscribeResponse response =
                com.ghatana.stt.core.grpc.proto.StreamingTranscribeResponse.newBuilder()
                    .setText(transcription.text())
                    .setIsFinal(transcription.isFinal())
                    .setConfidence(transcription.confidence())
                    .build();
            responseObserver.onNext(response);
        });

        session.onError(error -> {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription(error.getMessage())
                    .asRuntimeException()
            );
        });

        // Return observer for client requests
        return new io.grpc.stub.StreamObserver<>() {
            @Override
            public void onNext(com.ghatana.stt.core.grpc.proto.StreamingTranscribeRequest request) {
                AudioChunk chunk = new AudioChunk(
                    request.getAudioChunk().toByteArray(),
                    request.getSequenceNumber(),
                    request.getIsFinal(),
                    System.currentTimeMillis()
                );
                session.feedAudio(chunk);

                if (request.getIsFinal()) {
                    session.endStream();
                }
            }

            @Override
            public void onError(Throwable t) {
                session.close();
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                session.endStream();
                responseObserver.onCompleted();
            }
        };
    }

    // ====================================================================================
    // Type Conversions
    // ====================================================================================

    private AudioData convertAudio(com.ghatana.stt.core.grpc.proto.AudioData proto) {
        return AudioData.builder()
            .data(proto.getData().toByteArray())
            .sampleRate(proto.getSampleRate())
            .channels(proto.getChannels())
            .bitsPerSample(proto.getBitsPerSample())
            .format(convertAudioFormat(proto.getFormat()))
            .build();
    }

    private AudioFormat convertAudioFormat(com.ghatana.stt.core.grpc.proto.AudioFormat format) {
        return switch (format) {
            case PCM -> AudioFormat.PCM;
            case WAV -> AudioFormat.WAV;
            case MP3 -> AudioFormat.MP3;
            case FLAC -> AudioFormat.FLAC;
            default -> AudioFormat.PCM;
        };
    }

    private TranscriptionOptions convertOptions(com.ghatana.stt.core.grpc.proto.TranscriptionOptions proto) {
        return TranscriptionOptions.builder()
            .language(new java.util.Locale(proto.getLanguage()))
            .enablePunctuation(proto.getEnablePunctuation())
            .enableTimestamps(proto.getEnableTimestamps())
            .maxAlternatives(proto.getMaxAlternatives())
            .profanityFilter(proto.getProfanityFilter())
            .build();
    }

    private com.ghatana.stt.core.grpc.proto.TranscribeResponse convertResult(TranscriptionResult result) {
        com.ghatana.stt.core.grpc.proto.TranscribeResponse.Builder builder =
            com.ghatana.stt.core.grpc.proto.TranscribeResponse.newBuilder()
                .setText(result.getText())
                .setConfidence(result.confidence())
                .setProcessingTimeMs((int) result.processingTime().toMillis())
                .setLanguage(result.language())
                .setModelId(result.modelId());

        // Add word timings if present
        for (WordTiming word : result.words()) {
            builder.addWords(
                com.ghatana.stt.core.grpc.proto.WordTiming.newBuilder()
                    .setWord(word.word())
                    .setStartSec(word.startSec())
                    .setEndSec(word.endSec())
                    .setConfidence(word.confidence())
                    .build()
            );
        }

        // Add alternatives if present
        for (Alternative alt : result.alternatives()) {
            builder.addAlternatives(
                com.ghatana.stt.core.grpc.proto.Alternative.newBuilder()
                    .setText(alt.text())
                    .setConfidence(alt.confidence())
                    .build()
            );
        }

        return builder.build();
    }
}
