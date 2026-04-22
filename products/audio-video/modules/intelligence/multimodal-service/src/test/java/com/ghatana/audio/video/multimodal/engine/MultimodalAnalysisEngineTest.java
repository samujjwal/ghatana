package com.ghatana.audio.video.multimodal.engine;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for multimodal fusion and temporal alignment.
 *
 * @doc.type test
 * @doc.purpose Verifies multimodal analysis engine fusion behavior with fake adapters
 * @doc.layer product
 */
@DisplayName("MultimodalAnalysisEngine Tests [GH-90000]")
class MultimodalAnalysisEngineTest extends EventloopTestBase {

    @Test
    @DisplayName("Should fuse audio image and text context [GH-90000]")
    void shouldFuseAudioImageAndTextContext() { // GH-90000
        AudioResult audio = AudioResult.builder() // GH-90000
            .transcription("hello from the meeting room [GH-90000]")
            .confidence(0.93) // GH-90000
            .build(); // GH-90000
        VisualResult visual = VisualResult.builder() // GH-90000
            .sceneDescription("A person standing near a whiteboard [GH-90000]")
            .detections(List.of(new DetectionResult("person", 0.98, 10, 20, 30, 40))) // GH-90000
            .confidence(0.98) // GH-90000
            .build(); // GH-90000

        try (MultimodalAnalysisEngine engine = new MultimodalAnalysisEngine( // GH-90000
            new FakeMediaGateway(audio, visual, visual), // GH-90000
            AudioVideoRuntimeSettings.defaults())) { // GH-90000
            MultimodalResult result = runPromise(() -> Promise.of(engine.analyse( // GH-90000
                MultimodalRequest.builder() // GH-90000
                    .audioData(new byte[] {1, 2, 3}) // GH-90000
                    .imageData(new byte[] {4, 5, 6}) // GH-90000
                    .text("board review [GH-90000]")
                    .build() // GH-90000
            )));

            assertEquals("hello from the meeting room", result.getAudioResult().getTranscription()); // GH-90000
            assertEquals("A person standing near a whiteboard", result.getVisualResult().getSceneDescription()); // GH-90000
            assertTrue(result.getCombinedAnalysis().contains("Speech: \"hello from the meeting room\"")); // GH-90000
            assertTrue(result.getCombinedAnalysis().contains("Visual: A person standing near a whiteboard [GH-90000]"));
            assertTrue(result.getCombinedAnalysis().contains("Text context: board review [GH-90000]"));
            assertTrue(result.getProcessingTimeMs() >= 0); // GH-90000
        }
    }

    @Test
    @DisplayName("Should align speech segments with video frames [GH-90000]")
    void shouldAlignSpeechSegmentsWithVideoFrames() { // GH-90000
        AudioResult audio = AudioResult.builder() // GH-90000
            .transcription("hello world [GH-90000]")
            .confidence(0.88) // GH-90000
            .timedSegments(List.of( // GH-90000
                new AudioResult.TimedSegment(0, 999, "hello"), // GH-90000
                new AudioResult.TimedSegment(1000, 1999, "world") // GH-90000
            ))
            .build(); // GH-90000
        VisualResult video = VisualResult.builder() // GH-90000
            .sceneDescription("Two frames with objects [GH-90000]")
            .frameResults(List.of( // GH-90000
                new FrameResult(1, 500, List.of(new DetectionResult("person", 0.95, 0, 0, 10, 10))), // GH-90000
                new FrameResult(2, 1500, List.of(new DetectionResult("car", 0.90, 5, 5, 15, 15))) // GH-90000
            ))
            .detections(List.of( // GH-90000
                new DetectionResult("person", 0.95, 0, 0, 10, 10), // GH-90000
                new DetectionResult("car", 0.90, 5, 5, 15, 15) // GH-90000
            ))
            .confidence(0.95) // GH-90000
            .build(); // GH-90000

        try (MultimodalAnalysisEngine engine = new MultimodalAnalysisEngine( // GH-90000
            new FakeMediaGateway(audio, VisualResult.error("unused [GH-90000]"), video),
            AudioVideoRuntimeSettings.defaults())) { // GH-90000
            VideoAudioResult result = runPromise(() -> Promise.of( // GH-90000
                engine.analyseVideoWithAudio(new byte[] {9, 8, 7}, true, true, 2) // GH-90000
            ));

            assertEquals(2, result.getTemporalAlignments().size()); // GH-90000
            assertEquals("hello", result.getTemporalAlignments().get(0).getSpeechText()); // GH-90000
            assertEquals("world", result.getTemporalAlignments().get(1).getSpeechText()); // GH-90000
            assertTrue(result.getTemporalAlignments().get(0).getSyncConfidence() > 0.0); // GH-90000
            assertTrue(result.getCombinedNarrative().contains("[0s] \"hello\"")); // GH-90000
            assertTrue(result.getCombinedNarrative().contains("[1s] \"world\"")); // GH-90000
            assertFalse(result.getVideoResult().isError()); // GH-90000
        }
    }

    private static final class FakeMediaGateway implements MultimodalMediaGateway {
        private final AudioResult audioResult;
        private final VisualResult imageResult;
        private final VisualResult videoResult;

        private FakeMediaGateway(AudioResult audioResult, VisualResult imageResult, VisualResult videoResult) { // GH-90000
            this.audioResult = audioResult;
            this.imageResult = imageResult;
            this.videoResult = videoResult;
        }

        @Override
        public AudioResult transcribe(byte[] audioData) { // GH-90000
            return audioResult;
        }

        @Override
        public VisualResult analyseImage(byte[] imageData) { // GH-90000
            return imageResult;
        }

        @Override
        public VisualResult analyseVideo(byte[] videoData, int sampleFps, int maxFrames) { // GH-90000
            return videoResult;
        }

        @Override
        public String backendName() { // GH-90000
            return "test";
        }

        @Override
        public boolean metricsEnabled() { // GH-90000
            return true;
        }

        @Override
        public void close() { // GH-90000
        }
    }
}
