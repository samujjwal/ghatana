/**
 * @doc.type adapter
 * @doc.purpose gRPC service adapter for TTS Engine
 * @doc.layer platform
 * @doc.pattern Adapter
 */
package com.ghatana.media.service.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.tts.api.*;

import java.util.List;
import java.util.Locale;

/**
 * Adapter for exposing TtsEngine via gRPC.
 */
public class TtsGrpcAdapter {

    private final TtsEngine engine;

    public TtsGrpcAdapter(TtsEngine engine) {
        this.engine = engine;
    }

    /**
     * Handle synthesis request.
     */
    public void synthesize(
        com.ghatana.tts.core.grpc.proto.SynthesizeRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.tts.core.grpc.proto.SynthesizeResponse> responseObserver) {

        try {
            SynthesisOptions options = convertOptions(request.getOptions());
            AudioData audio = engine.synthesize(request.getText(), options);

            com.ghatana.tts.core.grpc.proto.SynthesizeResponse response =
                com.ghatana.tts.core.grpc.proto.SynthesizeResponse.newBuilder()
                    .setAudio(convertAudio(audio))
                    .setVoiceUsed(engine.getActiveVoice().voiceId())
                    .setProcessingTimeMs(100) // Would track actual time
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Synthesis failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Handle streaming synthesis.
     */
    public void synthesizeStreaming(
        com.ghatana.tts.core.grpc.proto.SynthesizeRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.tts.core.grpc.proto.AudioChunk> responseObserver) {

        try {
            SynthesisOptions options = convertOptions(request.getOptions());

            engine.synthesizeStreaming(request.getText(), options, chunk -> {
                com.ghatana.tts.core.grpc.proto.AudioChunk protoChunk =
                    com.ghatana.tts.core.grpc.proto.AudioChunk.newBuilder()
                        .setData(com.google.protobuf.ByteString.copyFrom(chunk.data()))
                        .setSequenceNumber(chunk.sequenceNumber())
                        .setIsLast(chunk.isLast())
                        .build();
                responseObserver.onNext(protoChunk);
            });

            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Streaming synthesis failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * Get available voices.
     */
    public void getVoices(
        com.ghatana.tts.core.grpc.proto.GetVoicesRequest request,
        io.grpc.stub.StreamObserver<com.ghatana.tts.core.grpc.proto.GetVoicesResponse> responseObserver) {

        List<VoiceInfo> voices = request.getLanguage().isEmpty()
            ? engine.getAvailableVoices()
            : engine.getAvailableVoices(new Locale(request.getLanguage()));

        com.ghatana.tts.core.grpc.proto.GetVoicesResponse.Builder builder =
            com.ghatana.tts.core.grpc.proto.GetVoicesResponse.newBuilder();

        for (VoiceInfo voice : voices) {
            builder.addVoices(convertVoice(voice));
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ====================================================================================
    // Type Conversions
    // ====================================================================================

    private SynthesisOptions convertOptions(com.ghatana.tts.core.grpc.proto.SynthesisOptions proto) {
        return SynthesisOptions.builder()
            .voiceId(proto.getVoiceId().isEmpty() ? null : proto.getVoiceId())
            .speed(proto.getSpeed())
            .pitch(proto.getPitch())
            .volume(proto.getVolume())
            .language(proto.getLanguage().isEmpty() ? null : new Locale(proto.getLanguage()))
            .sampleRate(proto.getSampleRate())
            .emotion(convertEmotion(proto.getEmotion()))
            .build();
    }

    private Emotion convertEmotion(com.ghatana.tts.core.grpc.proto.Emotion emotion) {
        return switch (emotion) {
            case HAPPY -> Emotion.HAPPY;
            case SAD -> Emotion.SAD;
            case ANGRY -> Emotion.ANGRY;
            case EXCITED -> Emotion.EXCITED;
            case CALM -> Emotion.CALM;
            case PROFESSIONAL -> Emotion.PROFESSIONAL;
            default -> Emotion.NEUTRAL;
        };
    }

    private com.ghatana.tts.core.grpc.proto.AudioData convertAudio(AudioData audio) {
        return com.ghatana.tts.core.grpc.proto.AudioData.newBuilder()
            .setData(com.google.protobuf.ByteString.copyFrom(audio.data()))
            .setSampleRate(audio.sampleRate())
            .setChannels(audio.channels())
            .setBitsPerSample(audio.bitsPerSample())
            .setFormat(convertAudioFormat(audio.format()))
            .build();
    }

    private com.ghatana.tts.core.grpc.proto.AudioFormat convertAudioFormat(AudioFormat format) {
        return switch (format) {
            case WAV -> com.ghatana.tts.core.grpc.proto.AudioFormat.WAV;
            case MP3 -> com.ghatana.tts.core.grpc.proto.AudioFormat.MP3;
            case FLAC -> com.ghatana.tts.core.grpc.proto.AudioFormat.FLAC;
            default -> com.ghatana.tts.core.grpc.proto.AudioFormat.PCM;
        };
    }

    private com.ghatana.tts.core.grpc.proto.VoiceInfo convertVoice(VoiceInfo voice) {
        return com.ghatana.tts.core.grpc.proto.VoiceInfo.newBuilder()
            .setVoiceId(voice.voiceId())
            .setName(voice.name())
            .setDescription(voice.description())
            .setLanguage(voice.language().toLanguageTag())
            .setSampleRate(voice.sampleRate())
            .setIsCloned(voice.isCloned())
            .build();
    }
}
