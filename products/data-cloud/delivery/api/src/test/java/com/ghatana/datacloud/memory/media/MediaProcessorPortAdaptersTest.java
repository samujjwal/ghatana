/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.spi.SttTranscription;
import com.ghatana.datacloud.spi.VoiceSttPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("MediaProcessorPort adapter tests")
@ExtendWith(MockitoExtension.class)
class MediaProcessorPortAdaptersTest extends EventloopTestBase {

    @Mock
    private MediaArtifactRepository repository;

    @Mock
    private VoiceSttPort voiceSttPort;

    private InMemoryMediaProcessorPort inMemoryMediaProcessorPort;
    private VoiceSttMediaProcessorAdapter voiceAdapter;
    private VisionAnalysisMediaProcessorAdapter visionAdapter;
    private MultimodalMediaProcessorAdapter multimodalAdapter;

    @BeforeEach
    void setUp() {
        inMemoryMediaProcessorPort = new InMemoryMediaProcessorPort();
        voiceAdapter = new VoiceSttMediaProcessorAdapter(repository, voiceSttPort);
        visionAdapter = new VisionAnalysisMediaProcessorAdapter(repository);
        multimodalAdapter = new MultimodalMediaProcessorAdapter(repository);
    }

    @Test
    void inMemoryAdapterReturnsStableIdsForSameInputs() {
        String transcriptId = runPromise(() -> inMemoryMediaProcessorPort.transcribeAudio(
            "artifact-1",
            "tenant-1",
            "en-US",
            Map.of("sampleRate", "16000")
        ));
        String transcriptIdRepeat = runPromise(() -> inMemoryMediaProcessorPort.transcribeAudio(
            "artifact-1",
            "tenant-1",
            "en-US",
            Map.of("sampleRate", "16000")
        ));

        assertThat(transcriptId).isEqualTo(transcriptIdRepeat);
        assertThat(transcriptId).startsWith("transcript-");

        String frameIndexId = runPromise(() -> inMemoryMediaProcessorPort.analyzeVision(
            "artifact-1",
            "tenant-1",
            "OBJECT_DETECTION",
            Map.of("threshold", "0.75")
        ));
        String frameIndexIdRepeat = runPromise(() -> inMemoryMediaProcessorPort.analyzeVision(
            "artifact-1",
            "tenant-1",
            "OBJECT_DETECTION",
            Map.of("threshold", "0.75")
        ));

        assertThat(frameIndexId).isEqualTo(frameIndexIdRepeat);
        assertThat(frameIndexId).startsWith("frame-index-");

        String indexId = runPromise(() -> inMemoryMediaProcessorPort.indexMultimodal(
            "artifact-1",
            "tenant-1",
            "AUDIO_VISUAL",
            Map.of("window", "30s")
        ));
        String indexIdRepeat = runPromise(() -> inMemoryMediaProcessorPort.indexMultimodal(
            "artifact-1",
            "tenant-1",
            "AUDIO_VISUAL",
            Map.of("window", "30s")
        ));

        assertThat(indexId).isEqualTo(indexIdRepeat);
        assertThat(indexId).startsWith("multimodal-index-");
    }

    @Test
    void voiceAdapterReturnsStableTranscriptIdBasedOnTranscriptionResult() {
        MediaArtifactRecord artifact = createArtifact("audio/wav", "audio://tenant-1/artifact-1");
        when(repository.findById("artifact-1", "tenant-1")).thenReturn(Promise.of(Optional.of(artifact)));
        when(voiceSttPort.transcribe(any(), eq("audio/wav"), eq("en-US")))
            .thenReturn(Promise.of(SttTranscription.of("hello world", 0.94, "voice-stt")));

        String transcriptId = runPromise(() -> voiceAdapter.transcribeAudio(
            "artifact-1",
            "tenant-1",
            "en-US",
            Map.of("sampleRate", "16000")
        ));
        String transcriptIdRepeat = runPromise(() -> voiceAdapter.transcribeAudio(
            "artifact-1",
            "tenant-1",
            "en-US",
            Map.of("sampleRate", "16000")
        ));

        assertThat(transcriptId).isEqualTo(transcriptIdRepeat);
        assertThat(transcriptId).startsWith("transcript-");
    }

    @Test
    void visionAdapterReturnsStableFrameIndexId() {
        MediaArtifactRecord artifact = createArtifact("image/png", "image://tenant-1/artifact-2");
        when(repository.findById("artifact-2", "tenant-1")).thenReturn(Promise.of(Optional.of(artifact)));

        String frameIndexId = runPromise(() -> visionAdapter.analyzeVision(
            "artifact-2",
            "tenant-1",
            "OBJECT_DETECTION",
            Map.of("threshold", "0.6")
        ));
        String frameIndexIdRepeat = runPromise(() -> visionAdapter.analyzeVision(
            "artifact-2",
            "tenant-1",
            "OBJECT_DETECTION",
            Map.of("threshold", "0.6")
        ));

        assertThat(frameIndexId).isEqualTo(frameIndexIdRepeat);
        assertThat(frameIndexId).startsWith("frame-index-");
    }

    @Test
    void multimodalAdapterReturnsStableIndexId() {
        MediaArtifactRecord artifact = createArtifact("video/mp4", "video://tenant-1/artifact-3");
        when(repository.findById("artifact-3", "tenant-1")).thenReturn(Promise.of(Optional.of(artifact)));

        String indexId = runPromise(() -> multimodalAdapter.indexMultimodal(
            "artifact-3",
            "tenant-1",
            "AUDIO_VISUAL",
            Map.of("window", "30s")
        ));
        String indexIdRepeat = runPromise(() -> multimodalAdapter.indexMultimodal(
            "artifact-3",
            "tenant-1",
            "AUDIO_VISUAL",
            Map.of("window", "30s")
        ));

        assertThat(indexId).isEqualTo(indexIdRepeat);
        assertThat(indexId).startsWith("multimodal-index-");
    }

    private static MediaArtifactRecord createArtifact(String mediaType, String storageUri) {
        return MediaArtifactRecord.create(
            "tenant-1",
            "agent-1",
            mediaType,
            storageUri,
            1024L,
            "checksum-1",
            60_000L,
            "tool-1",
            "corr-1",
            "ACTIVE",
            MediaArtifactRecord.LIFECYCLE_REGISTERED,
            "AUDIO_VISUAL",
            "INTERNAL",
            MediaArtifactRecord.CONSENT_GRANTED,
            "standard",
            Instant.now().plusSeconds(3_600),
            "blob",
            null,
            null,
            null,
            "owner-1",
            "source-1",
            Map.of("seed", "test"),
            "creator-1");
    }
}
